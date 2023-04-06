package symapQuery;

/***************************************************************
 * The Filter panel. 
 * CAS504 massive changes to queries
 */
import java.awt.Color;
import java.awt.Component;
import java.awt.ItemSelectable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collections;
import java.util.Iterator;
import java.util.Vector;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import symap.manager.Mproject;
import util.ErrorReport;
import util.Jhtml;
import util.Utilities;

public class QueryPanel extends JPanel {
	private static final long serialVersionUID = 2804096298674364175L;
	private boolean AND=true;

	public QueryPanel(SyMAPQueryFrame parentFrame) {
		theParentFrame = parentFrame;
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(Color.WHITE);

		createQueryPanel();
	}
	public  boolean isEitherAnno() 	{ return annoOptRow.isEnabled() && annoOptRow.isOne(); }
	public  boolean isOneAnno() 	{ return annoOptRow.isEnabled() && annoOptRow.isTwo(); }
	public  boolean isBothAnno() 	{ return annoOptRow.isEnabled() && annoOptRow.isThree(); }
	private boolean isNoAnno() 		{ return annoOptRow.isEnabled() && annoOptRow.isFour(); }
	
	private boolean isBlock() 	{ return blockOptRow.isEnabled() && blockOptRow.isOne(); }
	private boolean isNotBlock(){ return blockOptRow.isEnabled() && blockOptRow.isTwo(); }
	
	private boolean isAnnoTxt()	{ return txtAnno.isEnabled() && txtAnno.getText().trim().length()>0;}
	
	private boolean isBlockNum(){ // CAS514 was not checking for selected
		return chkBlock.isEnabled() && chkBlock.isSelected() && txtBlock.getText().trim().length()>0;}
	private boolean isCollinearSet(){ // CAS514 was not checking for selected
		return chkCollinearSet.isEnabled() && chkCollinearSet.isSelected() && txtCollinearSet.getText().trim().length()>0;}
	private boolean isHitIdx()	{ 
		return chkHitIdx.isEnabled() && chkHitIdx.isSelected() && txtHitIdx.getText().trim().length()>0;}
	private boolean isGeneNum()	{  
		return chkGeneNum.isEnabled() && chkGeneNum.isSelected() && txtGeneNum.getText().trim().length()>0;}
	
	// these are only good for the last query, so should only be used for building the tables
	public boolean isSingle() 		{ return chkSingle.isEnabled() && chkSingle.isSelected(); }
	public boolean isOrphan()		{ return chkSingle.isEnabled() && cmbSingleOpt.getSelectedIndex()==0;}
	
	public boolean isPgeneF()    	{ return chkPgeneF.isEnabled() 		&& chkPgeneF.isSelected(); }
	public boolean isLinkageInc() 	{ return chkLinkageInc.isEnabled() 	&& chkLinkageInc.isSelected(); }
	public boolean isUnannotInc() 	{ return chkUnannotInc.isEnabled() 	&& chkUnannotInc.isSelected(); }
	public boolean isOnlyInc()    	{ return chkOnlyInc.isEnabled() 	&& chkOnlyInc.isSelected(); }
	public boolean isInclude(int sp) {return incSpecies[sp].isEnabled() && incSpecies[sp].isSelected(); }
	public boolean isExclude(int sp) {return exSpecies[sp].isEnabled() 	&& exSpecies[sp].isSelected();}
	
