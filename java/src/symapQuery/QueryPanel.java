package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.Vector;
import java.util.HashMap;
import java.util.HashSet;

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

import symap.Globals;
import symap.manager.Mproject;
import util.Jhtml;
import util.Utilities;

/***************************************************************
 * The Filter panel. 
 * CAS504 massive changes to queries
 * 
 * DBData filters:
 * 	isOneAnno  - can only query in SQL for isEitherAnno
 * 	isAnnoText - otherwise, when doing anno search as query, returned hits without anno if they overlap
 *  isGeneNum  - have to parse geneTag
 *  Locations  - hit ends on two lines
 *  
 *  Single all genes:
 *	SELECT PA.idx, PA.grp_idx, PA.start, PA.end, PA.strand, PA.name, PA.tag, PA.numhits 
 *      FROM pseudo_annot AS PA
		WHERE  PA.type='gene' AND PA.grp_idx in (1,2,4,3) order by PA.idx
 *	Pair: 
 *	SELECT PA.idx, PA.grp_idx, PA.start, PA.end, PA.strand, PA.name, PA.tag, PA.numhits, 
 *      PH.idx, PH.hitnum, 
 *      PH.proj1_idx, PH.proj2_idx, PH.grp1_idx, PH.grp2_idx, PH.start1, PH.start2, PH.end1, PH.end2, 
 *      PH.pctid, PH.cvgpct, PH.countpct, PH.strand, PH.score, PH.htype, PH.runsize, PH.runnum, 
 *      B.blocknum, B.score, 
 *      PH.annot1_idx, PH.annot2_idx # not displayed, used to join two rows; allGenes PHA.annot_idx, PHA.annot2_idx
 *      FROM pseudo_hits AS PH
		LEFT JOIN pseudo_hits_annot AS PHA ON PH.idx = PHA.hit_idx
		LEFT JOIN pseudo_annot AS PA ON PHA.annot_idx = PA.idx
		LEFT JOIN pseudo_block_hits AS PBH ON PBH.hit_idx = PH.idx
		LEFT JOIN blocks AS B ON B.idx = PBH.block_idx  where PH.pair_idx=1   ORDER BY PH.hitnum  asc
 */
public class QueryPanel extends JPanel {
	private static final long serialVersionUID = 2804096298674364175L;
	private boolean AND=true;
	private static final int singleOrphan=0, singleGenes=1;

	protected QueryPanel(SyMAPQueryFrame parentFrame) {
		theParentFrame = parentFrame;
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(Color.WHITE);

		createQueryPanel();
	}
	
	protected boolean isIncludeMinor() { // CAS548 add; all show minor; multi
		return isEveryStarAnno() || (isMultiAnno() && chkMultiN.isSelected()) 
				|| isGeneNum() || isHitNum() || isAnnoTxt();
	}
	protected boolean isEveryStarAnno() {// CAS547 add
		return (radAnnoEveryStar.isEnabled() && radAnnoEveryStar.isSelected()); 
	} 
	protected boolean isEveryAnno() {
		return (radAnnoEvery.isEnabled() && radAnnoEvery.isSelected()); 
	}
	protected boolean isOneAnno() 	 {return radAnnoOne.isEnabled() && radAnnoOne.isSelected(); } 	// DBdata filter
	protected boolean isBothAnno()   {return radAnnoBoth.isEnabled() && radAnnoBoth.isSelected(); } 
	private   boolean isNoAnno() 	 {return radAnnoNone.isEnabled() && radAnnoNone.isSelected(); } 
	
	private boolean isBlock() 		{return radBlockYes.isEnabled() && radBlockYes.isSelected(); }
	private boolean isNotBlock() 	{return radBlockNo.isEnabled() && radBlockNo.isSelected(); }
	
	protected boolean isAnnoTxt()	{return txtAnno.isEnabled() && txtAnno.getText().trim().length()>0;}// DBdata filter;
	protected String getAnnoTxt()	{return txtAnno.getText().trim();}
	
	private boolean isBlockNum(){     // CAS514 was not checking for selected
		return chkBlock.isEnabled() && chkBlock.isSelected() && txtBlock.getText().trim().length()>0;}
	
