package symapQuery;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import database.DBconn2;
import symap.Globals;
import symap.manager.Mproject;
import util.Jcomp;
import util.Jhtml;
import util.Utilities;

/***************************************************************
 * The QuerySetup panel. Called by QueryFrame, calls qFrame.makeTabTable(sql, sum, isSingle()) 	
 * 
 * DBData filters:
 *  Locations  - hit ends on two lines
 *  isAnnoStr  - loads all rows then filters in DBdata (see not before makeChkAnnoWhere)
 *  isGeneNum  - have to parse geneTag for suffix (search of number happens with MYSQL) 
 *  isGeneNum  - if any SP row deactivated, must fill in the other side in DBdata
 *	isGeneOlap - MySQL search will not return mate if one has Olap<N, which makes the other half look like a pseudo; so have DBdata do all checkin
 *  isOlapAnno - -ii
 *  
 *  IsSelf has projIdx=projIdx, so needs special logic for this; search isSelf to find all references (this is messy)
 	ManagerFrame - creates 2nd Mproject with DisplayName and Abbrev ending with X
	QueryPanel   - mkSQL add refidx=0 for query
	SpeciesPanel - keeps both displayNames in special array for SummaryPanel
	DBdata  	 - makeAnnoKeys for only one species; both access with same spIdx  
		rsLoadRow, rsLoadRowMerge - if (chrIdx[0]==chrIdx[1]) use annoIdx1 and annoIdx2 to determine 0/1 for anno
		formatRowForTbl() 		  - does not use spIdx for project, but 0/1 since only two projects
		geneCntMap 				  - two entries with spIdx and spIdx+1 as keys
	SummaryPanel - uses spIdx and spIdx+1 as keys
 */
public class QueryPanel extends JPanel {
	private static final long serialVersionUID = 2804096298674364175L;
	private final int wFil1=150, wFil2=60;					// Spacing between left hand label and filters; CAS579c was 140
	private final String coDefNum = "3", groupDefNum="4";	// coset and group defaults
	private final boolean AND=true;							// For mysql joins
	
	protected boolean isMinorOlap=true;					// viewSymap -ii show overlap or minor (Globals.INFO -ii)
	private boolean   isPgeneF = Globals.bQueryPgeneF; 	// viewSymap -pg for old gene cluster; 
	private String    olapDesc;							// viewSymap -go for gene overlap
	private boolean   isSelf=false; 	
	private boolean   bNoAnno=false, bOneAnno=false, bNoPseudo=false;
	private boolean   bNoMulti=false, bNoClust=false;
	
	// Only one checkbox is allowed to be selected at a time. See setChkEnable. 
	private int nChkNone=0, nChkSingle=1, nChkBlk=2, nChkCoSet=3, nChkHit=4, nChkGene=5, nChkMulti=6, nChkClust=7;
	
	protected QueryPanel(QueryFrame parentFrame) {
		qFrame = parentFrame;
		olapDesc = (qFrame.isAlgo2() && Globals.bQueryOlap==false) ? "Exon" : "Gene";
		
		// what queries to display
		isSelf   =  qFrame.isSelf();
		bOneAnno =  qFrame.isOneHasAnno(); // only one of all projects have anno
		bNoAnno  =  qFrame.isAllNoAnno();  // no projects have anno
		bNoPseudo = qFrame.cntUsePseudo==0;
		bNoMulti = isSelf || bNoAnno;
		bNoClust = isSelf || bNoAnno || (bOneAnno && bNoPseudo);
		
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(Color.WHITE);

		createAllPanels();

		setClearDefaults(); // No matter on they get set on create, they all start out cleared
	}
	protected DBconn2 getDBC()  {return qFrame.getDBC();} // DBdata needs this for Gene#
	protected boolean isAlgo2() {return qFrame.isAlgo2();}
	    
	/*************************************************************
	 * XXX State and get
	 */
	protected boolean isSelf() {return qFrame.isSelf();} // for DBdata
	
	protected boolean isIncludeMinor() { 
		return isEveryStarAnno() || isOlapAnno_ii() || (isMultiN() && chkMultiMinor.isSelected()) 
				|| isGeneNum() || isHitNum() || isAnnoStr(); // CAS579c add isAnnoStr 
	}
	protected boolean isGroup() {return isMultiN() || isClustN() || isGeneNum();} // for UtilReport
	
// General
	protected boolean isAnnoStr()	{return chkAnno.isEnabled() && chkAnno.isSelected();}// DBdata filter;
	protected String getAnnoTxt()	{return txtAnno.getText().trim();}
	
	protected SpeciesPanel getSpeciesPanel() {return spPanel;}
	protected HashMap <Integer, String> getGrpCoords() { return grpCoords;} // DBdata filter
	
// Single
	protected boolean isSingle() 		{ return chkSingle.isEnabled() && chkSingle.isSelected(); }
	protected boolean isSingleOrphan()	{ return isSingle() && radOrphan.isSelected();} // WHERE not exists
	protected boolean isSingleGenes()	{ return isSingle() && radSingle.isSelected();} // WHERE
		
// Pair Hits 
	protected boolean isBlock() 	{return radBlkYes.isEnabled() && radBlkYes.isSelected(); }
	private boolean isNotBlock() 	{return radBlkNo.isEnabled() && radBlkNo.isSelected(); }
	
	// Annotated (Gene Hit)
	protected boolean isEveryStarAnno() {
		return (radAnnoEveryStar.isEnabled() && radAnnoEveryStar.isSelected()); 
	} 
	protected boolean isEveryAnno() {
		return (radAnnoEvery.isEnabled() && radAnnoEvery.isSelected()); 
	}
	protected boolean isOneAnno() 	 {// DBdata filter
		return radAnnoOne.isEnabled() && radAnnoOne.isSelected(); 
	} 	
	protected boolean isBothAnno()   {
		return (radAnnoBoth.isEnabled() && radAnnoBoth.isSelected()); 
	} 
	protected boolean isOlapAnno_ii()   {
		return (radAnno_ii.isEnabled() && radAnno_ii.isSelected()); 
	}
	private   boolean isNoAnno() 	 {return radAnnoNone.isEnabled() && radAnnoNone.isSelected(); } 
	
	// Collinear size
	protected boolean isCollinear() {return txtCoSetN.isEnabled() &&  !radCoSetIgn.isSelected() && txtCoSetN.getText().trim().length()> 0;} 
	
// Hit>= row
	protected boolean isGeneOlapOr(){return radOlapOr.isSelected();}
	protected boolean isGeneOlap()	{
		if (!txtGeneOlap.isEnabled()) return false;
		return (getGeneOlap()>0);
	} // DBdata filter
	protected int getGeneOlap() 	{return getValidNum(txtGeneOlap.getText().trim());}
	
// Exact Row
	protected boolean isBlockNum()		{return chkBlkNum.isEnabled() && chkBlkNum.isSelected();}
	protected boolean isCollinearNum()	{return chkCoSetNum.isEnabled() && chkCoSetNum.isSelected();}
	private   boolean isHitNum()		{return chkHitNum.isEnabled() && chkHitNum.isSelected();}
	protected boolean isGeneNum()		{return chkGeneNum.isEnabled() && chkGeneNum.isSelected();}	// mysql for num, DBdata filter for suffix; CAS579c no chk text

	protected String getGeneNum() { // DBdata filter; Text already checked
		if (!isGeneNum()) return null;
		return txtGeneNum.getText().trim();
	}

// group  
	// multi DBdata filter
	protected boolean isMultiN()  {return chkMultiN.isEnabled() && chkMultiN.isSelected();} 
	protected boolean isMultiSame() {return radMultiSame.isEnabled() && radMultiSame.isSelected();} 
	protected boolean isMultiTandem() {return radMultiTandem.isEnabled() && radMultiTandem.isSelected();} 
	protected int getMultiN() { // Multi; Text already checked
		if (!isMultiN()) return 0;
		return getValidNum(txtMultiN.getText().trim());
	}
	
	// Clust DBdata files
	protected boolean isClustN()    	{ return chkClustN.isEnabled() 	&& chkClustN.isSelected(); }
	protected boolean isClPerSp()    	{ return radClPerSp.isEnabled() && radClPerSp.isSelected(); }
	
	protected boolean isPGincTrans() 	{ return radIncTrans.isEnabled() && radIncTrans.isSelected(); }
	protected boolean isPGIncNoGene() 	{ return radIncNoGene.isEnabled()&& radIncNoGene.isSelected(); }
	protected boolean isPGIncOne()    	{ return radIncOne.isEnabled() 	&& radIncOne.isSelected(); }
	protected boolean isPGIncIgn()    	{ return radIncIgn.isEnabled() 	&& radIncIgn.isSelected(); }
	
	protected boolean isClInclude(int sp) { return incSpecies[sp].isEnabled() && incSpecies[sp].isSelected(); }
	protected boolean isClExclude(int sp) { return exSpecies[sp].isEnabled()  && exSpecies[sp].isSelected();}
	protected int getClustN() { // Clust; Text already checked
		if (!isClustN()) return 0;
		return getValidNum(txtClustN.getText().trim());
	}
	protected HashSet <Integer> getExcSpPos() {
		HashSet <Integer> spIdx = new HashSet <Integer> ();
		for(int p=0; p<nSpecies; p++) {
			if (isClExclude(p)) spIdx.add(p);
		}
		return spIdx;
	}