	public SpeciesSelectPanel getSpeciesPanel() {return speciesPanel;}
	public  HashMap <Integer, String> getGrpCoords() { return grpCoords;}
	/****************************************************
	 * Make query
	 **************************************************/
	public String makeSQLquery() {
		grpCoords.clear(); // CAS514 in case makeSpChrWhere is not called
		
		bValidQuery=true;
		boolean bSingle = isSingle();
		
		FieldData theFields = FieldData.getFields();
   	 
		String sql;
		if (bSingle) sql = "SELECT " + theFields.getDBFieldList(bSingle) + makeSingleSQL();
		else         sql = "SELECT " + theFields.getDBFieldList(bSingle) + makeHitSQL(theFields.getJoins());

		return sql;
	}
	/****************************************************
	 *  CAS513 previous was not working right - rewrote some things
	 *  CAS514 add pairs to orphan queries e.g. getPairWhere() = PH.pair_idx IN (1,2,3)
	 *  	   without the pairs, it will select all orphans in DB
	 *  	   Add the Genes Only - this is all genes regardless of pairs
	 */
	private String makeSingleSQL() {
		boolean bOrphan = isOrphan();
		
		String from = " FROM pseudo_annot AS " + Q.PA;
		
		String whereClause;
		if (bOrphan) whereClause =
			" WHERE not exists " +
			"\n(SELECT * FROM pseudo_hits_annot AS PHA " +
			"\nLEFT JOIN pseudo_hits as PH on PH.idx=PHA.hit_idx " + // join to restrict pairs
			"\nWHERE PA.idx=PHA.annot_idx and " + speciesPanel.getPairWhere() + ") and";
		else 
			whereClause=
			"\nLEFT JOIN pseudo_hits_annot as PHA on PHA.annot_idx=PA.idx " +
			"\nWHERE ";
			
		whereClause += " PA.type='gene'"; 
		
	// Annotation
		String text = txtAnno.getText();
		String anno = (text.length() == 0) ? "" : "PA.name LIKE '%" + text + "%'";
		whereClause = joinBool(whereClause, anno, AND);
	
	// Selected project -
		String locSQL = makeSpChrWhere(); 
		if (!locSQL.contentEquals("")) {
			whereClause = joinBool(whereClause, locSQL, AND); // Specifies grp_idx
		}
		else { // create list of all grp_idx from valid projects
			String selSingSp = (String) cmbSingleSpecies.getSelectedItem();
			boolean bAll = (selSingSp.contentEquals("All")); // specified project takes precedence
			
			String chrWhere="";
			for(int x=0; x<nSpecies; x++) {
				String species =  speciesPanel.getSpName(x);
				if (bAll || selSingSp.equals(species))  {
					chrWhere = joinStr(chrWhere, speciesPanel.getChrIdxStr(x), ","); // list of grp_idx for project
				}
			}	
			String clause = "PA.grp_idx in (" + chrWhere + ")";
			whereClause = joinBool(whereClause, clause, AND);
		}
		if (!bOrphan) whereClause += " order by PA.idx";
		
		return from + whereClause;
	}
	/*********************************************************
	 * Hit SELECT " + getDBFieldList() + " FROM pseudo_hits AS " + Q.PH + whereClause
	 * isOneAnno requires postprocessing
	 * isTwoAnno needs postprocessing only because there are a few cases where (PH.annot1_idx>0 and PH.annot2_idx>0)
	 *    is not true but it really  is true. It needs to be fixed in AnchorMain, which will take some work....
	 */
	private String makeHitSQL(String join) {
		String whereClause= " where " + speciesPanel.getPairWhere(); // PH.pair_idx in (...)
		/**
		if (isHitIdx()) { // if hit, no other filters
			whereClause = joinBool(whereClause, "PH.idx=" + txtHitIdx.getText(), AND);
			return " FROM pseudo_hits AS " + Q.PH + join + " " + whereClause;
		}
		**/
		whereClause = joinBool(whereClause, makeAnnoWhere(), AND);
		
		// Chr, loc
		whereClause = joinBool(whereClause, makeSpChrWhere(), AND);
		
		if (isHitIdx()) { // CAS520 changed from idx to hitnum, which needs chrs
			whereClause = joinBool(whereClause, makeChkHitWhere(), AND);
		}
		else if (isGeneNum()) { // CAS520 changed from idx to hitnum, which needs chrs
			whereClause = joinBool(whereClause, makeChkGeneNumWhere(whereClause), AND);
		}
		else if (isBlockNum()) { // if block, only anno filter and chromosome (CAS513 add chromosome)
			whereClause = joinBool(whereClause, makeChkBlockWhere(), AND);
		}
		else if (isCollinearSet()) { // if collinear, only anno filter and chromosome (CAS513 add chromosome)
			whereClause = joinBool(whereClause, makeChkCollinearWhere(), AND);
		}
		else {// isSynteny, isAnno, isColinear; isOneAnno, isTwoAnno use post-processing
			if (isBlock())         	whereClause = joinBool(whereClause, "PBH.block_idx is not null", AND);
			else if (isNotBlock()) 	whereClause = joinBool(whereClause, "PBH.block_idx is null", AND);
			
			if (isNoAnno())	  // either and both occur in the DBdata filters
				whereClause = joinBool(whereClause, 
					"(PH.annot1_idx=0 and PH.annot2_idx=0) AND PA.idx is null", AND); // 'is null' is necessary. 
			
			int n = isCollinearSize(true);
			String op = "PH.runsize>=" + n;
			if (coEQ.isSelected()) op = "PH.runsize=" + n;
			else if (coLE.isSelected()) op = "PH.runsize>0 and PH.runsize<=" + n;
			if (n>0) whereClause = joinBool(whereClause, op, AND); // CAS517 was >
		}
		return " FROM pseudo_hits AS " + Q.PH + join + " " + whereClause + "  ORDER BY PH.hitnum  asc"; // CAS520 was PH.idx
	}	
	/****************************************
	 * If this extra select is not done, then ONLY rows with PA.name with zinc are returned
	 * and there is no annotation for the other side of the hit
	 */
	private String makeAnnoWhere() {
		if (!isAnnoTxt()) return "";
		
		String clause = "PA.name LIKE '%" + txtAnno.getText() + "%'";
		
		return  "PH.idx in (SELECT  PH.idx " +
				"FROM      pseudo_hits       AS PH " +
				"LEFT JOIN pseudo_hits_annot AS PHA ON PH.idx = PHA.hit_idx " +
				"LEFT JOIN pseudo_annot      AS PA  ON PHA.annot_idx = PA.idx " +
				"WHERE " + clause + ")";
	}
	/******************************************************** 
	 * CAS504 added block search; CAS513 didn't always work - changed to just looking for block=N
	 * Convert block string to where statement 
	 *****/
	private String makeChkBlockWhere() {
		String block = txtBlock.getText().trim();
		if (block.equals("")) return "";
		
		try {
			int n = Integer.parseInt(block);
			return "B.blocknum=" + n;
		}
		catch (Exception e) {
			Utilities.showWarningMessage(
				"Invalid block number (" + block + "). Should be single number.\n"
			+ " Set chromosomes to narrow it down to a block, e.g. Chr1.Chr2.B where B=block"); // CAS512 add popup warning
			txtBlock.setText("");
			bValidQuery=false;
			return "";
		}
	}
	private String makeChkCollinearWhere() {
		String run = txtCollinearSet.getText().trim();
		if (run.equals("")) return "";
		
		try {
			int n = Integer.parseInt(run);
			return "ph.runnum=" + n; // CAS520 from size to num
		}
		catch (Exception e) {
			Utilities.showWarningMessage(
				"Invalid collinear set (" + run + "). Should be single number.\n"
			+ "e.g. Collinear 2.3.100.3 has set number 100.\n"
			+ "     A set number can occur for every pair, so set the chromosomes to narrow down to a single set."); 
			txtCollinearSet.setText("");
			bValidQuery=false;
			return "";
		}
	}
	// this only return one occurrence
	private String makeChkHitWhere() {// CAS520 from idx to hitnum
		String run = txtHitIdx.getText().trim();
		if (run.equals("")) return "";
		
		try {
			int n = Integer.parseInt(run);
			return "PH.hitnum=" + n; 
		}
		catch (Exception e) {
			Utilities.showWarningMessage(
				"Invalid hit# (" + run + "). Should be single number.\n");
			txtHitIdx.setText("");
			bValidQuery=false;
			return "";
		}
	}
	// this only return one half of hit, then one with genenum
	private String makeChkGeneNumWhere(String whereClause) {// CAS520 from idx to hitnum
		String gnStr = txtGeneNum.getText().trim();
		if (gnStr.equals("")) return "";
		int n;
		try {
			n = Integer.parseInt(gnStr);	
			return " PA.genenum=" + n;
		}
		catch (Exception e) {
			Utilities.showWarningMessage(
				"The Gene# entered is 'gnStr', which is not a number. \nThe input must be a number with no suffix.\n");
			txtGeneNum.setText("");
			bValidQuery=false;
			return "";
		}
	}
	/************************************
	 * The chromosomes are queried in MySQL and the locations in DBdata
	 * Works for both orphan and hit
	 */
	private String makeSpChrWhere() {
		String grpList="", where="";
		grpCoords.clear();
		
		// First check to see if any chromosomes are selected
		boolean isSet=false;
		for (int p=0; p<nSpecies; p++) {
			int index = speciesPanel.getSelChrIdx(p); 
			if(index >0) isSet=true;
		}
		if (!isSet) return "";
		
		for(int p=0; p<nSpecies; p++) {
			int index = speciesPanel.getSelChrIdx(p); // returns a PH.grp1_idx; CAS518 returns -1 if disabled
			if(index <= 0) continue;
			
			if (grpList.equals("")) grpList = index+"";
			else 					grpList += "," + index;
			
			String start = speciesPanel.getChrStart(p);
			String stop =  speciesPanel.getChrStop(p);
			if (start==null || stop==null) {
				bValidQuery=false;
				return "";
			}
			if (start.equals("")) start = "0";
			if (stop.equals(""))  stop  = "0";
			if (start!="0" || stop!="0")
				grpCoords.put(index, start + Q.delim2 + stop); // for DBdata.passFilter
		}
		if (grpList.equals("")) return "";
		
		if (isSingle()) { // CAS513 add orphan check
			if (grpList.contains(",")) 	where  = "(PA.grp_idx IN (" + grpList + ")) ";
			else 						where  = "(PA.grp_idx = " + grpList + ") ";
		}
		else {
			if (grpList.contains(",")) {
				where  = "( (PH.grp1_idx IN (" + grpList + ")) ";
				where += " AND ";
				where += "  (PH.grp2_idx IN (" + grpList + ")) )";
			}
			else {
				where  = "( (PH.grp1_idx = " + grpList + ") ";
				where += " OR ";
				where += "  (PH.grp2_idx = " + grpList + ") )";
			}
		}
		return where; 
	}