	private boolean isCollinearSet(){ // CAS514 was not checking for selected
		return chkCollinearSet.isEnabled() && chkCollinearSet.isSelected() && txtCollinearSet.getText().trim().length()>0;}
	
	protected boolean isHitNum()	{ // CAS548 renamed from isHitIdx
		return chkHitNum.isEnabled() && chkHitNum.isSelected() && txtHitNum.getText().trim().length()>0;}
	
	protected boolean isGeneNum()	{  
		return chkGeneNum.isEnabled() && chkGeneNum.isSelected() && txtGeneNum.getText().trim().length()>0;}
	
	protected String getGeneNum() { // DBdata filter
		if (!isGeneNum()) return null;
		return txtGeneNum.getText().trim();
	}
	
	protected boolean isMultiAnno()  {
		String txt = txtMultiN.getText().trim();
		return txtMultiN.isEnabled() && txt.length()>0 && !txt.equals("0"); } // DBdata filter
	
	protected int getMultiN() {
		if (!txtMultiN.isEnabled()) return 0;
		String ntext = txtMultiN.getText();
		if (ntext.length()==0) return 0;
		try {
			int n = Integer.parseInt(ntext);
			if (n<0) {
				Utilities.showWarningMessage("Invalid Multi size (" + ntext + "), must be positive.");
				bValidQuery=false;
				return 0;
			}
			return n;
		} catch (Exception e) { 
			Utilities.showWarningMessage("Invalid Multi integer ("+ ntext + ")" );
			bValidQuery=false;
		}; 
		return 0;
	}
	
	// these are only good for the last query, so should only be used for building the tables
	protected boolean isSingle() 		{ return chkSingle.isEnabled() && chkSingle.isSelected(); }
	protected boolean isSingleOrphan()	{ return isSingle() && cmbSingleOpt.getSelectedIndex()==singleOrphan;}
	protected boolean isSingleGenes()	{ return isSingle() && cmbSingleOpt.getSelectedIndex()==singleGenes;}
	
	protected boolean isPgeneF()    	{ return chkPgeneF.isEnabled() 		&& chkPgeneF.isSelected(); }
	protected boolean isLinkageInc() 	{ return chkLinkageInc.isEnabled() 	&& chkLinkageInc.isSelected(); }
	protected boolean isUnannotInc() 	{ return chkUnannotInc.isEnabled() 	&& chkUnannotInc.isSelected(); }
	protected boolean isOnlyInc()    	{ return chkOnlyInc.isEnabled() 	&& chkOnlyInc.isSelected(); }
	protected boolean isInclude(int sp) { return incSpecies[sp].isEnabled() && incSpecies[sp].isSelected(); }
	protected boolean isExclude(int sp) { return exSpecies[sp].isEnabled() 	&& exSpecies[sp].isSelected();}
	
	protected SpeciesSelectPanel getSpeciesPanel() {return speciesPanel;}
	protected HashMap <Integer, String> getGrpCoords() { return grpCoords;} // DBdata filter
	