	/********************************************************************
	 * XXX Filter panels
	 ***************************************************************/
	private void createAllPanels() {
		spPanel = new SpeciesPanel(qFrame, this);
		nSpecies = spPanel.getNumSpecies();			
		
		buttonPanel   = createButtonPanel();
		searchPanel   = createFilterAnnoPanel();
		singlePanel   = createFilterSinglePanel();
		pairRadPanel  = createFilterPairRadPanel();
		pairChkPanel  = createFilterPairExactPanel();
		pairHitPanel  = createFilterPairHitPanel();
		groupPanel    = createFilterGroupPanel();
	 
		add(buttonPanel);	add(Box.createVerticalStrut(10));
		
		pnlStep1 = new CollapsiblePanel("1. General", ""); // 2nd string goes under section title
		pnlStep1.add(searchPanel);		
		pnlStep1.add(spPanel);	
		pnlStep1.collapse();
		pnlStep1.expand();
		add(pnlStep1);	
		
		pnlStep2 = new CollapsiblePanel("2. Single genes", "");
		pnlStep2.add(singlePanel);			
		pnlStep2.collapse();
		pnlStep2.expand();
		add(pnlStep2);								
		
		pnlStep3 = new CollapsiblePanel("3. Pair hits", ""); 
		pnlStep3.add(pairRadPanel);			pnlStep3.add(Box.createVerticalStrut(5));
		pnlStep3.add(pairHitPanel);			pnlStep3.add(Box.createVerticalStrut(5));	
		pnlStep3.add(pairChkPanel);
		pnlStep3.collapse();
		pnlStep3.expand();
		add(pnlStep3);								
		
		pnlStep4 = new CollapsiblePanel("4. Compute Groups", "");
		pnlStep4.add(groupPanel);
		pnlStep4.collapse();
		pnlStep4.expand();
		add(pnlStep4);	
	}
	/** Top buttons **/
	private JPanel createButtonPanel() {
		JPanel panel = Jcomp.createRowPanel();
		
		btnExecute = Jcomp.createButton("Run Query","Apply filters and show results table"); 
		btnExecute.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setQueryButton(false); // changes to 'Running' and disable; set true in TableMainPanel after table created
				
				SQL sql = new SQL();
				String query = sql.makeSQLclause();
				
				if (query!=null) {
					String sum = sql.makeSummary();
					qFrame.makeTabTable(query, sum, isSingle());
				}
				else setQueryButton(true);
			}
		});
		JButton btnClear = Jcomp.createButton("Clear Filters", "Clear text fields and reset");
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setClearDefaults();
			}
		});
		JButton btnHelp = Jhtml.createHelpIconQuery(Jhtml.query);
		
		panel.add(btnClear);		panel.add(Box.createHorizontalStrut(170));
		panel.add(btnExecute);		panel.add(Box.createHorizontalStrut(220));
		panel.add(btnHelp);
		
		panel.setMaximumSize(panel.getPreferredSize());
		return panel;
	}
	/** Annotation **/
	private JPanel createFilterAnnoPanel() {
		JPanel page = Jcomp.createPagePanelNB();
		JPanel row = Jcomp.createRowPanel();
		
		chkAnno = Jcomp.createCheckBox("Annotation Description","A text string from the Gene Annotation", false);
		chkAnno.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setAnnoSingleRow();
			}
		});
		txtAnno = Jcomp.createTextField("", 30); txtAnno.setEnabled(false);
		
		row.add(chkAnno); row.add(Box.createHorizontalStrut(10));
		row.add(txtAnno);   row.setMaximumSize(row.getPreferredSize());
		
		if (!bNoAnno) page.add(row); 
		return page;
	}
	/** Orphan or Single genes **/
	private JPanel createFilterSinglePanel() {
		JPanel panel = Jcomp.createPagePanelNB();
		
		JPanel row = Jcomp.createRowPanel();
		chkSingle = Jcomp.createCheckBox("Single", "Returns columns of gene information (not hit information)", true);
		chkSingle.addActionListener(new ActionListener() { // Disabled others if orphans checked
			public void actionPerformed(ActionEvent e) {
				setAnnoSingleRow();
			}
		});
		row.add(chkSingle); row.add(Box.createHorizontalStrut(5));
		
		radOrphan = Jcomp.createRadio("Orphan genes (no hits)", "Genes with no hits");
		radSingle = Jcomp.createRadio("All genes (w/o hits)", "All genes in project");
		ButtonGroup bg = new ButtonGroup();
		bg.add(radOrphan); bg.add(radSingle); radSingle.setSelected(true);
		
		row.add(radOrphan); row.add(Box.createHorizontalStrut(5));
		row.add(radSingle);	
		
		if (!bNoAnno) panel.add(row); 
		else {
			JPanel row1 = Jcomp.createRowPanel();
			row1.add(Jcomp.createLabel("No annotated genes"));
			panel.add(row1);
		}
		return panel;
	}
	/** Radio buttons **/
	private JPanel createFilterPairRadPanel() {
		JPanel panel = Jcomp.createPagePanelNB();
		
		// block
		JPanel brow = Jcomp.createRowPanel();
		lblBlkOpts = Jcomp.createLabel("In Block (Synteny hit)", "Hit is in a synteny block");
		radBlkYes =  Jcomp.createRadio("Yes", "Only list hits in synteny blocks"); 	
		radBlkNo =   Jcomp.createRadio("No", "Only list hits that are not in a synteny block"); 	
		radBlkIgn =  Jcomp.createRadio("Ignore", "Does not matter if hit is in a block or not"); 
		
		brow.add(lblBlkOpts); 	
		if (Jcomp.isWidth(wFil1, lblBlkOpts)) brow.add(Box.createHorizontalStrut(Jcomp.getWidth(wFil1, lblBlkOpts)));
		brow.add(radBlkYes);  brow.add(Box.createHorizontalStrut(1));
		brow.add(radBlkNo);	  brow.add(Box.createHorizontalStrut(1));
		brow.add(radBlkIgn);  brow.add(Box.createHorizontalStrut(1));
		
		ButtonGroup bg = new ButtonGroup();
		bg.add(radBlkYes); bg.add(radBlkNo); bg.add(radBlkIgn); radBlkIgn.setSelected(true);
		
		panel.add(brow); panel.add(Box.createVerticalStrut(5));
		
		// anno
		JPanel arow = Jcomp.createRowPanel();;
		lblAnnoOpts 	 = Jcomp.createLabel("Annotated (Gene hit)", "Hit to a gene, hence, annotated hit");
		radAnnoEvery     = Jcomp.createRadio("Every", "Every hit aligned to at least one gene - best gene listed"); 	
		radAnnoEveryStar = Jcomp.createRadio("Every" + Globals.minorAnno, "Every hit aligned to at least one gene - ALL genes listed"); 
		radAnnoOne 		 = Jcomp.createRadio("One", "Every hit aligned to only one gene - best gene listed"); 		
		radAnnoBoth 	 = Jcomp.createRadio("Both", "Every hit aligned to two genes - best genes listed"); 	
		radAnnoNone 	 = Jcomp.createRadio("None", "Every hit aligned to no genes"); 	
		radAnnoIgn 		 = Jcomp.createRadio("Ignore", "Does not matter if hit aligned to gene or not"); 	
		
		String olap = (isMinorOlap) ? "Minor" : "Overlap"; //  -ii only
		radAnno_ii = Jcomp.createRadio(olap, "Only " + olap + " genes"); 	
		
		arow.add(lblAnnoOpts); 	
		if (Jcomp.isWidth(wFil1, lblAnnoOpts)) arow.add(Box.createHorizontalStrut(Jcomp.getWidth(wFil1, lblAnnoOpts)));
		
		arow.add(radAnnoEvery);		brow.add(Box.createHorizontalStrut(1));
		if (!isSelf) {arow.add(radAnnoEveryStar);	brow.add(Box.createHorizontalStrut(1)); }
		arow.add(radAnnoOne);		brow.add(Box.createHorizontalStrut(1));
		if (!bOneAnno) {arow.add(radAnnoBoth);		brow.add(Box.createHorizontalStrut(1));}
		arow.add(radAnnoNone);		brow.add(Box.createHorizontalStrut(1));
		arow.add(radAnnoIgn);	brow.add(Box.createHorizontalStrut(1));
		if (Globals.INFO) {arow.add(radAnno_ii);	brow.add(Box.createHorizontalStrut(1));} // -ii
		
		ButtonGroup ag = new ButtonGroup();
		ag.add(radAnnoEvery); ag.add(radAnnoEveryStar); ag.add(radAnnoOne); ag.add(radAnnoBoth);
		ag.add(radAnno_ii); ag.add(radAnnoNone); ag.add(radAnnoIgn);
		radAnnoIgn.setSelected(true);
		
		if (!bNoAnno) {
			panel.add(arow); panel.add(Box.createVerticalStrut(5));
		}
		// collinear
		JPanel crow = Jcomp.createRowPanel();;
		lblCoSetN = Jcomp.createLabel("Collinear size ", "Size of collinear set of genes");
		crow.add(lblCoSetN); 
		
		txtCoSetN = Jcomp.createTextField("0", 3); 
		crow.add(txtCoSetN); 
		int w = (int) (lblCoSetN.getPreferredSize().getWidth() + txtCoSetN.getPreferredSize().getWidth() - 1);
		if (wFil1 > w) crow.add(Box.createHorizontalStrut(wFil1-w));
		
		radCoSetGE =  Jcomp.createRadio(">=", "Show collinear sets >= N"); 
		radCoSetEQ =  Jcomp.createRadio("=", "Show collinear sets = N");  
		radCoSetLE =  Jcomp.createRadio("<=" , "Show collinear sets <= N"); 
		radCoSetIgn = Jcomp.createRadio("Ignore", "Do not search on sets"); 
		
		ButtonGroup g = new ButtonGroup();
		g.add(radCoSetGE); g.add(radCoSetEQ); g.add(radCoSetLE); g.add(radCoSetIgn);
		radCoSetIgn.setSelected(true);
		crow.add(radCoSetGE); crow.add(Box.createHorizontalStrut(1));
		crow.add(radCoSetEQ); crow.add(Box.createHorizontalStrut(1));
		crow.add(radCoSetLE); crow.add(Box.createHorizontalStrut(1));
		crow.add(radCoSetIgn);
		if (!bNoAnno && !bOneAnno) panel.add(crow); 
		
		radCoSetGE.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {txtCoSetN.setEnabled(true);}});
		radCoSetEQ.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {txtCoSetN.setEnabled(true);}});
		radCoSetLE.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {txtCoSetN.setEnabled(true);}});
		radCoSetIgn.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {txtCoSetN.setEnabled(false);}});
		txtCoSetN.setText(coDefNum); txtCoSetN.setEnabled(false); 
		
		return panel;
	}
	/** Check boxes **/
	private JPanel createFilterPairExactPanel() {
		JPanel panel = Jcomp.createPagePanelNB();
		JPanel row = Jcomp.createRowPanel();
		int sp1 = 3, sp2 = 11;
		
		lblChkExact = Jcomp.createLabel("Exact", "Search for exact string; do not prefix with chromosome numbers");
		row.add(lblChkExact); row.add(Box.createHorizontalStrut(Jcomp.getWidth(wFil2-3, lblChkExact)));
		
		chkBlkNum = Jcomp.createCheckBox("Block#", "Only the block number", true); 
		chkBlkNum.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setExactRow(chkBlkNum.isSelected(), nChkBlk, SpeciesPanel.locOnly);
			}
		});
		txtBlkNum = Jcomp.createTextField("", "Enter Block# only", 3, true); 
		row.add(chkBlkNum); row.add(Box.createHorizontalStrut(sp1));
		row.add(txtBlkNum);   row.add(Box.createHorizontalStrut(sp2));
		
		chkCoSetNum = Jcomp.createCheckBox("Collinear set#", "Only the collinear set number", true); 
		chkCoSetNum .addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setExactRow(chkCoSetNum.isSelected(), nChkCoSet, SpeciesPanel.locOnly);
			}
		});
		txtCoSetNum  = Jcomp.createTextField("", "Enter Collinear# only", 3, true); 
		if (!bNoAnno && !bOneAnno) {
			row.add(chkCoSetNum); row.add(Box.createHorizontalStrut(sp1));
			row.add(txtCoSetNum); row.add(Box.createHorizontalStrut(sp2));
		}
		chkHitNum = Jcomp.createCheckBox("Hit#", "Enter Hit# (same as column Hit#)", true); 
		chkHitNum.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setExactRow(chkHitNum.isSelected(), nChkHit, SpeciesPanel.locOnly);
			}
		});
		txtHitNum = Jcomp.createTextField("", "Enter Hit# ", 4, true); 
		row.add(chkHitNum); row.add(Box.createHorizontalStrut(sp1));
		row.add(txtHitNum);   row.add(Box.createHorizontalStrut(sp2));
		
		chkGeneNum = Jcomp.createCheckBox("Gene#", "A Gene# with or without a suffix", true);
		chkGeneNum.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setExactRow(chkGeneNum.isSelected(), nChkGene, SpeciesPanel.locChr);
			}
		});
		txtGeneNum = Jcomp.createTextField("", "Enter Gene# only", 4, true); 
		if (!(bNoAnno && bNoPseudo)) {// if noAnno has pseudo, they can be searched on
			row.add(chkGeneNum); row.add(Box.createHorizontalStrut(sp1));
			row.add(txtGeneNum); 	
		}
		row.setMaximumSize(row.getPreferredSize());
		row.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(row);
		
		return panel;
	}
	/** Filter Hit attributes - active with all pair searches except Chks **/
	private JPanel createFilterPairHitPanel() {// CAS578 add
		JPanel panel = Jcomp.createPagePanelNB(); 
		JPanel row = Jcomp.createRowPanel();
		
		int w=2;
		lblHitId = Jcomp.createLabel("%Id ",  "Approximate percent identity of subhits");
		txtHitId = Jcomp.createTextField("0", "Approximate percent identity of subhits", w, true);
		
		lblHitSim = Jcomp.createLabel("%Sim ", "Approximate percent similarity of subhits");
		txtHitSim = Jcomp.createTextField("0", "Approximate percent similarity of subhits", w, true);
		
		lblHitCov = Jcomp.createLabel("Cov ", "Largest summed merged subhit lengths of the two species");
		txtHitCov = Jcomp.createTextField("0", "Largest summed merged subhit lengths of the two species", w*2, true);
		
		lblGeneOlap = Jcomp.createLabel(olapDesc + " %Olap ", olapDesc + " overlap");
		txtGeneOlap = Jcomp.createTextField("0", olapDesc + " overlap", w, true);
		radOlapOr   = Jcomp.createRadio("Either", "At least one has %Olap>=N");
		radOlapAnd  = Jcomp.createRadio("Both", "Both have %Olap>=N");
		ButtonGroup o = new ButtonGroup(); o.add(radOlapOr); o.add(radOlapAnd); radOlapOr.setSelected(true);
		
		w=15;
		lblHitGE = Jcomp.createLabel("Hit >=  ", "Value >= input number");
		row.add(lblHitGE);		row.add(Box.createHorizontalStrut(Jcomp.getWidth(wFil2, lblHitGE)));
		row.add(lblHitId); 		row.add(txtHitId); 		row.add(Box.createHorizontalStrut(w));
		row.add(lblHitSim); 	row.add(txtHitSim); 	row.add(Box.createHorizontalStrut(w));
		row.add(lblHitCov); 	row.add(txtHitCov); 	
		if (!bNoAnno) {
			row.add(Box.createHorizontalStrut(w*3));
			row.add(lblGeneOlap); 	row.add(txtGeneOlap); row.add(Box.createHorizontalStrut(2));
			if (!bOneAnno) {row.add(radOlapOr); row.add(Box.createHorizontalStrut(2)); row.add(radOlapAnd);}
		}
		
		row.setMaximumSize(row.getPreferredSize());
		row.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(row);
		return panel;
	}
	/** Filter Multi-hit and Cluster hits **/
	private JPanel createFilterGroupPanel() {		
		JPanel panel = Jcomp.createPagePanelNB(); 	
		
	/* Multi-hit geness */
		JPanel mrow = Jcomp.createRowPanel();
		chkMultiN = Jcomp.createCheckBox("Multi-hit genes ", "Genes with multiple hits to the same species; sets Group column", false);
		chkMultiN.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				if (chkMultiN.isSelected()) {
					setChkAll(nChkMulti);	// checks off except this one	
					setCoSetRow(false); 
				}
				else {
					setChkAll(nChkNone);
					setCoSetRow(true); 
				}
				boolean tip = chkClustN.isSelected() || chkMultiN.isSelected();
				lblGrpTip.setEnabled(tip);
				setRefresh();
		}});
		mrow.add(chkMultiN); 
		
		lblMultiN = Jcomp.createLabel(">=  ", ">= N hits"); 
		mrow.add(lblMultiN);  
		
		txtMultiN = Jcomp.createTextField(groupDefNum, ">= N hits", 3); 
		mrow.add(txtMultiN); 
		mrow.add(Box.createHorizontalStrut(5));
		
		chkMultiMinor = Jcomp.createCheckBox("Minor*", "If checked, include minor gene hits", false);
		mrow.add(chkMultiMinor); 
		
		mrow.add(Box.createHorizontalStrut(6));
		lblMultiOpp = Jcomp.createLabel("  Opposite: ", "Opposite chromosome");
		mrow.add(lblMultiOpp);
		
		radMultiTandem = Jcomp.createRadio("Tandem", "If checked, the N hits for a gene must be to sequential genes");
		if (!bOneAnno) {mrow.add(radMultiTandem); mrow.add(Box.createHorizontalStrut(1));}
		
		radMultiSame = Jcomp.createRadio("Same Chr", "If checked, the N hits for a gene must be to the same opposite chromosome");
		mrow.add(radMultiSame); mrow.add(Box.createHorizontalStrut(1));
		
		radMultiDiff = Jcomp.createRadio("Diff Chr", "If checked, the N hits for a gene can be to any set of chromosomes");
		mrow.add(radMultiDiff); mrow.add(Box.createHorizontalStrut(1));
		
		ButtonGroup ag = new ButtonGroup();
		ag.add(radMultiDiff); ag.add(radMultiTandem); ag.add(radMultiSame); radMultiSame.setEnabled(true);
		
		if (!bNoMulti) {panel.add(mrow); panel.add(Box.createVerticalStrut(5));}
		
	/* Cluster hits */
		JPanel crow = Jcomp.createRowPanel();
		if (isPgeneF) chkClustN = Jcomp.createCheckBox("PgeneF", "Putative gene families; Sets Group column", false); 
		else          chkClustN = Jcomp.createCheckBox("Cluster genes", "Cluster overlapping genes; Sets Group column", false); 
		
		chkClustN.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				if (chkClustN.isSelected()) 	{
					setChkAll(nChkClust);
					setCoSetRow(false); 
				}
				else {
					setChkAll(nChkNone);
					setCoSetRow(true);
				}
				boolean tip = chkClustN.isSelected() || chkMultiN.isSelected();
				lblGrpTip.setEnabled(tip);
				setRefresh();
			}
		});
		chkClustN.setSelected(false);
		crow.add(chkClustN);
		int w = chkMultiN.getPreferredSize().width;
		crow.add(Box.createHorizontalStrut(w - chkClustN.getPreferredSize().width));
		
		lblClustN = Jcomp.createLabel(">=  ", ">= N"); 
		crow.add(lblClustN);  
		
		txtClustN = Jcomp.createTextField(groupDefNum, ">= N", 3); 
		crow.add(txtClustN); 	crow.add(Box.createHorizontalStrut(8));
		
		// crow for Cluster only;
		radClHit   = Jcomp.createRadio("Total hits", ">=N hits with shared genes");
		radClPerSp = Jcomp.createRadio("Genes per species", ">=N unique genes per species");
		if (!isPgeneF) {
			crow.add(radClHit); crow.add(Box.createHorizontalStrut(8)); crow.add(radClPerSp);
			
			ButtonGroup rad = new ButtonGroup();
			rad.add(radClHit); rad.add(radClPerSp); radClHit.setSelected(true);
		}
			
		// crow for PgeneF only; Originally No Annotation, Complete Linkage, At least one checkboxes; now can only select one
		lblIncFilters = Jcomp.createLabel("Include: ", "Filters for included species");
		radIncOne    = Jcomp.createRadio("At least one", "Each group must have at least one of each included species");
		radIncIgn    = Jcomp.createRadio("Ignore", "No filters on included species");
		radIncNoGene = Jcomp.createRadio("No gene","The included species may not have hits that align to genes");
		radIncTrans  = Jcomp.createRadio("Linkage", "For >2 species: for hits A-B and B-C, there must also be hit A-C");
		
		ButtonGroup inc = new ButtonGroup();
		if (isPgeneF) { 
			crow.add(lblIncFilters); 	crow.add(Box.createHorizontalStrut(2));
			crow.add(radIncNoGene); 	crow.add(Box.createHorizontalStrut(2));
			inc.add(radIncNoGene); 
			
			if (nSpecies>2) {
				crow.add(radIncOne); 		crow.add(Box.createHorizontalStrut(2));
				crow.add(radIncTrans);  	crow.add(Box.createHorizontalStrut(2));
				crow.add(radIncIgn);  		crow.add(Box.createHorizontalStrut(2));
				inc.add(radIncOne); inc.add(radIncTrans); inc.add(radIncIgn); radIncOne.setSelected(true);
			}
			else {
				crow.add(radIncIgn);  crow.add(Box.createHorizontalStrut(2));
				inc.add(radIncIgn); radIncIgn.setSelected(true);
			}
		}
		if (!bNoClust) panel.add(crow);  // Vertical box in if statement 
		
		// Include/exclude
		Vector<Mproject> theProjects = qFrame.getProjects();
		speciesName = new String [nSpecies];
		speciesAbbr = new String [nSpecies];
		incSpecies = new JCheckBox[nSpecies]; // vector of checkboxes
		for(int x=0; x<nSpecies; x++) {
			speciesAbbr[x] = theProjects.get(x).getdbAbbrev();
			speciesName[x] = theProjects.get(x).getDisplayName();
			incSpecies[x] = Jcomp.createCheckBox(speciesName[x], true);
			incSpecies[x].addActionListener(new ActionListener() { 
				public void actionPerformed(ActionEvent e) {
					int cnt=0;
					for (int x=0; x<nSpecies; x++) {
						if (incSpecies[x].isSelected()) {
							exSpecies[x].setSelected(false);
							cnt++;
						}
					}
					radIncOne.setEnabled(cnt>0); 
					radIncNoGene.setEnabled(cnt>0);
					radIncTrans.setEnabled(cnt>0);
					radIncIgn.setEnabled(cnt>0);
				}
			});
		}
		exSpecies = new JCheckBox[theProjects.size()];
		for(int x=0; x<exSpecies.length; x++) {
			exSpecies[x] = Jcomp.createCheckBox(speciesName[x], false);
			exSpecies[x].addActionListener(new ActionListener() { 
				public void actionPerformed(ActionEvent e) {
					for (int x=0; x<nSpecies; x++) {
						if (exSpecies[x].isSelected()) 
							incSpecies[x].setSelected(false);
					}
					int cnt=0;
					for (int x=0; x<nSpecies; x++) if (incSpecies[x].isSelected()) cnt++;
					radIncOne.setEnabled(cnt>0);
					radIncNoGene.setEnabled(cnt>0);
					radIncTrans.setEnabled(cnt>0);
					radIncIgn.setEnabled(cnt>0);
				}
			});
		}		
		
		lblInclude = Jcomp.createLabel("  Include: ");
		lblExclude = Jcomp.createLabel("  Exclude: ");
		if (nSpecies>2 && !bNoClust) {
			JPanel row = Jcomp.createRowPanel();
			
			row.add(lblInclude);
			for(int x=0; x<incSpecies.length; x++) {
				row.add(incSpecies[x]); row.add(Box.createHorizontalStrut(5));
			}
			panel.add(row);
	
			row = Jcomp.createRowPanel();		
			row.add(lblExclude);
			for(int x=0; x<exSpecies.length; x++) {
				row.add(exSpecies[x]); row.add(Box.createHorizontalStrut(5));
			}
			panel.add(row);
			panel.setMaximumSize(panel.getPreferredSize());
		}
		panel.add(Box.createVerticalStrut(15));
		
		if (!isSelf && !bNoMulti && !bNoClust) {
			String tip = "Tips: Use Hit filters for stricter groups.";
			if (!bNoPseudo) tip += " To ignore Pseudo, select Annotated 'Both'. ";
			lblGrpTip = Jcomp.createLabel(tip);  lblGrpTip.setEnabled(false);
			panel.add(lblGrpTip);
			panel.setMaximumSize(panel.getPreferredSize());
		}
		else {
			String tip;
			if (isSelf) 					tip = "Not available for self-synteny: Every+, Minor hits, Groups";
			else if (bNoPseudo && !bNoAnno) tip = "Clusters: Must have pseudo genes computed for un-annotated";
			else 							tip = "Groups: Must have at least one project with annotated genes";
			lblGrpTip = Jcomp.createLabel(tip); lblGrpTip.setEnabled(true);
			panel.add(lblGrpTip);
			panel.setMaximumSize(panel.getPreferredSize());
		}
		return panel;
	}
	/**************************************************************
	 * XXX Clears and enables;
	 */
	private void setClearDefaults() {
		// Anno/Chr 
		txtAnno.setText(""); txtAnno.setEnabled(false); 
		chkAnno.setEnabled(true); chkAnno.setSelected(false);
		spPanel.setClear();  
		
		// Radio buttons for singles and pairs
		setRadioRows(true);
		radBlkIgn.setSelected(true);   radAnnoIgn.setSelected(true);  radCoSetIgn.setSelected(true); 
		
		// Exact
		txtCoSetNum.setText(""); txtBlkNum.setText(""); txtHitNum.setText(""); 	txtGeneNum.setText(""); 	
		if (txtCoSetN.getText().isEmpty()) txtCoSetN.setText(coDefNum); 
		
		// Hit 
		setHitRow(true);
		txtHitId.setText("0"); txtHitSim.setText("0"); txtHitCov.setText("0"); txtGeneOlap.setText("0");
		radOlapOr.setSelected(true); 
		
		// Groups
		chkMultiN.setSelected(false);
		if (txtMultiN.getText().isEmpty()) txtMultiN.setText(groupDefNum); 
		radMultiSame.setSelected(true); 
		setChkAll(nChkNone);
		
		chkClustN.setSelected(false);
		radClPerSp.setSelected(false);
		if (txtClustN.getText().isEmpty()) txtClustN.setText(groupDefNum); 
		if (nSpecies==2) radIncIgn.setSelected(true);
		else 			 radIncOne.setSelected(true);
		for (int x=0; x<incSpecies.length; x++) incSpecies[x].setSelected(true);
		for (int x=0; x<exSpecies.length; x++)  exSpecies[x].setSelected(false);
		
		setRefresh();
	}
	// All checks (except Anno) and clear; everything turned off except for nChk
	// nChkNone=0, nChkSingle=1, nChkBlk=2, nChkCoSet=3, nChkHit=4, nChkGene=5, nChkMulti=6, nChkClust=7;
	private void setChkAll(int nChk) {  
		if (nChk==nChkSingle || nChk==nChkMulti || nChk==nChkClust) lblChkExact.setEnabled(false);
		else                                                        lblChkExact.setEnabled(true); 
		
		boolean b, bn = (nChk==nChkNone);
		
		b = (nChk==nChkSingle);
		chkSingle.setEnabled(bn || b); chkSingle.setSelected(b);
		radOrphan.setEnabled(b);  radSingle.setEnabled(b);
		
		b = (nChk==nChkBlk);
		chkBlkNum.setEnabled(bn || b);  chkBlkNum.setSelected(b);    
		txtBlkNum.setEnabled(b); 	
		
		b = (nChk==nChkCoSet);
		chkCoSetNum.setEnabled(bn || b); chkCoSetNum.setSelected(b);  
		txtCoSetNum.setEnabled(b); 	
		
		b = (nChk==nChkHit);
		chkHitNum.setEnabled(bn || b);  chkHitNum.setSelected(b);    
		txtHitNum.setEnabled(b);	
		
		b = (nChk==nChkGene);
		chkGeneNum.setEnabled(bn || b);  chkGeneNum.setSelected(b);   
		txtGeneNum.setEnabled(b);	
		
		b = (nChk==nChkMulti);
		chkMultiN.setEnabled(bn || b);  chkMultiN.setSelected(b);  
		lblMultiN.setEnabled(b);    	txtMultiN.setEnabled(b); 
		lblMultiOpp.setEnabled(b);  	chkMultiMinor.setEnabled(b); 
		radMultiSame.setEnabled(b);  	radMultiTandem.setEnabled(b); radMultiDiff.setEnabled(b);
		
		b = (nChk==nChkClust);
		chkClustN.setEnabled(bn || b);  chkClustN.setSelected(b); 
		lblClustN.setEnabled(b);  		txtClustN.setEnabled(b);
		radClHit.setEnabled(b); 		radClPerSp.setEnabled(b);
		lblInclude.setEnabled(b); lblExclude.setEnabled(b); lblIncFilters.setEnabled(b);
		
		for(int x=0; x<exSpecies.length; x++)  exSpecies[x].setEnabled(b);
		
		// the Inc check boxes should not be active if nothing checked
		int cnt=0;
		for(int x=0; x<incSpecies.length; x++) {
			incSpecies[x].setEnabled(b);
			if (b && incSpecies[x].isSelected()) cnt++; 
		}
		radIncOne.setEnabled(cnt>0);
		radIncNoGene.setEnabled(cnt>0);
		radIncTrans.setEnabled(cnt>0);
		radIncIgn.setEnabled(cnt>0);
	}
	// Single and Anno can both be checked at same time; both turn on locChr; 
	// Single turns off everything else; Anno only turns off Exact
	private void setAnnoSingleRow() {// CAS579c add 
		boolean bSingle = chkSingle.isSelected();
		boolean bAnno = isAnnoStr();
		
		txtAnno.setEnabled(bAnno);
	
		if (bSingle) { 
			setChkAll(nChkSingle); // Disable Exact rows
			setRadioRows(false); 
			setHitRow(false);
			spPanel.setChkEnable(true, SpeciesPanel.locChr, isSelf); 
		}
		else if (bAnno) { 
			setChkAll(nChkSingle); // disable Exact
			chkSingle.setEnabled(true); chkSingle.setSelected(false); // Now enable single check
			radOrphan.setEnabled(false);  radSingle.setEnabled(false);
			
			setRadioRows(true); 
			setHitRow(true);
			spPanel.setChkEnable(true, SpeciesPanel.locChr, isSelf); 
		}
		else {
			setChkAll(nChkNone);
			setRadioRows(true); 
			setHitRow(true);
			spPanel.setChkEnable(false, SpeciesPanel.locChr, isSelf); 
		}
		
		setRefresh();
	}
	// In Block, Annotated (Gene), CoSet,  Clear 
	private void setRadioRows(boolean b) { 
		lblBlkOpts.setEnabled(b); radBlkYes.setEnabled(b);radBlkNo.setEnabled(b);radBlkIgn.setEnabled(b);
		
		lblAnnoOpts.setEnabled(b); radAnnoEvery.setEnabled(b);radAnnoEveryStar.setEnabled(b);
		radAnnoOne.setEnabled(b);  radAnnoBoth.setEnabled(b); radAnno_ii.setEnabled(b); 
		radAnnoNone.setEnabled(b); radAnnoIgn.setEnabled(b);
		
		setCoSetRow(b);
	}
	// Coset row - as needed for Groups
	private void setCoSetRow(boolean b) { 
		lblCoSetN.setEnabled(b);  txtCoSetN.setEnabled(b); 	
		radCoSetGE.setEnabled(b); radCoSetEQ.setEnabled(b);
		radCoSetLE.setEnabled(b); radCoSetIgn.setEnabled(b);
		
		if (b) txtCoSetN.setEnabled(!radCoSetIgn.isSelected());
		else   txtCoSetN.setEnabled(b);
	}

	// Hit panel: called by setClearDefaults, setExactRow, setSingleRow
	private void setHitRow(boolean b) { 
		lblHitGE.setEnabled(b);
		lblHitId.setEnabled(b);    txtHitId.setEnabled(b); 
		lblHitSim.setEnabled(b);   txtHitSim.setEnabled(b); 
		lblHitCov.setEnabled(b);   txtHitCov.setEnabled(b); 
		lblGeneOlap.setEnabled(b); txtGeneOlap.setEnabled(b); 
		radOlapOr.setEnabled(b); radOlapAnd.setEnabled(b);
	}
	
	// Exact (Block, Hit, Collinear, Gene); 
	// nChk is the number representing Checkbox, type is Species settings for Species Check and Start/end
	private void setExactRow(boolean isSel, int nChk, int type) { 
		if (isSel) setChkAll(nChk);
		else       setChkAll(nChkNone);
			
		setRadioRows(!isSel); 
		setHitRow(!isSel);
	
		chkAnno.setEnabled(!isSel); 
		txtAnno.setEnabled(!isSel); 
		
		spPanel.setChkEnable(isSel, type, (isSelf && nChk==nChkSingle)); 
		
		setRefresh();
	}
	
	// panels do not repaint right on enable if this is not included
	private void setRefresh() {
		searchPanel.revalidate(); searchPanel.repaint();
		singlePanel.revalidate(); singlePanel.repaint();
		pairRadPanel.revalidate(); pairRadPanel.repaint();
		pairChkPanel.revalidate(); pairChkPanel.repaint();
		groupPanel.revalidate(); groupPanel.repaint();
	}
	protected void showWarnMsg(String msg) {
		JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE);
	}
	private int getValidNum(String text) {
		try {
			return Integer.parseInt(text);
		}
		catch (Exception e) {return -1;}
	}
	protected void setQueryButton(boolean b) {
		if (b) btnExecute.setText("Run Query");
		else btnExecute.setText("Running...");
		btnExecute.setEnabled(b);
	}
	
	/***************************************************************
	 * XXX Make Query: All SQL and Summary methods CAS579c
	 */
	private class SQL { 
		private boolean bValidQuery=true;
		
		private String makeSQLclause() {
			grpCoords.clear(); // in case makeSpChrWhere is not called

			bValidQuery=true;
			TableColumns theFields = TableColumns.getFields(isIncludeMinor(), qFrame.isAlgo2()); 
			boolean bSingle = isSingle();
			
			String sql = "SELECT " + theFields.getDBFieldList(bSingle);
			if (bSingle) sql += makeSQLsingle();
			else         sql += makeSQLpair(theFields.getJoins());

			if (!bValidQuery) return null;
			return sql;
		}
		/****************************************************
		 * Single all genes:
		 *	SELECT PA.idx, PA.grp_idx, PA.start, PA.end, PA.strand, PA.name, PA.tag, PA.numhits 
		 *      FROM pseudo_annot AS PA
		 *		WHERE  PA.type='gene' AND PA.grp_idx in (1,2,4,3) order by PA.idx
		 */
		private String makeSQLsingle() {
			boolean bOrphan = isSingleOrphan();
			
			String from = " FROM pseudo_annot AS " + Q.PA;
			
			String whereClause;
			if (bOrphan)  whereClause = " WHERE not exists " +
				"\n(SELECT * FROM pseudo_hits_annot AS PHA " +
				"\nLEFT JOIN pseudo_hits as PH on PH.idx=PHA.hit_idx " + // join to restrict pairs
				"\nWHERE PA.idx=PHA.annot_idx and " + spPanel.getPairIdxWhere() + ") and ";
			else 
				whereClause=	"\nWHERE ";
			
			whereClause += " PA.type='gene'"; 
			
		// Annotation
			if (isAnnoStr()) {
				String annoStr = txtAnno.getText();
				if (annoStr.equals("")) { 
					showWarnMsg("No annotation string entered\n");
					bValidQuery=false;
					return "";	
				}
				String anno = "PA.name LIKE '%" + annoStr + "%'";
				whereClause = makeJoinBool(whereClause, anno, AND);
			}
			String grp = "(PA.grp_idx IN (" + spPanel.getGrpIdxStr() + ")) ";
			whereClause = makeJoinBool(whereClause, grp, AND); 
			
			if (!bOrphan) whereClause += " order by PA.idx";

			return from + whereClause;
		}
		
		/*********************************************************
		 *  Pair: 
		 *	SELECT PA.idx, PA.grp_idx, PA.start, PA.end, PA.strand, PA.name, PA.tag, PA.numhits, 
		 *      PH.idx, PH.hitnum, 
		 *      PH.proj1_idx, PH.proj2_idx, PH.grp1_idx, PH.grp2_idx, PH.start1, PH.start2, PH.end1, PH.end2, 
		 *      PH.pctid, PH.cvgpct, PH.countpct, PH.strand, PH.score, PH.htype, PH.runsize, PH.runnum, 
		 *      B.blocknum, B.score, 
		 *      PH.annot1_idx, PH.annot2_idx # not displayed, used to join two rows; allGenes PHA.annot_idx, PHA.annot2_idx
		 *      FROM 	  pseudo_hits 		AS PH
		 *		LEFT JOIN pseudo_hits_annot AS PHA ON PH.idx = PHA.hit_idx
		 *		LEFT JOIN pseudo_annot 		AS PA ON PHA.annot_idx = PA.idx
		 *		LEFT JOIN pseudo_block_hits AS PBH ON PBH.hit_idx = PH.idx
		 *		LEFT JOIN blocks AS B ON B.idx = PBH.block_idx  where PH.pair_idx=1   ORDER BY PH.hitnum  asc
		 * Hit SELECT " + getDBFieldList() + " FROM pseudo_hits AS " + Q.PH + whereClause
		 * isOneAnno and annotation requires post-processing, though anno exists is checked below
		 * pseudo_hits: pctid=avg %id mummer, cvgpct=avg %sim, score=summed length
		 * pseudo_hits_annot: olap, exlap - use Q.olapCol
		 */
		private String makeSQLpair(String join) {
			// these are the only text boxes not checked until computed
			if (isMultiN())   isValidMulti();   				if (!bValidQuery) return "";
			if (isClustN())   isValidCluster(); 				if (!bValidQuery) return "";
			if (isGeneOlap()) isValidHit(txtGeneOlap, "Olap");  if (!bValidQuery) return "";
			if (isGroup()) 	  isOneChrChkOnly();				if (!bValidQuery) return "";// CAS579b they do not work
			if (isNoAnno())	  isNoAnnoOnly();					if (!bValidQuery) return "";// CAS579c make sure this is not selected with anno
			
			// The where clause is fully computed, and the bValidQuery checked at the end in makeSQLclause
			String whereClause = "\n where " + spPanel.getPairIdxWhere(); // PH.pair_idx in (...)
			
			// General: these work with almost everything (disabled if not)
			String chrLoc = makeSpLocWhere();
			whereClause = makeJoinBool(whereClause, chrLoc, AND);
			
			if (isAnnoStr()) {
				whereClause = makeJoinBool(whereClause, makeChkAnnoWhere(), AND);
			}
			
			if (isSelf) { // DIR_SELF 
				int g1 = spPanel.getSelChrIdx(0); 
				int g2 = spPanel.getSelChrIdx(1); 
				
				if (g1 == -1 && g2 == -1) {
					whereClause = makeJoinBool(whereClause, "PH.refidx=0", AND); 
				}
				else if (g1>0 && g2>0) {
					if (g1>g2)       whereClause = makeJoinBool(whereClause, "PH.refidx=0", AND); // grp1>grp2 lower tile
					else  if (g1<g2) whereClause = makeJoinBool(whereClause, "PH.refidx>0", AND); // grp1<grp2  upper tile
					else  whereClause = makeJoinBool(whereClause, "PH.start1>PH.start2", AND); 	  // below the diagonal (do not use PH.refidx=0)
				}
				else { // select 1 grpIdx
					String locStr = "((PH.grp1_idx>=PH.grp2_idx and PH.refidx=0) "
							   + " or (PH.grp1_idx<PH.grp2_idx  and PH.refidx>0))";
					whereClause = makeJoinBool(whereClause, locStr, AND);
				}
			}
		
			// Exact: only one can be selected
			if (isHitNum()) { 					
				whereClause = makeJoinBool(whereClause,  makeChkHitWhere(), AND);
			}
			else if (isGeneNum()) {    			// check suffix in DBdata.passFilters
				whereClause = makeJoinBool(whereClause, makeChkGeneNumWhere(), AND); 
			}
			else if (isBlockNum()) { 			// if block, only anno filter and chromosome
				whereClause = makeJoinBool(whereClause, makeChkBlockWhere(), AND);
			}
			else if (isCollinearNum()) { 		// if collinear, only anno filter and chromosome
				whereClause = makeJoinBool(whereClause, makeChkCollinearWhere(), AND);
			}
			// Everything else
			else {
				// Hit>=
				if (isValidHit(txtHitId, "%Id")) 	whereClause = makeJoinBool(whereClause, "PH.pctid>=" + txtHitId.getText().trim(), AND);
				if (isValidHit(txtHitSim, "%Sim")) 	whereClause = makeJoinBool(whereClause, "PH.cvgpct>=" + txtHitSim.getText().trim(), AND);
				if (isValidHit(txtHitCov, "Cov")) 	whereClause = makeJoinBool(whereClause, "PH.score>=" + txtHitCov.getText().trim(), AND);
				// Olap is done in DBdata because it checks all; will not return mate if one is < and the other >=
				
				// Block o Yes o No
				if (isBlock())         	whereClause = makeJoinBool(whereClause, "PBH.block_idx is not null", AND);
				else if (isNotBlock()) 	whereClause = makeJoinBool(whereClause, "PBH.block_idx is null", AND);
				
				// Annotate (Gene) 
				if (isNoAnno())	 {
					whereClause = makeJoinBool(whereClause, "PH.gene_overlap=0", AND); 
				}
				else if (isOneAnno())  {// finished in DBdata
					whereClause = makeJoinBool(whereClause, "PH.gene_overlap=1", AND); 
				}
				else if (isBothAnno()) 	{								
					whereClause = makeJoinBool(whereClause, "PH.gene_overlap=2", AND); 
				}
				else if (isEveryAnno() || isEveryStarAnno() || isAnnoStr() || isOlapAnno_ii())  {
					whereClause = makeJoinBool(whereClause, "PH.gene_overlap>0", AND); 
				}
				// Groups
				else if (!isPgeneF && (isMultiN() || isClustN())) {// pgenef not included because it can have all g0
					if (isClustN()) isValidCluster();
					if (isMultiTandem()) whereClause = makeJoinBool(whereClause, "PH.gene_overlap=2", AND); 
					else                 whereClause = makeJoinBool(whereClause, "PH.gene_overlap>0", AND);
				}
				// Collinear size o >= o < o =
				int n = isCoSetSzN(true);
				String op = "PH.runsize>=" + n;
				if      (radCoSetEQ.isSelected()) op = "PH.runsize=" + n;
				else if (radCoSetLE.isSelected()) op = "PH.runsize>0 and PH.runsize<=" + n;
				if (n>0) whereClause = makeJoinBool(whereClause, op, AND); 
			}
			String order = "\n  ORDER BY PH.hitnum  asc";
			return " FROM pseudo_hits AS " + Q.PH + join + " " + whereClause + order; 
		}	
		
		/******************************************************** 
		 * Search; Convert filter string to where statement 
		 *****/
		/* Make annotation string */
		// 1. the following is no faster and needs special processing; (PA.name LIKE '%" + annoStr + "%') ";
		//    if PA.name was changed to VARCHAR, it would be faster
		// 2. limiting rows based on chrs requires getting the other half in DBdata (like geneNum) - can be a lot of rows
		//    DBdata filters all rows
		private String makeChkAnnoWhere() {
			String annoStr = txtAnno.getText().trim();
			
			if (annoStr.equals("")) { 
				showWarnMsg("No annotation string entered\n");
				bValidQuery=false;
				return "";	
			}
			String where = " (PH.annot1_idx>0 or PH.annot2_idx>0) and PH.gene_overlap>0"; 
			return where;
		}
		private String makeChkBlockWhere() {
			String block = txtBlkNum.getText().trim();
			
			int n = getValidNum(block);
			if (n>0) return "B.blocknum=" + n;
			
			showWarnMsg("Invalid block number '" + block + "'. Should be single number.\n"
					+ " Set chromosomes to narrow it down to a block, e.g. Chr1.Chr2.B where B=block"); 
			txtBlkNum.setText("");
			bValidQuery=false;
			return "";
		}
		private String makeChkCollinearWhere() {
			String run = txtCoSetNum.getText().trim();
			
			int n = getValidNum(run);
			if (n>0) return "PH.runnum=" + n; 
			
			showWarnMsg("Invalid collinear set '" + run + "'. Should be single number.\n"
					+ "e.g. Collinear 2.3.100.3 has set number 100.\n"
					+ "     A set number can occur for every pair, so set the chromosomes to narrow down to a single set."); 
			bValidQuery=false;
			return "";
		}
		private String makeChkHitWhere() {
			String num = txtHitNum.getText().trim();
			
			int n = getValidNum(num);
			if (n>0) return "PH.hitnum=" + n; 
			
			showWarnMsg("Invalid hit# '" + num + "'. Should be single number.\n");
			txtHitNum.setText("");
			bValidQuery=false;
			return "";
		}
		private String makeChkGeneNumWhere() {
			String gnTxt = txtGeneNum.getText().trim();
			
			String gnStr = gnTxt;
			if (!gnStr.equals("") && Utilities.isValidGenenum(gnStr)) {
				if (gnStr.contains(".")) {
					if (gnStr.endsWith(".")) gnStr = gnStr.substring(0, gnStr.indexOf("."));
					if (gnStr.contains(".")) {
						String [] tok = gnStr.split("\\.");
						int x = Utilities.getInt(tok[0]);
						if (x != -1) gnStr = x+"";
						else         gnStr = "";
					}
				}
			} else gnStr="";
			
			if (gnStr.equals("")) {
				showWarnMsg("The Gene# entered is '" + gnTxt 
						+ "', which is not a valid Gene#. \nEnter a number, or number.suffix\n");
				txtGeneNum.setText("");
				bValidQuery=false;
				return "";	
			}
			String where = " (PH.annot1_idx>0 or PH.annot2_idx>0) and PA.genenum=" + gnStr;
			String grp = "(PA.grp_idx IN (" + spPanel.getGrpIdxStr() + ")) ";
			where = makeJoinBool(where, grp, AND); 
			
			return where;
		}
		
		// The chromosomes are queried in MySQL and the locations in DBdata; Works for both orphan and hit
		// This only works for Chr and Loc; ignored are in Single, Anno, and Gene
		private String makeSpLocWhere() {
			if (!spPanel.hasSpAllIgnoreRows()) {
				showWarnMsg("At least one species must be checked (i.e. before Species name).");
				bValidQuery=false;
				return "";
			}
			String grpList="", where="";
			grpCoords.clear();

			// First check to see if any chromosomes are selected; if not, then pairIdx is sufficient
			if (!spPanel.hasSpSelectChr() && !spPanel.hasSpIgnoreRow()) return "";

			// CAS579c was checking if species=2, and the results worked only part of the time (the ones I tested)
			for(int p=0; p<nSpecies; p++) {
				int index = spPanel.getSelChrIdx(p); // returns a PH.grp1_idx; returns -1 if disabled
				if (index <= 0) continue;
				
				if (grpList.equals("")) grpList = index + "";
				else 					grpList += "," + index;
				
				String start = spPanel.getChrStart(p);
				String stop =  spPanel.getChrStop(p);
				if (start==null || stop==null) {
					bValidQuery=false;
					return "";
				}
				if (start.equals("")) start = "0";
				if (stop.equals(""))  stop  = "0";
				if (!start.equals("0") || !stop.equals("0")) 
					grpCoords.put(index, start + Q.delim2 + stop); // for DBdata.passFilter
			}
			if (grpList.equals("")) return "";
			
			if (isSingle()) { 
				if (grpList.contains(",")) 	where  = "(PA.grp_idx IN (" + grpList + ")) ";
				else 						where  = "(PA.grp_idx = " + grpList + ") ";
			}
			else { // grp_idx is unique to one species one chromosome
				if (grpList.contains(",")) {
					where  = "((PH.grp1_idx IN (" + grpList + ")) AND (PH.grp2_idx IN (" + grpList + ")))";
				}
				else {
					where  = "((PH.grp1_idx = " + grpList + ") OR (PH.grp2_idx = " + grpList + "))";
				}
			}
			return where; 
		}
		private boolean isOneChrChkOnly() { // CAS579b add to disallow
			int cnt=0;
			
			for(int p=0; p<nSpecies; p++) {
				int index = spPanel.getSelChrIdx(p); // returns a PH.grp1_idx; returns -1 if disabled
				if (index > 0) cnt++;;
			}
			if (cnt<=1) return true;
			
			showWarnMsg("Only one chromosome may be selected for a Gene# or Group search\n");
			bValidQuery=false;
			return false;
		}
		private void isNoAnnoOnly() { // CAS579c add to disallow
			String msg=null;
			if (isAnnoStr())      msg = "None (no annotated) is set with annotation description";
			else if (isGeneOlap())msg = "None (no annotated) is set with Olap (requiring gene overlap)";
			else if (isMultiN())  msg = "None (no annotated) is set with Multi-hit gene";
			else if (isClustN())  msg = "None (no annotated) is set with Cluster gene";
			else return;
			
			showWarnMsg(msg + "\nThis will produce 0 results.");
			bValidQuery=false;
			return;
		}
		/* Size for Collinear set */
		private int isCoSetSzN(boolean prt) 	{ 
			if (!txtCoSetN.isEnabled() || radCoSetIgn.isSelected()) return 0;
			String ntext = txtCoSetN.getText();
			if (ntext.length()==0) {
				if (prt) showWarnMsg("Colliner size is blank: must be positive number."
							+ "\ne.g. Collinear 2.3.100.3 - the size is 3.");
				bValidQuery=false;
				return 0;
			}
			try {
				int n = Integer.parseInt(ntext);
				if (n<0) {
					if (prt) showWarnMsg("Invalid Colliner size (" + ntext + "), must be positive."
								+ "\n   e.g. Collinear 2.3.100.3 - the size is 3.");
					bValidQuery=false; 
					return 0;
				}
				return n;
			} catch (Exception e) { 
				if (prt) showWarnMsg("Invalid Colliner integer ("+ ntext + ")" );
				bValidQuery=false; 
			}; 
			return 0;
		}
		/* Hit >=: for all 4 Hit values */
		private boolean isValidHit(JTextField hitTxt, String col) {
			if (!hitTxt.isEnabled()) return false;
			
			String num = hitTxt.getText().trim();
			if (num.equals("") || num.equals("0")) return false;
			
			int n = getValidNum(num);
			if (n==0) return false; 
			if (n>0)  return true; 	
			
			showWarnMsg("Invalid Hit '" + col + "'. Should be single positive number.\n");
			hitTxt.setText("0");
			bValidQuery=false;
			return false;
		}
		private boolean isValidMulti() {
			if (!txtMultiN.isEnabled()) return true;
			String ntext = txtMultiN.getText();
			
			int n = getValidNum(ntext);
			if (n>1) return true;
			
			showWarnMsg("Invalid Multi size (" + ntext + "). Must be >1.");
			bValidQuery=false;
			txtMultiN.setText(groupDefNum);
			return false;
		}
		private boolean isValidCluster() {
			if (!txtClustN.isEnabled()) return true;
			String ntext = txtClustN.getText();
			
			int n = getValidNum(ntext);
			if (n<=0) {	
				showWarnMsg("Invalid Clust size (" + ntext + "). Must greater than 0.");
				bValidQuery=false;
				txtClustN.setText(groupDefNum);
				return false;
			}
			int cnt=0;
			for (int i = 0; i < nSpecies; i++){
				if (isClInclude(i)) cnt++;
			}
			if (cnt>1) return true;
			
			showWarnMsg("Must have at least two included species\n");
			bValidQuery=false;
			return false;
		}
		/*******************************************************
		 * Summary  
		 */
		private String makeSummary() { 
			int numSpecies = spPanel.getNumSpecies();
			if(numSpecies ==0) return "No species"; // not possible?
			
			String retVal="";
			String anno = (isAnnoStr()) ? txtAnno.getText() : ""; 
			String loc =  makeSummaryLoc(); 
			
			retVal = makeJoinDelim(retVal, anno, ";  "); 
			retVal = makeJoinDelim(retVal, loc, ";  "); 
			
			if (isSingle()) {
				retVal = (isSingleOrphan()) ? "Single Orphan genes" : "Single All genes";
				retVal = makeJoinDelim(retVal, loc, ";  ");
				retVal = makeJoinDelim(retVal, anno, ";  ");
				return retVal;
			}
			if (isHitNum()) {
				retVal = makeJoinDelim(retVal,  "Hit#="+txtHitNum.getText().trim(), ";  "); 
			}
			else if (isBlockNum()) {
				retVal = makeJoinDelim(retVal, "Block="+txtBlkNum.getText().trim(), ";  ");
			}
			else if (isCollinearNum()) {
				retVal = makeJoinDelim(retVal, "Collinear set "+txtCoSetNum.getText().trim(), ";  ");
			}
			else if (isGeneNum()) {
				retVal = makeJoinDelim(retVal, "Gene# "+txtGeneNum.getText().trim(), ";  ");
			}
			else {
				if (isBlock())			retVal = makeJoinDelim(retVal, "Block hits", ";  ");
				else if (isNotBlock()) 	retVal = makeJoinDelim(retVal, "No Block hits", ";  ");
				
				if (isEveryStarAnno())  retVal = makeJoinDelim(retVal, "Every Gene +minor", ";  "); // must be before next if
				else if (isEveryAnno()) retVal = makeJoinDelim(retVal, "Every Gene", ";  ");
				else if (isOneAnno())	retVal = makeJoinDelim(retVal, "One Gene", ";  ");
				else if (isBothAnno())	retVal = makeJoinDelim(retVal, "Both Gene", ";  "); 
				else if (isNoAnno()) 	retVal = makeJoinDelim(retVal, "No Gene hits", ";  ");
				else if (isOlapAnno_ii()) 	{ // -ii  can do Minor or overlapping
					if (isMinorOlap) retVal = makeJoinDelim(retVal, "Minor genes", ";  "); 
					else             retVal = makeJoinDelim(retVal, "Overlapping genes", ";  ");
				}
				
				int n = isCoSetSzN(false);
				if (n>0) {
					String op = ">=";
					if (radCoSetEQ.isSelected()) op = "=";
					else if (radCoSetLE.isSelected()) op = "<=";
					retVal = makeJoinDelim(retVal, "Collinear size" + op + n, ";  ");
				}
				
				if (isValidHit(txtHitId, "%Id"))  retVal = makeJoinDelim(retVal, "%Id>=" + txtHitId.getText().trim(), ";  ");
				if (isValidHit(txtHitSim, "%Sim"))retVal = makeJoinDelim(retVal, "%Sim>=" + txtHitSim.getText().trim(), ";  ");
				if (isValidHit(txtHitCov, "Cov")) retVal = makeJoinDelim(retVal, "Cov>=" + txtHitCov.getText().trim(), ";  ");
				if (isValidHit(txtGeneOlap, "%Olap")) {
					String or = (isGeneOlapOr()) ? " (Either) " : " (Both) ";
					retVal = makeJoinDelim(retVal, olapDesc + " Olap>=" + txtGeneOlap.getText().trim() + or, ";  ");
				}
				
				if (isMultiN())	{
					String multi = "Multi >=" + getMultiN();
					if (chkMultiMinor.isSelected()) 	 multi += "*";
					
					if (radMultiSame.isSelected()) 		 multi += ", Same Chr";
					else if (radMultiDiff.isSelected())  multi += ", Diff Chr";
					else if (radMultiTandem.isSelected())multi += ", Tandem";
					retVal = makeJoinDelim(retVal, multi, ";  ");
				}
				
				if (isClustN()) {
					String clust = "Cluster >=" + getClustN();
					if (isClPerSp()) clust += " unique genes";
					else             clust += " hits";
					if (nSpecies>2) { 				
						String inc="", exc="";
						int incCnt=0;
						for (int i=0; i<speciesName.length; i++) 
							if (isClInclude(i)) incCnt++;
						if (incCnt==speciesName.length) inc = "All";
						else {
							for (int i=0; i<speciesName.length; i++) {
								if (isClInclude(i)) inc = makeJoinDelim(inc, speciesAbbr[i],  ","); 
								if (isClExclude(i)) exc = makeJoinDelim(exc, speciesAbbr[i],  ",");
							}
							if (!exc.equals("")) clust += ", Exc(" + exc + ")";
						}
						if (!inc.equals("")) {	
							clust += ", Inc(" + inc + ")";
							
							if (isPgeneF) {
								if (isPGIncNoGene())	clust += " No anno";
								else if (isPGincTrans())clust += " Transitive";
								else if (isPGIncOne())	clust += " At least one";
							}
						}
					}
					retVal = makeJoinDelim(retVal, clust, ";  ");
				}
			}
			if (retVal.equals("")) retVal= "Pair Hits"; 
			
			return retVal; 
		}
		// Summary for single and pairs
		private String makeSummaryLoc() {
			int numSpecies = spPanel.getNumSpecies();
			
			String retVal = "";
			String type = isSingle() ? " Gene Loc " : " Hit Loc "; // the locs are checked in DBdata
		
			for(int x=0; x<numSpecies; x++) {
				if (spPanel.isSpIgnore(x)) {
					String msg = "No "+ spPanel.getSpAbbr(x);
					retVal = makeJoinDelim(retVal, msg, ", ");
					continue; 
				}
				String chroms =  spPanel.getSelChrNum(x);
				if (chroms.equals("All")) continue;
					
				int cn = Utilities.getInt(chroms);
				String loc = (cn<0) ? " " + chroms : " Chr" + chroms;           
				
				String start = 	 spPanel.getStartkb(x);
				String end = 	 spPanel.getStopkb(x);
			
				if (!start.equals("") && !end.equals("")) loc += type + start + "-" + end; 
				else if (!start.equals("")) loc += type + start + "-end"; 
				else if (!end.equals(""))   loc += type + "0-" + end;   
				
				retVal = makeJoinDelim(retVal, spPanel.getSpAbbr(x) + loc , ", ");
			}		
			return retVal;
		}
		private static String makeJoinBool(String left, String right, boolean isAND) { // mysql
			if(left.length() == 0) return right;
			if(right.length() == 0) return left;
			if(isAND) return left + " AND " + right;
			return left + " OR " + right;
		}	
		private String makeJoinDelim (String s1, String s2, String delim) { // summary
			if (!s1.equals("") && !s2.equals("")) return s1 + delim + s2;
			else if (!s1.equals("")) return s1;
			else if (!s2.equals("")) return s2;
			else return "";
		}
	} // End SQL class
	 /************************************************************
	  * Rules: chkOn enables some and disables others
	  *******************************************************/
	JPanel buttonPanel, searchPanel, singlePanel, pairRadPanel, pairChkPanel, pairHitPanel, groupPanel;
	
	private JCheckBox  chkAnno = null;  // CAS579c add so can silence other species
	private JTextField txtAnno = null; // Used with all but HitNum
	
	protected SpeciesPanel spPanel = null;	
	protected int nSpecies=0;
	private HashMap <Integer, String> grpCoords = new HashMap <Integer, String> ();
	
	// FilterSingle
	private JCheckBox chkSingle = null; 
	private JRadioButton radOrphan = null, radSingle=null;
		
	// FilterPairRad - block, anno, coset
	private JLabel lblBlkOpts = null;	
	private JRadioButton radBlkYes, radBlkNo, radBlkIgn;
	
	private JLabel lblAnnoOpts = null;
	private JRadioButton radAnnoEvery, radAnnoEveryStar, radAnnoOne, radAnnoBoth,  radAnnoNone, 
			radAnno_ii, radAnnoIgn;	// -ii only 
	
	private JTextField txtCoSetN = null; 
	private JLabel     lblCoSetN = null;
	private JRadioButton radCoSetGE, radCoSetEQ, radCoSetLE, radCoSetIgn;
	
	// FilterPair hit attributes
	private JLabel lblHitGE, lblHitId, lblHitSim, lblHitCov, lblGeneOlap;
	private JTextField txtHitId = null, txtHitSim = null, txtHitCov = null, txtGeneOlap = null;
	private JRadioButton radOlapOr = null, radOlapAnd = null;
		
	// FilterPairChk
	private JLabel lblChkExact;
	private JCheckBox  chkBlkNum = null, chkCoSetNum = null, chkHitNum = null, chkGeneNum = null;
	private JTextField txtBlkNum = null, txtCoSetNum = null, txtHitNum = null, txtGeneNum = null;
	
	// FilterGroup
	private JLabel lblGrpTip;
	private JCheckBox chkMultiN = null;
	private JTextField txtMultiN = null;
	private JLabel     lblMultiN = null, lblMultiOpp = null;
	private JRadioButton radMultiSame=null, radMultiTandem=null, radMultiDiff=null;
	private JCheckBox  chkMultiMinor = null;
	
	private JCheckBox chkClustN = null;
	private JTextField txtClustN = null; 
	private JLabel     lblClustN = null;
	private JRadioButton radClPerSp = null, radClHit;
	private JCheckBox [] incSpecies = null, exSpecies = null;
	private String [] speciesName = null;
	private String [] speciesAbbr = null;
	private JRadioButton radIncOne = null, radIncNoGene = null, radIncTrans = null, radIncIgn = null;
	private JLabel lblInclude = null, lblExclude = null, lblIncFilters = null;
	
	private JButton btnExecute = null;
	
	// Not big enough to bother adding buttons to pnlStepOne.expand(); pnlStepOne.collapse()
	private CollapsiblePanel pnlStep1 = null, pnlStep2 = null, pnlStep3 = null, pnlStep4 = null; 
	private QueryFrame qFrame = null;
}