	private static String joinBool(String left, String right, boolean isAND) { // mysql
		if(left.length() == 0) return right;
		if(right.length() == 0) return left;
		if(isAND) return left + " AND " + right;
		return left + " OR " + right;
	}	
	/*******************************************************
	 * Summary  CAS503 made this a complete list of filters
	 * CAS514 reorder again
	 */
	public String makeSummary() { 
		int numSpecies = speciesPanel.getNumSpecies();
		if(numSpecies ==0) return "No species"; // not possible?
		
		// anno and loc used for all the rest; but single only uses with All
		String anno = txtAnno.getText();
		String loc =  makeSummaryLocation(); // chr must be set to return location
		String retVal="";
		
		if(isSingle()) {
			retVal = (isOrphan()) ? "Orphan genes" : "All genes";
			retVal = joinStr(retVal, anno, ";  ");
			
			String selSingSp = (String) cmbSingleSpecies.getSelectedItem();
			if (loc!="") retVal = joinStr(retVal, loc, ";  ");
			else retVal = joinStr(retVal, selSingSp, ";  "); 
				
			return retVal;
		}
		if (isHitIdx()) {
			retVal = joinStr(retVal,  "Hit#="+txtHitIdx.getText().trim() + " (may hit overlapped genes)", ";  ");
		}
		else if (isBlockNum()) {
			retVal = joinStr(retVal, "Block="+txtBlock.getText().trim(), ";  ");
		}
		else if (isCollinearSet()) {
			retVal = joinStr(retVal, "Collinear set "+txtCollinearSet.getText().trim(), ";  ");
		}
		else if (isGeneNum()) {
			retVal = joinStr(retVal, "Gene# "+txtGeneNum.getText().trim() + " (one side of hit only)", ";  ");
		}
		else {
			if (isBlock())			retVal = joinStr(retVal, "Block hits", ";  ");
			else if (isNotBlock()) 	retVal = joinStr(retVal, "No Block hits", ";  ");
			
			if (isEitherAnno())		retVal = joinStr(retVal, "Either Gene hit", ";  ");
			else if (isOneAnno())	retVal = joinStr(retVal, "One Gene hit", ";  ");
			else if (isBothAnno())	retVal = joinStr(retVal, "Both Gene hits", ";  ");
			else if (isNoAnno()) 	retVal = joinStr(retVal, "No Gene hits", ";  ");
			
			int n=isCollinearSize(false);
			if (n>0) {
				String op = ">=";
				if (coEQ.isSelected()) op = "=";
				else if (coLE.isSelected()) op = "<=";
				retVal = joinStr(retVal, "Collinear size " + op + n, ";  ");
			}
			
			if (isPgeneF()) {
				retVal = joinStr(retVal, "Run PgeneF", ";  ");
				String inc="", exc="";
				for (int i=0; i<speciesName.length; i++) {
					if (isInclude(i)) inc = joinStr(inc, speciesName[i],  ",");
					if (isExclude(i)) exc = joinStr(exc, speciesName[i],  ",");
				}
				if (!inc.equals("")) inc = "Inc (" + inc + ")";
				if (!exc.equals("")) exc = "Exc (" + exc + ")";
				retVal = joinStr(retVal, joinStr(inc, exc, ", "), ";  ");
				
				if(isUnannotInc())	retVal = joinStr(retVal, "Not annotated", ";  ");
				if(isLinkageInc())	retVal = joinStr(retVal, "Linkage", ";  ");
				if(isOnlyInc())		retVal = joinStr(retVal, "Only included", ";  ");
			}
		}
		if (retVal.equals("")) retVal="Pair hits"; 
		retVal = joinStr(retVal, anno, ";  ");
		retVal = joinStr(retVal, loc, ";  "); 
	
		return retVal; 
	}
	