	// geneNum and Anno will show correct chrs but genenum/anno on opposite; CAS548 provide set instead of one
	protected HashSet<Integer> getGrp() {
		HashSet <Integer> idxSet = new HashSet <Integer> ();
		for (int p=0; p<nSpecies; p++) {
			int index = speciesPanel.getSelChrIdx(p); 
			if(index > 0) idxSet.add(index);
		} 
		return idxSet;
	}
	/****************************************************
	 * Make query
	 **************************************************/
	protected String makeSQLquery() {
		grpCoords.clear(); // CAS514 in case makeSpChrWhere is not called
		
		bValidQuery=true;
		
		FieldData theFields = FieldData.getFields(isIncludeMinor(), theParentFrame.isAlgo2()); // CAS547 Every+, CAS548 add v547 for olap
   	 
		boolean bSingle = isSingle();
		String sql;
		if (bSingle) sql = "SELECT " + theFields.getDBFieldList(bSingle) + makeSingleSQL();
		else         sql = "SELECT " + theFields.getDBFieldList(bSingle) + makePairSQL(theFields.getJoins());

		return sql;
	}
	/****************************************************
	 *  CAS513 previous was not working right - rewrote some things
	 *  CAS514 add pairs to orphan queries e.g. getPairWhere() = PH.pair_idx IN (1,2,3)
	 *  	   without the pairs, it will select all orphans in DB
	 *  	   Add the Genes Only - this is all genes regardless of pairs
	 *  CAS547 Add Gene with hits, which displays all hit
	 */
	private String makeSingleSQL() {
		boolean bOrphan = isSingleOrphan();
		
		String from = " FROM pseudo_annot AS " + Q.PA;
		
		String whereClause;
		if (bOrphan) 
			whereClause =
			" WHERE not exists " +
			"\n(SELECT * FROM pseudo_hits_annot AS PHA " +
			"\nLEFT JOIN pseudo_hits as PH on PH.idx=PHA.hit_idx " + // join to restrict pairs
			"\nWHERE PA.idx=PHA.annot_idx and " + speciesPanel.getPairWhere() + ") and ";
		else 
			whereClause=	"\nWHERE ";
			// CAS541 "\nLEFT JOIN pseudo_hits_annot as PHA on PHA.annot_idx=PA.idx " +
		
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
	 * isOneAnno and annotatin requires postprocessing, though anno exists is checked below
	 */
	private String makePairSQL(String join) {
		String whereClause= " where " + speciesPanel.getPairWhere(); // PH.pair_idx in (...)
		
		// Chr, loc;
		whereClause = joinBool(whereClause, makeSpChrWhere(), AND);
		
		if (isHitNum()) { 					// CAS520 changed from idx to hitnum, which needs chrs
			whereClause = joinBool(whereClause, makeChkHitWhere(), AND);
		}
		else if (isGeneNum()) {    			// CAS541 check in DBdata.passFilters
			whereClause = joinBool(whereClause, makeChkGeneNumWhere(), AND); // CAS548 add either gene 
		}
		else if (isBlockNum()) { 			// if block, only anno filter and chromosome (CAS513 add chromosome)
			whereClause = joinBool(whereClause, makeChkBlockWhere(), AND);
		}
		else if (isCollinearSet()) { 		// if collinear, only anno filter and chromosome (CAS513 add chromosome)
			whereClause = joinBool(whereClause, makeChkCollinearWhere(), AND);
		}
		else {// isSynteny, isAnno, isCollinear
			if (isBlock())         	whereClause = joinBool(whereClause, "PBH.block_idx is not null", AND);
			else if (isNotBlock()) 	whereClause = joinBool(whereClause, "PBH.block_idx is null", AND);
			
			if (isNoAnno())	 
				whereClause = joinBool(whereClause, "(PH.annot1_idx=0 and PH.annot2_idx=0) AND PA.idx is null", AND); // 'is null' is necessary. 
			else if (isBothAnno()) 														// CAS543 add search here instead of DBdata
				whereClause = joinBool(whereClause, "(PH.annot1_idx>0 and PH.annot2_idx>0)", AND); 
			else if (isOneAnno() || isMultiAnno() || isEveryAnno() || isEveryStarAnno() || isAnnoTxt())  // isOne&isMulti finished in DBdata
				whereClause = joinBool(whereClause, "(PH.annot1_idx>0 or PH.annot2_idx>0)", AND); 
			
			int n = isCollinearSize(true);
			String op = "PH.runsize>=" + n;
			if      (radCoEQ.isSelected()) op = "PH.runsize=" + n;
			else if (radCoLE.isSelected()) op = "PH.runsize>0 and PH.runsize<=" + n;
			if (n>0) whereClause = joinBool(whereClause, op, AND); // CAS517 was >
		}
		return " FROM pseudo_hits AS " + Q.PH + join + " " + whereClause + "  ORDER BY PH.hitnum  asc"; // CAS520 was PH.idx
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
	// this only return one occurrence  CAS520 from idx to hitnum
	private String makeChkHitWhere() {
		String run = txtHitNum.getText().trim();
		if (run.equals("")) return "";
		
		try {
			int n = Integer.parseInt(run);
			return "PH.hitnum=" + n; 
		}
		catch (Exception e) {
			Utilities.showWarningMessage(
				"Invalid hit# (" + run + "). Should be single number.\n");
			txtHitNum.setText("");
			bValidQuery=false;
			return "";
		}
	}
	// CAS543 allow suffix (was just allowed number)
	private String makeChkGeneNumWhere() {
		String gnStr = txtGeneNum.getText().trim();
		
		if (Utilities.isValidGenenum(gnStr)) return "(PH.annot1_idx>0 or PH.annot2_idx>0)";
		
		Utilities.showWarningMessage(
			"The Gene# entered is '" + gnStr + "', which is not a valid Gene#. \nEnter a number, or number.suffix\n");
		txtGeneNum.setText("");
		bValidQuery=false;
		return "";	
	}
	
	// The chromosomes are queried in MySQL and the locations in DBdata; Works for both orphan and hit
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
			if (index <= 0) continue;
			
			if (grpList.equals("")) grpList = index + "";
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
		else { // grp_idx is unique to one species one chromosome
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
	 * Summary  CAS503 made this a complete list of filters; CAS514 reorder again
	 */
	protected String makeSummary() { 
		int numSpecies = speciesPanel.getNumSpecies();
		if(numSpecies ==0) return "No species"; // not possible?
		
		String anno = (txtAnno.isEnabled()) ? txtAnno.getText() : "";
		String loc =  makeSummaryLocation(); // chr must be set to return location
		String retVal="";
		
		if(isSingle()) {
			retVal = (isSingleOrphan()) ? "Orphan genes" : "All genes";
			retVal = joinStr(retVal, anno, ";  ");
			
			String selSingSp = (String) cmbSingleSpecies.getSelectedItem();
			if (loc!="") retVal = joinStr(retVal, loc, ";  ");
			else retVal = joinStr(retVal, selSingSp, ";  "); 
				
			return retVal;
		}
		if (isHitNum()) {
			retVal = joinStr(retVal,  "Hit#="+txtHitNum.getText().trim(), ";  "); // CAS548 remove "(may overlap..."
		}
		else if (isBlockNum()) {
			retVal = joinStr(retVal, "Block="+txtBlock.getText().trim(), ";  ");
		}
		else if (isCollinearSet()) {
			retVal = joinStr(retVal, "Collinear set "+txtCollinearSet.getText().trim(), ";  ");
		}
		else if (isGeneNum()) {
			retVal = joinStr(retVal, "Gene# "+txtGeneNum.getText().trim(), ";  ");
		}
		else {
			if (isBlock())			retVal = joinStr(retVal, "Block hits", ";  ");
			else if (isNotBlock()) 	retVal = joinStr(retVal, "No Block hits", ";  ");
			
			if (isEveryStarAnno())  retVal = joinStr(retVal, "Every Gene with ANY hit", ";  "); // must be before next if
			else if (isEveryAnno()) retVal = joinStr(retVal, "Every Gene with best hit", ";  ");
			else if (isOneAnno())	retVal = joinStr(retVal, "One Gene best hit", ";  ");
			else if (isBothAnno())	retVal = joinStr(retVal, "Both Gene best hits", ";  ");
			else if (isNoAnno()) 	retVal = joinStr(retVal, "No Gene hits", ";  ");
			
			int n=isCollinearSize(false);
			if (n>0) {
				String op = ">=";
				if (radCoEQ.isSelected()) op = "=";
				else if (radCoLE.isSelected()) op = "<=";
				retVal = joinStr(retVal, "Collinear size " + op + n, ";  ");
			}
			
			if (isMultiAnno())	{
				if (chkMultiN.isSelected())
					retVal = joinStr(retVal, "Genes with multiple hits>=" + getMultiN() + "*", ";  ");
				else 
					retVal = joinStr(retVal, "Genes with multiple hits>=" + getMultiN(), ";  ");
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
		if (retVal.equals("")) retVal= "Pair Hits"; 
		retVal = joinStr(retVal, anno, ";  ");
		retVal = joinStr(retVal, loc, ";  "); 
	
		return retVal; 
	}
	
	// CAS504 add Include 
	private String makeSummaryLocation() {
		int numSpecies = speciesPanel.getNumSpecies();

		String retVal = ""; // CAS503 added info and remove some wordiness, i.e. do not need to list species unless location

		String type = isSingle() ? " Gene Loc " : " Hit Loc "; // CAS541 add type; the locs are checked in DBdata
		
		for(int x=0; x<numSpecies; x++) {
			if (!speciesPanel.isSpEnabled(x)) continue; // CAS518 can be disabled by Single
			
			String species = speciesPanel.getSpName(x);
			String chroms =  speciesPanel.getSelChrNum(x);
			String start = 	 speciesPanel.getStartAbbr(x);
			String end = 	 speciesPanel.getStopAbbr(x);
			if(!chroms.equals("All")) {
				String loc = " Chr " + chroms;
				if (!start.equals("") && !end.equals("")) loc += type + start + "-" + end; 
				else if (!start.equals("")) loc += type + start + "-end"; // CAS549 add -end
				else if (!end.equals(""))   loc += type + "0-" + end; // CAS513 was 1; CAS549 add 0-
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
				bValidQuery=false; 
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
		speciesPanel = new SpeciesSelectPanel(theParentFrame, this);
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

		annoLabel = new JLabel("Annotation Description");
		txtAnno = new JTextField(30);
		row.add(annoLabel); row.add(Box.createHorizontalStrut(10));
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
		int width=140;
		
		// block
		JPanel brow = createRow();
		lblBlockOpts = 		new JLabel("In Block (Synteny hit)");
		radBlockYes = 		new JRadioButton("Yes"); 	radBlockYes.setBackground(Color.WHITE); 
		radBlockNo = 		new JRadioButton("No"); 	radBlockNo.setBackground(Color.WHITE);
		radBlockIgnore = 	new JRadioButton("Ignore"); radBlockIgnore.setBackground(Color.WHITE);
		radBlockIgnore.setToolTipText("Does not matter if hit is in a block or not");
		
		brow.add(lblBlockOpts); 	
		if (width>0 && width > lblBlockOpts.getPreferredSize().width) 
			brow.add(Box.createHorizontalStrut(width-lblBlockOpts.getPreferredSize().width));
		brow.add(radBlockYes);		brow.add(Box.createHorizontalStrut(1));
		brow.add(radBlockNo);		brow.add(Box.createHorizontalStrut(1));
		brow.add(radBlockIgnore);	brow.add(Box.createHorizontalStrut(1));
		ButtonGroup bg = new ButtonGroup();
		bg.add(radBlockYes); bg.add(radBlockNo); bg.add(radBlockIgnore);
		radBlockIgnore.setSelected(true);
		panel.add(brow); panel.add(Box.createVerticalStrut(5));
		
		// anno
		JPanel arow = createRow();
		lblAnnoOpts 		= new JLabel("Annotated (Gene hit)");
		radAnnoEvery 		= new JRadioButton("Every"); 	radAnnoEvery.setBackground(Color.WHITE); 
		radAnnoEvery.setToolTipText("Every hit aligned to at least one gene - best gene listed");
		radAnnoEveryStar 	= new JRadioButton("Every" + Globals.minorAnno); radAnnoEveryStar.setBackground(Color.WHITE); 
		radAnnoEveryStar.setToolTipText("Every hit aligned to at least one gene - ALL genes listed");
		radAnnoOne 			= new JRadioButton("One"); 		radAnnoOne.setBackground(Color.WHITE); 
		radAnnoOne.setToolTipText("Every hit aligned to only one gene - best gene listed");
		radAnnoBoth 		= new JRadioButton("Both"); 	radAnnoBoth.setBackground(Color.WHITE); 
		radAnnoBoth.setToolTipText("Every hit aligned to two genes - best genes listed");
		radAnnoNone 		= new JRadioButton("None"); 	radAnnoNone.setBackground(Color.WHITE); 
		radAnnoNone.setToolTipText("Every hit aligned to no genes");
		radAnnoIgnore 		= new JRadioButton("Ignore"); 	radAnnoIgnore.setBackground(Color.WHITE); 
		radAnnoIgnore.setToolTipText("Does not matter if hit aligned to gene or not");
		arow.add(lblAnnoOpts); 			
		if (width>0 && width > lblAnnoOpts.getPreferredSize().width) 
			arow.add(Box.createHorizontalStrut(width-lblAnnoOpts.getPreferredSize().width));
		arow.add(radAnnoEvery);		brow.add(Box.createHorizontalStrut(1));
		arow.add(radAnnoEveryStar);	brow.add(Box.createHorizontalStrut(1)); // CAS549 works for algo1 now too
		arow.add(radAnnoOne);			brow.add(Box.createHorizontalStrut(1));
		arow.add(radAnnoBoth);			brow.add(Box.createHorizontalStrut(1));
		arow.add(radAnnoNone);			brow.add(Box.createHorizontalStrut(1));
		arow.add(radAnnoIgnore);		brow.add(Box.createHorizontalStrut(1));
		ButtonGroup ag = new ButtonGroup();
		ag.add(radAnnoEvery);ag.add(radAnnoEveryStar);ag.add(radAnnoOne);ag.add(radAnnoBoth);
		ag.add(radAnnoNone);ag.add(radAnnoIgnore);
		radAnnoIgnore.setSelected(true);
		panel.add(arow);
		panel.add(Box.createVerticalStrut(5));
		
		// collinear
		JPanel crow = createRow();
		lblCollinearN = new JLabel("Collinear size ");
		crow.add(lblCollinearN); crow.add(Box.createHorizontalStrut(3));
		
		txtCollinearN = new JTextField(3); txtCollinearN.setBackground(Color.WHITE);
		txtCollinearN.setText("0"); txtCollinearN.setMaximumSize(txtCollinearN.getPreferredSize());
		crow.add(txtCollinearN); 
		int w = (int) (lblCollinearN.getPreferredSize().getWidth()+txtCollinearN.getPreferredSize().getWidth()+1);
		if (width>0 && width > w) arow.add(Box.createHorizontalStrut(width-w));
		
		radCoGE = new JRadioButton(">="); radCoGE.setBackground(Color.WHITE);
		radCoEQ = new JRadioButton("=");  radCoEQ.setBackground(Color.WHITE);
		radCoLE = new JRadioButton("<="); radCoLE.setBackground(Color.WHITE);
		ButtonGroup g = new ButtonGroup();
		g.add(radCoGE); g.add(radCoEQ); g.add(radCoLE);
		radCoGE.setSelected(true);
		crow.add(radCoGE); crow.add(Box.createHorizontalStrut(1));
		crow.add(radCoEQ); crow.add(Box.createHorizontalStrut(1));
		crow.add(radCoLE); crow.add(Box.createHorizontalStrut(30));
		
		lblMultiN = new JLabel("Multi-hit >="); // CAS548 add
		crow.add(lblMultiN); crow.add(Box.createHorizontalStrut(3));
		txtMultiN = new JTextField(3); txtMultiN.setBackground(Color.WHITE);
		txtMultiN.setText("0"); txtMultiN.setMaximumSize(txtCollinearN.getPreferredSize());
		crow.add(txtMultiN); crow.add(Box.createHorizontalStrut(1));
		chkMultiN = new JCheckBox("Minor*"); chkMultiN.setBackground(Color.WHITE); chkMultiN.setSelected(false);
		chkMultiN.setToolTipText("If checked, include minor gene hits");
		crow.add(chkMultiN);
		
		panel.add(crow); 
		
		return panel;
	}
	/** Block and Hit **/
	private JPanel createFilterCheckPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBackground(Color.WHITE);
		
		JPanel row = createRow();
		
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
		
		chkHitNum = new JCheckBox("Hit#");
		chkHitNum.setBackground(Color.WHITE);
		chkHitNum.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				boolean bSel = chkHitNum.isSelected();
				setAllEnabled(!bSel); // turn all off
				if (bSel) {
					chkHitNum.setEnabled(true);
					txtHitNum.setEnabled(true);
					speciesPanel.setEnabled(true); 
					setAnnoEnabled(false);
				}
			}
		});
		txtHitNum = new JTextField(4); txtHitNum.setEnabled(false);
		row.add(chkHitNum); row.add(Box.createHorizontalStrut(5));
		row.add(txtHitNum); row.add(Box.createHorizontalStrut(15));
		
		chkGeneNum = new JCheckBox("Gene#");
		chkGeneNum.setBackground(Color.WHITE);
		chkGeneNum.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				boolean bSel = chkGeneNum.isSelected();
				setAllEnabled(!bSel); // turn all off
				if (bSel) {
					chkGeneNum.setEnabled(true);
					txtGeneNum.setEnabled(true); 
					//speciesPanel.setIsGene(true);// CAS541
					setAnnoEnabled(false);
				}
				//else  speciesPanel.setIsGene(false);
			}
		});
		txtGeneNum = new JTextField(6); txtGeneNum.setEnabled(false);
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
		JPanel	row = createRow();
		
		chkSingle = new JCheckBox("Single");	
		chkSingle.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkSingle.setBackground(Color.WHITE);
		chkSingle.addActionListener(new ActionListener() { // CAS503 disabled others if orphans checked
			public void actionPerformed(ActionEvent e) {
				boolean isSelect = chkSingle.isSelected();
				setAllEnabled(!isSelect);
				chkSingle.setEnabled(true);
				
				if (isSelect) {
					String selSp = (String) cmbSingleSpecies.getSelectedItem();
					speciesPanel.setIsSingle(selSp, true); // CAS541 rest of logic in SpeciesSelect
					
					cmbSingleSpecies.setEnabled(true);
					cmbSingleOpt.setEnabled(true);
					singleProjLabel.setEnabled(true);
				}
				else {
					speciesPanel.setIsSingle(null, false);
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
				String selSp = (String) cmbSingleSpecies.getSelectedItem();
				speciesPanel.setIsSingle(selSp, true); // CAS518 change; CAS541 put logic in SpeciesSelectPanel
			}
		});
		cmbSingleSpecies.setEnabled(false);
		
		singleProjLabel = new JLabel("Project: "); singleProjLabel.setEnabled(false);
		row.add(singleProjLabel); row.add(Box.createHorizontalStrut(1));
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

			JPanel row = createRow();
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
	
			row = createRow();
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
		
		radBlockIgnore.setSelected(true);
		radAnnoIgnore.setSelected(true);
		radCoGE.setSelected(true);
		
		txtAnno.setText("");
		txtCollinearN.setText("");
		txtMultiN.setText("");
		chkMultiN.setSelected(false);
		
		txtBlock.setText(""); 			txtBlock.setEnabled(false);	chkBlock.setSelected(false);
		txtCollinearSet.setText(""); 	txtCollinearSet.setEnabled(false);	chkCollinearSet.setSelected(false);
		
		txtHitNum.setText(""); 			txtHitNum.setEnabled(false);   chkHitNum.setSelected(false);
		txtGeneNum.setText(""); 		txtGeneNum.setEnabled(false);  chkGeneNum.setSelected(false);
		
		chkSingle.setSelected(false);
		cmbSingleOpt.setSelectedIndex(0);     cmbSingleOpt.setEnabled(false);
		cmbSingleSpecies.setSelectedIndex(0); cmbSingleSpecies.setEnabled(false); 
		singleProjLabel.setEnabled(false);
		
		chkPgeneF.setSelected(false); setPgenefEnable(false);
		
		speciesPanel.clear();
		speciesPanel.setEnabled(true); // CAS521 was leaving it disabled
	}
	private void setAnnoEnabled(boolean b) {
		annoLabel.setEnabled(b);
		txtAnno.setEnabled(b); // all use except HitIdx and GeneNum, which disable
	}
	private void setAllEnabled(boolean b) { // everything
		if (b) setAnnoEnabled(b);
		
		lblBlockOpts.setEnabled(b); radBlockYes.setEnabled(b);radBlockNo.setEnabled(b);radBlockIgnore.setEnabled(b);
		
		lblAnnoOpts.setEnabled(b); radAnnoEvery.setEnabled(b);radAnnoEveryStar.setEnabled(b);
		radAnnoOne.setEnabled(b);  radAnnoBoth.setEnabled(b); 
		radAnnoNone.setEnabled(b); radAnnoIgnore.setEnabled(b);
		
		lblMultiN.setEnabled(b); txtMultiN.setEnabled(b); chkMultiN.setEnabled(b);
		lblCollinearN.setEnabled(b); txtCollinearN.setEnabled(b); radCoGE.setEnabled(b);radCoEQ.setEnabled(b);radCoLE.setEnabled(b);
		
		chkBlock.setEnabled(b);  		txtBlock.setEnabled(false); 		// only chkBlock enables
		chkCollinearSet.setEnabled(b);  txtCollinearSet.setEnabled(false); 	// only chkColinear enables
		chkHitNum.setEnabled(b); 		txtHitNum.setEnabled(false);		// only chkHitIdx enables
		chkGeneNum.setEnabled(b); 		txtGeneNum.setEnabled(false);		// only chkHitIdx enables
	
		speciesPanel.setEnabled(b); // All chr, start, end
		
		chkSingle.setEnabled(b); 
		cmbSingleSpecies.setEnabled(false); cmbSingleOpt.setEnabled(false); singleProjLabel.setEnabled(false);// only chkSingle enables
		
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
	private JPanel createRow() {
		JPanel arow = new JPanel();
		arow.setLayout(new BoxLayout(arow, BoxLayout.LINE_AXIS));
		arow.setAlignmentX(Component.LEFT_ALIGNMENT);
		arow.setBackground(Color.WHITE);
		return arow;
	}
	public  boolean isValidQuery() 	{ 
		boolean b = bValidQuery;
		bValidQuery=true;
		return b;
	}
	public boolean isAlgo2() {return theParentFrame.isAlgo2();}
	
     /************************************************************/
	private SyMAPQueryFrame theParentFrame = null;
	
	// Not big enough to bother adding buttons to pnlStepOne.expand(); pnlStepOne.collapse()
	private CollapsiblePanel pnlStep1 = null, pnlStep2 = null, pnlStep3 = null, pnlStep4 = null; 
	
	private JTextField txtAnno = null; 
	private JLabel annoLabel = null;
	
	private SpeciesSelectPanel speciesPanel = null;	
	private JCheckBox [] incSpecies = null, exSpecies = null;
	private String [] speciesName = null;
	
	private JCheckBox chkSingle = null, chkOnlyInc = null, chkUnannotInc = null, chkLinkageInc = null;
	
	private JLabel lblBlockOpts = null;	// CAS547 removed OptionRow class to create explicitly
	private JRadioButton radBlockYes, radBlockNo, radBlockIgnore;
	
	private JLabel lblAnnoOpts = null;
	private JRadioButton radAnnoEvery, radAnnoEveryStar, radAnnoOne, radAnnoBoth, 
						 radAnnoNone, radAnnoIgnore;	
	
	private JTextField txtMultiN = null;// CAS548 add multi
	private JLabel     lblMultiN = null;
	private JCheckBox  chkMultiN = null;
	
	private JTextField txtCollinearN = null;
	private JLabel     lblCollinearN = null;
	private JRadioButton radCoGE, radCoEQ, radCoLE;
	
	private JLabel singleProjLabel=null;
	private JComboBox <String> cmbSingleOpt = null;
	private JComboBox <String> cmbSingleSpecies = null;
	
	private JCheckBox chkPgeneF = null;
	private JCheckBox  chkBlock = null, chkCollinearSet = null, chkHitNum = null, chkGeneNum = null;
	private JTextField txtBlock = null, txtCollinearSet = null, txtHitNum = null, txtGeneNum = null;
	
	private JButton btnExecute = null;
	
	private int nSpecies=0;
	private HashMap <Integer, String> grpCoords = new HashMap <Integer, String> ();
	
	private boolean bValidQuery=true;
}