	// CAS504 add Include 
	private String makeSummaryLocation() {
		int numSpecies = speciesPanel.getNumSpecies();

		String retVal = ""; // CAS503 added info and remove some wordiness, i.e. do not need to list species unless location

		for(int x=0; x<numSpecies; x++) {
			if (!speciesPanel.isSpEnabled(x)) continue; // CAS518 can be disabled by Single
			
			String species = speciesPanel.getSpName(x);
			String chroms =  speciesPanel.getSelChrNum(x);
			String start = 	 speciesPanel.getStartAbbr(x);
			String end = 	 speciesPanel.getStopAbbr(x);
			if(!chroms.equals("All")) {
				String loc = " Chr " + chroms;
				if (!start.equals("") && !end.equals("")) loc += " Range " + start + "-" + end; 
				else if (!start.equals("")) loc += " Start " + start; 
				else if (!end.equals(""))   loc += " Range 0-" + end; // CAS513 was 1
				retVal = joinStr(retVal, species + loc , ", ");
			}
		}		
		return retVal;
	}
	
	private String joinStr (String s1, String s2, String delim) { // summary
		if (!s1.equals("") && !s2.equals("")) return s1 + delim + s2;
		else if (!s1.equals("")) return s1;
		else if (!s2.equals("")) return s2;
		else return "";
	}
	private int isCollinearSize(boolean prt) 	{ // CAS512 add warning the first time its called
		if (!txtCollinearN.isEnabled()) return 0;
		String ntext = txtCollinearN.getText();
		if (ntext.length()==0) return 0;
		try {
			int n = Integer.parseInt(ntext);
			if (n<0) {
				if (prt) 
					Utilities.showWarningMessage("Invalid Colliner size (" + ntext + "), must be positive."
							+ "\ne.g. Collinear 2.3.100.3 - the size is 3.");
				return 0;
			}
			return n;
		} catch (Exception e) { 
			if (prt) Utilities.showWarningMessage("Invalid Colliner integer ("+ ntext + ")" );
			bValidQuery=false;
		}; 
		return 0;
	}
	
	/********************************************************************
	 * XXX Filter panels
	 ***************************************************************/
	private void createQueryPanel() {
		speciesPanel = new SpeciesSelectPanel(theParentFrame);
		nSpecies = speciesPanel.getNumSpecies();			// used in orphanPanel
		
		JPanel buttonPanel = createButtonPanel();
		JPanel searchPanel = createFilterAnnoPanel();
		JPanel orphanPanel = createFilterSinglePanel();
		JPanel blockPanel  = createFilterCheckPanel();
		JPanel basicPanel  = createFilterOptionsPanel();
		JPanel pgenePanel  = createFilterPgeneFPanel();
	 
		add(buttonPanel);								add(Box.createVerticalStrut(10));
		
		pnlStep1 = new CollapsiblePanel("1. General", ""); 
		pnlStep1.add(searchPanel);		
		pnlStep1.add(speciesPanel);	
		pnlStep1.collapse();
		pnlStep1.expand();
		add(pnlStep1);	
		
		pnlStep2 = new CollapsiblePanel("2. Single genes", "");
		pnlStep2.add(orphanPanel);			
		pnlStep2.collapse();
		pnlStep2.expand();
		add(pnlStep2);								
		
		pnlStep3 = new CollapsiblePanel("3. Pair hits", ""); 
		pnlStep3.add(basicPanel);			pnlStep3.add(Box.createVerticalStrut(5));
		pnlStep3.add(blockPanel);				
		pnlStep3.collapse();
		pnlStep3.expand();
		add(pnlStep3);								
		
		pnlStep4 = new CollapsiblePanel("4. Putative gene families (PgeneFs)", "");
		pnlStep4.add(pgenePanel);
		pnlStep4.collapse();
		pnlStep4.expand();
		add(pnlStep4);								
	}
	private JPanel createButtonPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBackground(Color.WHITE);
		
		btnExecute = new JButton("Run Search"); 
		btnExecute.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnExecute.setBackground(Color.WHITE);
		btnExecute.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String query = makeSQLquery();
				String sum = makeSummary();
				if (bValidQuery)
					theParentFrame.makeTable(query, sum, isSingle());
			}
		});
		JButton btnClear = new JButton("Clear Filters");
		btnClear.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnClear.setBackground(Color.white);
		
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setClearFilters();
			}
		});
		JButton btnHelp = Jhtml.createHelpIconQuery(Jhtml.query);
		
		panel.add(btnClear);		panel.add(Box.createHorizontalStrut(170));
		panel.add(btnExecute);		panel.add(Box.createHorizontalStrut(220));
		panel.add(btnHelp);
		
		panel.setMaximumSize(panel.getPreferredSize());
		return panel;
	}
	
	/** Filter Hits **/
	private JPanel createFilterAnnoPanel() {
		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.PAGE_AXIS));
		page.setBackground(Color.WHITE);
		
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
		row.setBackground(Color.WHITE);

		JLabel aLabel = new JLabel("Annotation Description");
		txtAnno = new JTextField(30);
		row.add(aLabel); row.add(Box.createHorizontalStrut(10));
		row.add(txtAnno); 
		row.setMaximumSize(row.getPreferredSize());
		row.setAlignmentX(LEFT_ALIGNMENT);
		page.add(row); 
		
		return page;
	}	
	private JPanel createFilterOptionsPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBackground(Color.WHITE);
		
		blockOptRow = new OptionsRow("In Block (Synteny hit)", "Yes", "No", "", "", "All", 5, 150);
		blockOptRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(blockOptRow); panel.add(Box.createVerticalStrut(5));
		
		annoOptRow = new OptionsRow("Annotated (Gene hit)", "Either", "One", "Both", "None", "All", 5, 150);
		annoOptRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(annoOptRow); panel.add(Box.createVerticalStrut(5));
		
		JPanel crow = new JPanel();
		crow.setLayout(new BoxLayout(crow, BoxLayout.LINE_AXIS));
		crow.setAlignmentX(Component.LEFT_ALIGNMENT);
		crow.setBackground(Color.WHITE);
		
		lblCollinearN = new JLabel("Collinear size ");
		crow.add(lblCollinearN); crow.add(Box.createHorizontalStrut(3));
		
		txtCollinearN = new JTextField(3); txtCollinearN.setBackground(Color.WHITE);
		txtCollinearN.setText("0"); txtCollinearN.setMaximumSize(txtCollinearN.getPreferredSize());
		crow.add(txtCollinearN); crow.add(Box.createHorizontalStrut(15));
		
		coGE = new JRadioButton(">="); coGE.setBackground(Color.WHITE);
		coEQ = new JRadioButton("=");  coEQ.setBackground(Color.WHITE);
		coLE = new JRadioButton("<="); coLE.setBackground(Color.WHITE);
		ButtonGroup g = new ButtonGroup();
		g.add(coGE); g.add(coEQ); g.add(coLE);
		coGE.setSelected(true);
		crow.add(coGE); crow.add(Box.createHorizontalStrut(1));
		crow.add(coEQ); crow.add(Box.createHorizontalStrut(1));
		crow.add(coLE); crow.add(Box.createHorizontalStrut(1));
		
		panel.add(crow); 
		
		return panel;
	}
	/** Block and Hit **/
	private JPanel createFilterCheckPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBackground(Color.WHITE);
		
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
		row.setBackground(Color.WHITE);
		
		chkBlock = new JCheckBox("Block#");
		chkBlock.setBackground(Color.WHITE);
		chkBlock.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				boolean blkSel = chkBlock.isSelected();
				setAllEnabled(!blkSel);
				if (blkSel) {
					chkBlock.setEnabled(true);
					txtBlock.setEnabled(true);
					speciesPanel.setEnabled(true); 
				}
			}
		});
		txtBlock = new JTextField(4); txtBlock.setEnabled(false);
		row.add(chkBlock); row.add(Box.createHorizontalStrut(5));
		row.add(txtBlock); row.add(Box.createHorizontalStrut(15));
		
		chkCollinearSet = new JCheckBox("Collinear set#"); // CAS520 change to set
		chkCollinearSet.setBackground(Color.WHITE);
		chkCollinearSet.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				boolean coSel = chkCollinearSet.isSelected();
				setAllEnabled(!coSel);
				if (coSel) {
					chkCollinearSet.setEnabled(true);
					txtCollinearSet.setEnabled(true);
					speciesPanel.setEnabled(true); 
				}
			}
		});
		txtCollinearSet = new JTextField(4); txtCollinearSet.setEnabled(false);
		row.add(chkCollinearSet); row.add(Box.createHorizontalStrut(5));
		row.add(txtCollinearSet); row.add(Box.createHorizontalStrut(15));
		
		chkHitIdx = new JCheckBox("Hit#");
		chkHitIdx.setBackground(Color.WHITE);
		chkHitIdx.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				boolean hitSel = chkHitIdx.isSelected();
				setAllEnabled(!hitSel); // turn all off
				if (hitSel) {
					chkHitIdx.setEnabled(true);
					txtHitIdx.setEnabled(true);
					speciesPanel.setEnabled(true);
				}
			}
		});
		txtHitIdx = new JTextField(4); txtHitIdx.setEnabled(false);
		row.add(chkHitIdx); row.add(Box.createHorizontalStrut(5));
		row.add(txtHitIdx); row.add(Box.createHorizontalStrut(15));
		
		chkGeneNum = new JCheckBox("Gene#");
		chkGeneNum.setBackground(Color.WHITE);
		chkGeneNum.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				boolean bSel = chkGeneNum.isSelected();
				setAllEnabled(!bSel); // turn all off
				if (bSel) {
					chkGeneNum.setEnabled(true);
					txtGeneNum.setEnabled(true);
					speciesPanel.setEnabled(false);
				}
			}
		});
		txtGeneNum = new JTextField(4); txtGeneNum.setEnabled(false);
		row.add(chkGeneNum); row.add(Box.createHorizontalStrut(5));
		row.add(txtGeneNum); 	
		
		row.setMaximumSize(row.getPreferredSize());
		row.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(row);
		
		return panel;
	}
	/** Orphan **/
	private JPanel createFilterSinglePanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBackground(Color.WHITE);
		
		// orphan row
		JPanel	row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBackground(Color.WHITE);
		
		chkSingle = new JCheckBox("Single");	
		chkSingle.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkSingle.setBackground(Color.WHITE);
		chkSingle.addActionListener(new ActionListener() { // CAS503 disabled others if orphans checked
			public void actionPerformed(ActionEvent e) {
				boolean isSelect = chkSingle.isSelected();
				setAllEnabled(!isSelect);
				
				if (isSelect) {
					String selSp = (String) cmbSingleSpecies.getSelectedItem();
					boolean bAll = (selSp.contentEquals("All")); // specified project takes precedence
					speciesPanel.setEnabled(false);
					if (!bAll) speciesPanel.setSpEnabled(selSp); // CAS517 turn on if species selected
					chkSingle.setEnabled(true);
					cmbSingleSpecies.setEnabled(true);
					cmbSingleOpt.setEnabled(true);
				}
			}
		});
		chkSingle.setSelected(false);
		row.add(chkSingle); row.add(Box.createHorizontalStrut(5));
		
		cmbSingleOpt = new JComboBox <String> (); // CAS514 add for all genes
		cmbSingleOpt.setBackground(Color.WHITE);
		cmbSingleOpt.addItem("Orphan genes (no hits)");
		cmbSingleOpt.addItem("All genes (w/o hits)");
		cmbSingleOpt.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {}
		});
		cmbSingleOpt.setEnabled(false);
		row.add(cmbSingleOpt); row.add(Box.createHorizontalStrut(5));
		
		cmbSingleSpecies = new JComboBox <String> (); // CAS513 add type
		cmbSingleSpecies.setBackground(Color.WHITE);
		cmbSingleSpecies.addItem("All");
		
		for(int x=0; x<nSpecies; x++)
			cmbSingleSpecies.addItem(speciesPanel.getSpName(x));
		cmbSingleSpecies.setMaximumSize(cmbSingleSpecies.getPreferredSize());
		
		cmbSingleSpecies.addActionListener(new ActionListener() { // CAS503 disabled chr
			public void actionPerformed(ActionEvent e) {
				speciesPanel.setEnabled(false); 
				String selSp = (String) cmbSingleSpecies.getSelectedItem();
				boolean bAll = (selSp.contentEquals("All")); // specified project takes precedence
				if (!bAll) speciesPanel.setSpEnabled(selSp); // CAS518 was confusing, allow Chr selection after species
			}
		});
		cmbSingleSpecies.setEnabled(false);
		
		row.add(new JLabel("Project: ")); row.add(Box.createHorizontalStrut(1));
		row.add(cmbSingleSpecies); 
		
		row.setMaximumSize(row.getPreferredSize());
		row.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(row); 
		
		return panel;
	}
	/** Filter Punitive gene family **/
	private JPanel createFilterPgeneFPanel() {		
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBackground(Color.WHITE);

		Vector<Mproject> theProjects = theParentFrame.getProjects();
		
		chkPgeneF = new JCheckBox("Compute PgeneF");	
		chkPgeneF.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkPgeneF.setBackground(Color.WHITE);
		chkPgeneF.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setPgenefEnable(chkPgeneF.isSelected());	
			}
		});
		chkPgeneF.setSelected(false);
		panel.add(chkPgeneF);
		
		speciesName = new String [theProjects.size()];
		incSpecies = new JCheckBox[theProjects.size()];
		for(int x=0; x<incSpecies.length; x++) {
			speciesName[x] = theProjects.get(x).getDisplayName();
			incSpecies[x] = new JCheckBox(speciesName[x]);
			incSpecies[x].setBackground(Color.WHITE);
			incSpecies[x].setEnabled(false);
		}
		exSpecies = new JCheckBox[theProjects.size()];
		for(int x=0; x<exSpecies.length; x++) {
			exSpecies[x] = new JCheckBox(speciesName[x]);
			exSpecies[x].setBackground(Color.WHITE);
			exSpecies[x].setEnabled(false);
		}		
		
		Vector<String> sortedGroups = new Vector<String> ();
		for(int x=0; x<theProjects.size(); x++) {
			if(!sortedGroups.contains(theProjects.get(x).getdbCat())) 
				sortedGroups.add(theProjects.get(x).getdbCat());
		}
		Collections.sort(sortedGroups);
		
		for(int grpIdx=0; grpIdx<sortedGroups.size(); grpIdx++) {
			String catName = sortedGroups.get(grpIdx);
			boolean firstOne = true;

			JPanel row = new JPanel();
			row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
			row.setBackground(Color.WHITE);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			row.add(new JLabel("Include: "));
			for(int x=0; x<incSpecies.length; x++) {
				if(catName.equals(theProjects.get(x).getdbCat())) {
					if(firstOne) {
						panel.add(Box.createVerticalStrut(10));
						if(sortedGroups.size() > 1)
							panel.add(new JLabel(catName.toUpperCase()));
						firstOne = false;
					}
					row.add(incSpecies[x]);
					row.add(Box.createHorizontalStrut(5));
				}
			}
			panel.add(row);
	
			row = new JPanel();
			row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
			row.setBackground(Color.WHITE);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
	
			row.add(new JLabel("Exclude: "));
			for(int x=0; x<exSpecies.length; x++) {
				if(catName.equals(theProjects.get(x).getdbCat())) {
					if(firstOne) {
						panel.add(Box.createVerticalStrut(10));
						if(sortedGroups.size() > 1)
							panel.add(new JLabel(catName.toUpperCase()));
						firstOne = false;
					}
					row.add(exSpecies[x]);
					row.add(Box.createHorizontalStrut(5));
				}
			}
			if (nSpecies>2) panel.add(row);
		}
		
		chkUnannotInc = new JCheckBox("No annotation to included species");	  chkUnannotInc.setEnabled(false);
		chkLinkageInc = new JCheckBox("Complete linkage of included species"); chkLinkageInc.setEnabled(false);
		chkOnlyInc = 	new JCheckBox("At least one hit for each included species"); chkOnlyInc.setEnabled(false);
		chkUnannotInc.setAlignmentX(Component.LEFT_ALIGNMENT); chkUnannotInc.setBackground(Color.WHITE);
		chkLinkageInc.setAlignmentX(Component.LEFT_ALIGNMENT); chkLinkageInc.setBackground(Color.WHITE);
		chkOnlyInc.setAlignmentX(Component.LEFT_ALIGNMENT); chkOnlyInc.setBackground(Color.WHITE);
		
		panel.add(Box.createVerticalStrut(10));
		panel.add(new JLabel("PgeneF must have: "));
		panel.add(Box.createVerticalStrut(2));
		panel.add(chkUnannotInc); panel.add(Box.createVerticalStrut(2));
		if (nSpecies>2) {
			panel.add(chkLinkageInc);panel.add(Box.createVerticalStrut(2));
			panel.add(chkOnlyInc);
		}
		panel.setMaximumSize(panel.getPreferredSize());
	
		return panel;
	}
	// CAS513 the enables and clears needs a bit of rewiring
	private void setClearFilters() {
		setAllEnabled(true);
		
		txtAnno.setText("");
		blockOptRow.setValue(5); // options
		annoOptRow.setValue(5);  
		
		txtCollinearN.setText("");
		
		txtBlock.setText(""); 			txtBlock.setEnabled(false);	chkBlock.setSelected(false);
		txtCollinearSet.setText(""); 	txtCollinearSet.setEnabled(false);	chkCollinearSet.setSelected(false);
		coGE.setSelected(true);
		txtHitIdx.setText(""); 			txtHitIdx.setEnabled(false);   chkHitIdx.setSelected(false);
		txtGeneNum.setText(""); 		txtGeneNum.setEnabled(false);  chkGeneNum.setSelected(false);
		
		chkSingle.setSelected(false);
		cmbSingleOpt.setSelectedIndex(0);     cmbSingleOpt.setEnabled(false);
		cmbSingleSpecies.setSelectedIndex(0); cmbSingleSpecies.setEnabled(false); 
		
		chkPgeneF.setSelected(false); setPgenefEnable(false);
		
		speciesPanel.clear();
		speciesPanel.setEnabled(true); // CAS521 was leaving it disabled
	}
	
	private void setAllEnabled(boolean b) { // everything
		txtAnno.setEnabled(true); // all use except HitIdx, which disables
		
		blockOptRow.setEnabled(b); 
		annoOptRow.setEnabled(b); 
	
		lblCollinearN.setEnabled(b); txtCollinearN.setEnabled(b); coGE.setEnabled(b);coEQ.setEnabled(b);coLE.setEnabled(b);
		
		chkBlock.setEnabled(b);  		txtBlock.setEnabled(false); 		// only chkBlock enables
		chkCollinearSet.setEnabled(b);  txtCollinearSet.setEnabled(false); 	// only chkColinear enables
		chkHitIdx.setEnabled(b); 		txtHitIdx.setEnabled(false);		// only chkHitIdx enables
		chkGeneNum.setEnabled(b); 		txtGeneNum.setEnabled(false);		// only chkHitIdx enables
	
		speciesPanel.setEnabled(b); // All chr, start, end
		
		chkSingle.setEnabled(b); cmbSingleSpecies.setEnabled(false); cmbSingleOpt.setEnabled(false); // only chkSingle enables
		
		chkPgeneF.setEnabled(b);
		setPgenefEnable(false);
	}
	private void setPgenefEnable(boolean b) {
		chkLinkageInc.setEnabled(b);
		chkUnannotInc.setEnabled(b);
		chkOnlyInc.setEnabled(b);
		for(int x=0; x<exSpecies.length; x++)  exSpecies[x].setEnabled(b);
		for(int x=0; x<incSpecies.length; x++) incSpecies[x].setEnabled(b);
	}

	public  boolean isValidQuery() 	{ 
		boolean b = bValidQuery;
		bValidQuery=true;
		return b;
	}
	/************************************************************************/
	public class OptionsRow extends JPanel implements ItemSelectable, ItemListener {
		private static final long serialVersionUID = -8714286499322636798L;
		
		private JLabel titleLabel;
		private JRadioButton case1Button, case2Button, case3Button, case4Button, ignButton; // CAS540 add case4
		private ButtonGroup group;
		private Vector<ItemListener> listeners;
		
		public OptionsRow ( String titleText, String strCase1Text, String strCase2Text, 
						    String strCase3Text, String strCase4Text, String strIgnText, 
							int nInitialValue, int width )
		{
			titleLabel    = new JLabel(titleText);
			case1Button = 	new JRadioButton(strCase1Text);
			case2Button = 	new JRadioButton(strCase2Text);
			case3Button = 	new JRadioButton(strCase3Text);
			case4Button = 	new JRadioButton(strCase4Text);
			ignButton  = 	new JRadioButton(strIgnText);
			
		    group = new ButtonGroup();
		    group.add(case1Button); group.add(case2Button); group.add(case3Button); 
		    group.add(case4Button);group.add(ignButton);
			
			setLayout( new BoxLayout ( this, BoxLayout.X_AXIS ) );
			super.setBackground(Color.WHITE);
			add(titleLabel);
			if (width>0 && width > titleLabel.getPreferredSize().width) 
				add(Box.createHorizontalStrut(width-titleLabel.getPreferredSize().width));
			if (!strCase1Text.equals(""))  	{ case1Button.setBackground(Color.WHITE);	add(case1Button); }
			if (!strCase2Text.equals(""))	{ case2Button.setBackground(Color.WHITE);	add(case2Button); }
			if (!strCase3Text.equals(""))	{ case3Button.setBackground(Color.WHITE);	add(case3Button); }
			if (!strCase4Text.equals(""))	{ case4Button.setBackground(Color.WHITE);	add(case4Button); }
			if (!strIgnText.equals(""))		{ ignButton.setBackground(Color.WHITE);		add(ignButton); }
			
			listeners = new Vector<ItemListener>();
			case1Button.addItemListener(this);
			case2Button.addItemListener(this);
			case3Button.addItemListener(this);
			case4Button.addItemListener(this);
			ignButton.addItemListener(this);

			setValue ( nInitialValue );
		}
		
		public void setEnabled(boolean enabled) {
			titleLabel.setEnabled(enabled);
			case1Button.setEnabled(enabled);
			case2Button.setEnabled(enabled);
			case3Button.setEnabled(enabled);
			case4Button.setEnabled(enabled);
			ignButton.setEnabled(enabled);
		}
		public boolean isEnabled() {return titleLabel.isEnabled();} // CAS514
		
		public void itemStateChanged(ItemEvent e) {
	    	Iterator<ItemListener> iter = listeners.iterator();
	    	while ( iter.hasNext() ) {
	    		JRadioButton b = (JRadioButton)e.getItem();
	    		iter.next().itemStateChanged(new ItemEvent(this, e.getID(), b.getText(), e.getStateChange()));
	    	}
		}
		public boolean isOne() 		{return case1Button.isSelected();}
		public boolean isTwo()  	{return case2Button.isSelected();}
		public boolean isThree()  	{return case3Button.isSelected();}
		public boolean isFour()  	{return case4Button.isSelected();}
		
		public int getValue() {
			if (case1Button.isSelected()) return 1;
			if (case2Button.isSelected()) return 2;
			if (case3Button.isSelected()) return 3;
			if (case4Button.isSelected()) return 4;
			return 5;
		}
		public void setValue( int nVal ) {
			switch ( nVal ) {
			case 1: group.setSelected(case1Button.getModel(), true); break;
			case 2:	group.setSelected(case2Button.getModel(), true); break;		
			case 3:	group.setSelected(case3Button.getModel(), true); break;	
			case 4:	group.setSelected(case4Button.getModel(), true); break;	
			case 5:	group.setSelected(ignButton.getModel(), true); break;
			}
		}
		public void addItemListener(ItemListener l) {
			listeners.add(l);
		}
		public Object[] getSelectedObjects() {
			return null;
		}
		public void removeItemListener(ItemListener l) {
			listeners.remove(l);
		}
		public String toString() {
			if      (case1Button.isSelected()) return titleLabel.getText() + " " + case1Button.getText();
			else if (case2Button.isSelected()) return titleLabel.getText() + " " + case2Button.getText();
			else if (case3Button.isSelected()) return titleLabel.getText() + " " + case3Button.getText();
			else if (case4Button.isSelected()) return titleLabel.getText() + " " + case4Button.getText();
			else if (ignButton.isSelected())   return titleLabel.getText() + " " + ignButton.getText();
			else return "<blank>";
		}
	} // end Options class
    /************************************************************/
	private SyMAPQueryFrame theParentFrame = null;
	
	// Not big enough to bother adding buttons to pnlStepOne.expand(); pnlStepOne.collapse()
	private CollapsiblePanel pnlStep1 = null, pnlStep2 = null, pnlStep3 = null, pnlStep4 = null; 
	
	private JTextField txtAnno = null; 
	
	private SpeciesSelectPanel speciesPanel = null;	
	private JCheckBox [] incSpecies = null, exSpecies = null;
	private String [] speciesName = null;
	
	private JCheckBox chkSingle = null,
			chkOnlyInc = null, chkUnannotInc = null, chkLinkageInc = null;
	
	// CAS504 add
	private OptionsRow blockOptRow = null, annoOptRow=null;
	private JTextField txtCollinearN = null;
	private JLabel     lblCollinearN = null;
	private JRadioButton coGE, coEQ, coLE;
	
	private JComboBox <String> cmbSingleOpt = null;
	private JComboBox <String> cmbSingleSpecies = null;
	
	private JCheckBox chkPgeneF = null;
	private JCheckBox  chkBlock = null, chkCollinearSet = null, chkHitIdx = null, chkGeneNum = null;
	private JTextField txtBlock = null, txtCollinearSet = null, txtHitIdx = null, txtGeneNum = null;
	
	private JButton btnExecute = null;
	
	private int nSpecies=0;
	private HashMap <Integer, String> grpCoords = new HashMap <Integer, String> ();
	
	private boolean bValidQuery=true;
}
