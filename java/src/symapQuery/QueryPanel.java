package symapQuery;

import java.awt.Color;
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
 * The QuerySetup panel. 	
 * CAS504 massive changes to queries  CAS561 Jcomp everything; Remove Multi-Exon; Add Species check
 * 
 * DBData filters:
 * 	isOneAnno  - can only query in SQL for isEitherAnno
 * 	isAnnoText - otherwise, when doing anno search as query, returned hits without anno if they overlap
 *  isGeneNum  - have to parse geneTag
 *  Locations  - hit ends on two lines
 *  Gene# 	   - Must fill in the other side in DBdata
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
	protected boolean isMinorOlap=true;		// -ii show overlap or minor
	private boolean AND=true;
	private String coDefNum = "3", multiDefNum="4";	// CAS555 add defaults
	// Only one checkbox is allowed to be selected at a time. See setChkEnable. CAS561 add
	private int nChkNone=0, nChkSingle=1, nChkBlk=2, nChkCoSet=3, nChkHit=4, nChkGene=5, nChkMulti=6, nChkPgene=7;
	
	protected QueryPanel(SyMAPQueryFrame parentFrame) {
		theParentFrame = parentFrame;
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(Color.WHITE);

		createQueryPanel();

		setClearDefaults(); // No matter on they get set on create, they all start out cleared
	}
	protected DBconn2 getDBC() {return theParentFrame.getDBC();} // DBdata needs this for Gene#
	protected boolean isAlgo2() {return theParentFrame.isAlgo2();}
	    
	/*************************************************************
	 * XXX State and get
	 */
	protected boolean isGroup() { // for TableReport; CAS556 add
		return isMultiAnno() || isPgeneF() || isGeneNum();
	}
	protected boolean isIncludeMinor() { // CAS548 add; all show minor; multi; CAS560 add isOlapAnno
		return isEveryStarAnno() || isOlapAnno() || (isMultiAnno() && chkMultiMinor.isSelected()) 
				|| isGeneNum() || isHitNum(); // CAS555 remove isAnnoTxt()
	}
	protected boolean isEveryStarAnno() {// CAS547 add
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
	protected boolean isOlapAnno()   {
		return (radAnnoOlap.isEnabled() && radAnnoOlap.isSelected()); 
	}
	private   boolean isNoAnno() 	 {return radAnnoNone.isEnabled() && radAnnoNone.isSelected(); } 
	
	protected boolean isBlock() 		{return radBlkYes.isEnabled() && radBlkYes.isSelected(); }
	private boolean isNotBlock() 	{return radBlkNo.isEnabled() && radBlkNo.isSelected(); }
	
	protected boolean isAnnoTxt()	{return txtAnno.isEnabled() && txtAnno.getText().trim().length()>0;}// DBdata filter;
	protected String getAnnoTxt()	{return txtAnno.getText().trim();}
	
	// CAS561 quit checking for blank text -- that should be error
	private boolean isBlockNum()	{return chkOnBlkNum.isEnabled() && chkOnBlkNum.isSelected();}
	protected boolean isHitNum()	{return chkOnHitNum.isEnabled() && chkOnHitNum.isSelected();}
	protected boolean isGeneNum()	{ return chkOnGeneNum.isEnabled() && chkOnGeneNum.isSelected();}
	private boolean isCollinearNum(){ return chkOnCoSetNum.isEnabled() && chkOnCoSetNum.isSelected();}
	
	protected String getGeneNum() { // DBdata filter; Text already checked
		if (!isGeneNum()) return null;
		return txtGeneNum.getText().trim();
	}
	// multi DBdata filter
	protected boolean isMultiAnno()  {return chkOnMultiN.isEnabled() && chkOnMultiN.isSelected();} 
	protected int getMultiN() {
		if (!txtMultiN.isEnabled()) return 0;
		String ntext = txtMultiN.getText();
		
		int n = getValidNum(ntext);
		if (n>0) return n;
		
		showWarnMsg("Invalid Multi size (" + ntext + ").");
		bValidQuery=false;
		txtMultiN.setText(multiDefNum);
		return 0;
	}
	
	protected boolean isMultiChr() {return radMultiSame.isEnabled() && radMultiSame.isSelected();} // CAS555
	protected boolean isMultiTandem() {return radMultiTandem.isEnabled() && radMultiTandem.isSelected();} // CAS555
	
	// collinear
	private int isCoSetN(boolean prt) 	{ // CAS512 add warning the first time its called
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
	protected boolean isCollinear() {return (isCoSetN(false)>0);} // CAS556 DBdata to sort
	
	// these are only good for the last query, so should only be used for building the tables
	protected boolean isSingle() 		{ return chkOnSingle.isEnabled() && chkOnSingle.isSelected(); }
	protected boolean isSingleOrphan()	{ return isSingle() && radOrphan.isSelected();}
	protected boolean isSingleGenes()	{ return isSingle() && radSingle.isSelected();}
	
	protected boolean isPgeneF()    	{ return chkOnPgeneF.isEnabled() 		&& chkOnPgeneF.isSelected(); }
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
	 * XXX Make query
	 **************************************************/
	protected String makeSQLquery() {
		grpCoords.clear(); // in case makeSpChrWhere is not called

		bValidQuery=true;
		FieldData theFields = FieldData.getFields(isIncludeMinor(), theParentFrame.isAlgo2()); // CAS547 Every+, CAS548 add v547 for olap
		boolean bSingle = isSingle();
		
		String sql = "SELECT " + theFields.getDBFieldList(bSingle);
		if (bSingle) sql += makeSingleSQL();
		else         sql += makePairSQL(theFields.getJoins());

		if (!bValidQuery) return null;
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
		if (!speciesPanel.isAtLeastOneSpChecked()) {
			showWarnMsg("At least one species must be checked (i.e. before Species name).");
			bValidQuery=false;
			return "";
		}
		boolean bOrphan = isSingleOrphan();
		
		String from = " FROM pseudo_annot AS " + Q.PA;
		
		String whereClause;
		if (bOrphan)  whereClause = " WHERE not exists " +
			"\n(SELECT * FROM pseudo_hits_annot AS PHA " +
			"\nLEFT JOIN pseudo_hits as PH on PH.idx=PHA.hit_idx " + // join to restrict pairs
			"\nWHERE PA.idx=PHA.annot_idx and " + speciesPanel.getPairIdxWhere() + ") and ";
		else 
			whereClause=	"\nWHERE ";
		
		whereClause += " PA.type='gene'"; 
		
	// Annotation
		String text = txtAnno.getText();
		String anno = (text.length() == 0) ? "" : "PA.name LIKE '%" + text + "%'";
		whereClause = makeJoinBool(whereClause, anno, AND);
	
		String grp = "(PA.grp_idx IN (" + speciesPanel.getAllChrIdxForGene() + ")) ";
		whereClause = makeJoinBool(whereClause, grp, AND); // CAS561 change to get all grps enabled or selected 
		
		if (!bOrphan) whereClause += " order by PA.idx";

		return from + whereClause;
	}
	/*********************************************************
	 * Hit SELECT " + getDBFieldList() + " FROM pseudo_hits AS " + Q.PH + whereClause
	 * isOneAnno and annotatin requires postprocessing, though anno exists is checked below
	 */
	private String makePairSQL(String join) {
		if (isMultiAnno()) { // CAS561 this is only text box not checked until computed
			getMultiN();
			if (!bValidQuery) return "";
		}
		
		String whereClause = " where " + speciesPanel.getPairIdxWhere(); // PH.pair_idx in (...)
		
		// Chr, loc;
		whereClause = makeJoinBool(whereClause, makeSpChrLocWhere(), AND);
		
		if (isHitNum()) { 					// CAS520 changed from idx to hitnum, which needs chrs
			whereClause = makeJoinBool(whereClause, makeChkHitWhere(), AND);
		}
		else if (isGeneNum()) {    			// CAS541 check in DBdata.passFilters
			whereClause = makeJoinBool(whereClause, makeChkGeneNumWhere(), AND); // CAS548 add either gene 
			if (speciesPanel.isAtLeastOneSpChecked()) {
				String grp = "(PA.grp_idx IN (" + speciesPanel.getAllChrIdxForGene() + ")) ";
				whereClause = makeJoinBool(whereClause, grp, AND); 					 // CAS561 let user pick chr
			}
		}
		else if (isBlockNum()) { 			// if block, only anno filter and chromosome (CAS513 add chromosome)
			whereClause = makeJoinBool(whereClause, makeChkBlockWhere(), AND);
		}
		else if (isCollinearNum()) { 		// if collinear, only anno filter and chromosome (CAS513 add chromosome)
			whereClause = makeJoinBool(whereClause, makeChkCollinearWhere(), AND);
		}
		else {// isSynteny, isAnno, isCollinear
			if (isBlock())         	whereClause = makeJoinBool(whereClause, "PBH.block_idx is not null", AND);
			else if (isNotBlock()) 	whereClause = makeJoinBool(whereClause, "PBH.block_idx is null", AND);
			
			if (isNoAnno())	 
				whereClause = makeJoinBool(whereClause, "(PH.annot1_idx=0 and PH.annot2_idx=0) AND PA.idx is null", AND); // 'is null' is necessary. 
			else if (isBothAnno() || isMultiTandem()) 														// CAS543 add search here instead of DBdata
				whereClause = makeJoinBool(whereClause, "(PH.annot1_idx>0 and PH.annot2_idx>0)", AND); 
			else if (isOneAnno() || isMultiAnno() || isEveryAnno() || isEveryStarAnno() || isAnnoTxt() || isOlapAnno())  // isOne&isMulti finished in DBdata
				whereClause = makeJoinBool(whereClause, "(PH.annot1_idx>0 or PH.annot2_idx>0)", AND); 
			
			int n = isCoSetN(true);
			String op = "PH.runsize>=" + n;
			if      (radCoSetEQ.isSelected()) op = "PH.runsize=" + n;
			else if (radCoSetLE.isSelected()) op = "PH.runsize>0 and PH.runsize<=" + n;
			if (n>0) whereClause = makeJoinBool(whereClause, op, AND); // CAS517 was >
		}
		return " FROM pseudo_hits AS " + Q.PH + join + " " + whereClause + "  ORDER BY PH.hitnum  asc"; // CAS520 was PH.idx
	}	
	
	/******************************************************** 
	 * Block search; Convert block string to where statement 
	 *****/
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
		if (n>0)return "ph.runnum=" + n; 
		
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
	// CAS543 allow suffix (was just allowed number)
	private String makeChkGeneNumWhere() {
		String gnStr = txtGeneNum.getText().trim();
		
		if (gnStr.equals("") || !Utilities.isValidGenenum(gnStr)) { // CAS561 check for empty
			showWarnMsg("The Gene# entered is '" + gnStr 
					+ "', which is not a valid Gene#. \nEnter a number, or number.suffix\n");
			txtGeneNum.setText("");
			bValidQuery=false;
			return "";	
		}
		// CAS561 speedup - only gets one sides, searches for the other annot_idx genenum in DBdata 
		String gnSearch="";
		if (gnStr.contains(".")) {
			if (gnStr.endsWith(".")) gnStr = gnStr.substring(0, gnStr.indexOf("."));
			if (gnStr.contains(".")) {
				String [] tok = gnStr.split("\\.");
				int x = Utilities.getInt(tok[0]);
				if (x != -1) gnStr = x+"";
				else         gnStr = null;
			}
		}
		if (gnStr!=null) gnSearch = "and PA.genenum=" + gnStr;
		else symap.Globals.eprt("Cannot parse " + txtGeneNum.getText().trim());
		
		return "(PH.annot1_idx>0 or PH.annot2_idx>0) " + gnSearch;
	}
	
	// The chromosomes are queried in MySQL and the locations in DBdata; Works for both orphan and hit
	private String makeSpChrLocWhere() {
		String grpList="", where="";
		grpCoords.clear();
		
		// First check to see if any chromosomes are selected
		boolean isSet=false;
		for (int p=0; p<nSpecies && !isSet; p++) {
			int index = speciesPanel.getSelChrIdx(p); 
			if(index >0) isSet=true;
		}
		if (!isSet) return "";
		
		// Once chr per species can be selected
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
		
		if (isSingle()) { 
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

	/*******************************************************
	 * Summary  
	 */
	protected String makeSummary() { 
		int numSpecies = speciesPanel.getNumSpecies();
		if(numSpecies ==0) return "No species"; // not possible?
		
		String retVal="";
		String anno = (txtAnno.isEnabled()) ? txtAnno.getText() : "";
		String loc =  makeSummaryLocation(); 
		
		retVal = makeJoinDelim(retVal, anno, ";  "); // CAS555 was at bottom, so added last
		retVal = makeJoinDelim(retVal, loc, ";  "); 
		
		if(isSingle()) {
			retVal = (isSingleOrphan()) ? "Orphan genes" : "All genes";
			retVal = makeJoinDelim(retVal, loc, ";  ");
			retVal = makeJoinDelim(retVal, anno, ";  ");
			return retVal;
		}
		if (isHitNum()) {
			retVal = makeJoinDelim(retVal,  "Hit#="+txtHitNum.getText().trim(), ";  "); // CAS548 remove "(may overlap..."
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
			
			if (isEveryStarAnno())  retVal = makeJoinDelim(retVal, "Every Gene with ANY hit", ";  "); // must be before next if
			else if (isEveryAnno()) retVal = makeJoinDelim(retVal, "Every Gene with best hit", ";  ");
			else if (isOneAnno())	retVal = makeJoinDelim(retVal, "One Gene best hit", ";  ");
			else if (isBothAnno())	retVal = makeJoinDelim(retVal, "Both Gene best hits", ";  ");
			else if (isOlapAnno()) 	{
				if (isMinorOlap) retVal = makeJoinDelim(retVal, "Minor genes", ";  "); 
				else retVal = makeJoinDelim(retVal, "Overlapping genes with hit", ";  ");
			}
			else if (isNoAnno()) 	retVal = makeJoinDelim(retVal, "No Gene hits", ";  ");
			
			int n = isCoSetN(false);
			if (n>0) {
				String op = ">=";
				if (radCoSetEQ.isSelected()) op = "=";
				else if (radCoSetLE.isSelected()) op = "<=";
				retVal = makeJoinDelim(retVal, "Collinear size" + op + n, ";  ");
			}
			
			if (isMultiAnno())	{
				String multi = "Multi >=" + getMultiN();
				if (chkMultiMinor.isSelected()) 	multi += "*";
				if (radMultiSame.isSelected()) 		multi += ", Same Chr";
				if (radMultiTandem.isSelected()) 	multi += ", Tandem";
				retVal = makeJoinDelim(retVal, multi, ";  ");
			}
			
			if (isPgeneF()) {
				retVal = makeJoinDelim(retVal, "Run PgeneF", ";  ");
				String inc="", exc="";
				for (int i=0; i<speciesName.length; i++) {
					if (isInclude(i)) inc = makeJoinDelim(inc, speciesName[i],  ",");
					if (isExclude(i)) exc = makeJoinDelim(exc, speciesName[i],  ",");
				}
				if (!inc.equals("")) inc = "Inc (" + inc + ")";
				if (!exc.equals("")) exc = "Exc (" + exc + ")";
				retVal = makeJoinDelim(retVal, makeJoinDelim(inc, exc, ", "), ";  ");
				
				if(isUnannotInc())	retVal = makeJoinDelim(retVal, "Not annotated", ";  ");
				if(isLinkageInc())	retVal = makeJoinDelim(retVal, "Linkage", ";  ");
				if(isOnlyInc())		retVal = makeJoinDelim(retVal, "Only included", ";  ");
			}
		}
		if (retVal.equals("")) retVal= "Pair Hits"; 
		
		return retVal; 
	}
	// Summary for single and pairs
	private String makeSummaryLocation() {
		int numSpecies = speciesPanel.getNumSpecies();
		
		String retVal = "";
		String type = isSingle() ? " Gene Loc " : " Hit Loc "; // CAS541 add type; the locs are checked in DBdata
		
		boolean bAnyNotSel=false;
		for(int x=0; x<numSpecies; x++) {
			if (!speciesPanel.isSpEnabled(x)) bAnyNotSel=true; 
		}	
		for(int x=0; x<numSpecies; x++) {
			if (!speciesPanel.isSpEnabled(x)) continue; 
				
			String chroms =  speciesPanel.getSelChrNum(x);
			if(chroms.equals("All")) {
				if (bAnyNotSel) retVal = makeJoinDelim(retVal, speciesPanel.getSpName(x), ", ");
				continue;
			}
			
			String species = speciesPanel.getSpName(x);
			String start = 	 speciesPanel.getStartkb(x);
			String end = 	 speciesPanel.getStopkb(x);
			
			int cn = Utilities.getInt(chroms);
			String loc = (cn<0) ? " " + chroms : " Chr" + chroms;           // CAS561 may have prefix
			
			if (!start.equals("") && !end.equals("")) loc += type + start + "-" + end; 
			else if (!start.equals("")) loc += type + start + "-end"; // CAS549 add -end
			else if (!end.equals(""))   loc += type + "0-" + end;     // CAS549 add 0-
			retVal = makeJoinDelim(retVal, species + loc , ", ");
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
	
	/********************************************************************
	 * XXX Filter panels
	 ***************************************************************/
	private void createQueryPanel() {
		speciesPanel = new SpeciesSelectPanel(theParentFrame, this);
		nSpecies = speciesPanel.getNumSpecies();			// used in orphanPanel
		
		buttonPanel = createButtonPanel();
		searchPanel = createFilterAnnoPanel();
		singlePanel = createFilterSinglePanel();
		pairRadPanel  = createFilterPairRadPanel();
		pairChkPanel  = createFilterPairChkPanel();
		groupPanel  = createFilterGroupPanel();
	 
		add(buttonPanel);								add(Box.createVerticalStrut(10));
		
		pnlStep1 = new CollapsiblePanel("1. General", ""); 
		pnlStep1.add(searchPanel);		
		pnlStep1.add(speciesPanel);	
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
		pnlStep3.add(pairChkPanel);				
		pnlStep3.collapse();
		pnlStep3.expand();
		add(pnlStep3);								
		
		pnlStep4 = new CollapsiblePanel("4. Compute Gene Groups", "");
		pnlStep4.add(groupPanel);
		pnlStep4.collapse();
		pnlStep4.expand();
		add(pnlStep4);	
	}
	/** Top buttons **/
	private JPanel createButtonPanel() {
		JPanel panel = Jcomp.createRowPanel();
		
		btnExecute = Jcomp.createButton("Run Search","Apply filters and show results table"); 
		btnExecute.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String query = makeSQLquery();
				
				if (query!=null) {
					String sum = makeSummary();
					theParentFrame.makeTable(query, sum, isSingle());
				}
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
		
		annoLabel = Jcomp.createLabel("Annotation Description","A text string from the Gene Annotation");
		txtAnno = Jcomp.createTextField("", 30);
		
		row.add(annoLabel); row.add(Box.createHorizontalStrut(10));
		row.add(txtAnno);   row.setMaximumSize(row.getPreferredSize());
		
		page.add(row); 
		return page;
	}
	/** Orphan or Single genes **/
	private JPanel createFilterSinglePanel() {
		JPanel panel = Jcomp.createPagePanelNB();
		
		JPanel	row = Jcomp.createRowPanel();
		chkOnSingle = Jcomp.createCheckBox("Single", "Returns columns of gene information (not hit information)", true);
		chkOnSingle.addActionListener(new ActionListener() { // Disabled others if orphans checked
			public void actionPerformed(ActionEvent e) {
				setRadChkLoc(chkOnSingle.isSelected(), nChkSingle, SpeciesSelectPanel.locChr);
			}
		});
		row.add(chkOnSingle); row.add(Box.createHorizontalStrut(5));
		
		radOrphan = Jcomp.createRadio("Orphan genes (no hits)");
		radSingle = Jcomp.createRadio("All genes (w/o hits)");
		ButtonGroup bg = new ButtonGroup();
		bg.add(radOrphan); bg.add(radSingle); radSingle.setSelected(true);
		
		row.add(radOrphan); row.add(Box.createHorizontalStrut(5));
		row.add(radSingle);
		panel.add(row); 
		return panel;
	}
	/** Radio buttons **/
	private JPanel createFilterPairRadPanel() {
		JPanel panel = Jcomp.createPagePanelNB();
		int width=140;
		
		// block
		JPanel brow = Jcomp.createRowPanel();
		lblBlkOpts = Jcomp.createLabel("In Block (Synteny hit)", "Hit is in a synteny block");
		radBlkYes =  Jcomp.createRadio("Yes", "Only list hits in synteny blocks"); 	
		radBlkNo =   Jcomp.createRadio("No", "Only list hits that are not in a synteny block"); 	
		radBlkIgn =  Jcomp.createRadio("Ignore", "Does not matter if hit is in a block or not"); 
		
		brow.add(lblBlkOpts); 	
		if (Jcomp.isWidth(width, lblBlkOpts)) brow.add(Box.createHorizontalStrut(Jcomp.getWidth(width, lblBlkOpts)));
		brow.add(radBlkYes);  brow.add(Box.createHorizontalStrut(1));
		brow.add(radBlkNo);	brow.add(Box.createHorizontalStrut(1));
		brow.add(radBlkIgn);	brow.add(Box.createHorizontalStrut(1));
		
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
		
		String olap = (isMinorOlap) ? "Minor" : "Overlap"; // CAS560 -ii only
		radAnnoOlap 		= Jcomp.createRadio(olap, "Only " + olap + " genes"); 	
		
		arow.add(lblAnnoOpts); 	
		if (Jcomp.isWidth(width, lblAnnoOpts)) arow.add(Box.createHorizontalStrut(Jcomp.getWidth(width, lblAnnoOpts)));
		
		arow.add(radAnnoEvery);		brow.add(Box.createHorizontalStrut(1));
		arow.add(radAnnoEveryStar);	brow.add(Box.createHorizontalStrut(1)); // CAS549 works for algo1 now too
		arow.add(radAnnoOne);		brow.add(Box.createHorizontalStrut(1));
		arow.add(radAnnoBoth);		brow.add(Box.createHorizontalStrut(1));
		arow.add(radAnnoNone);		brow.add(Box.createHorizontalStrut(1));
		arow.add(radAnnoIgn);	brow.add(Box.createHorizontalStrut(1));
		if (Globals.INFO) {arow.add(radAnnoOlap);	brow.add(Box.createHorizontalStrut(1));} // CAS560 -ii
		
		ButtonGroup ag = new ButtonGroup();
		ag.add(radAnnoEvery); ag.add(radAnnoEveryStar); ag.add(radAnnoOne); ag.add(radAnnoBoth);
		ag.add(radAnnoOlap); ag.add(radAnnoNone); ag.add(radAnnoIgn);
		radAnnoIgn.setSelected(true);
		
		panel.add(arow); panel.add(Box.createVerticalStrut(5));
		
		// collinear
		JPanel crow = Jcomp.createRowPanel();;
		lblCoSetN = Jcomp.createLabel("Collinear size ", "Size of collinear set of genes");
		crow.add(lblCoSetN); 
		
		txtCoSetN = Jcomp.createTextField("0", 3); 
		crow.add(txtCoSetN); 
		int w = (int) (lblCoSetN.getPreferredSize().getWidth() + txtCoSetN.getPreferredSize().getWidth() - 1);
		if (width > w) crow.add(Box.createHorizontalStrut(width-w));
		
		radCoSetGE =  Jcomp.createRadio(">=", "Show collinear sets >= N"); 
		radCoSetEQ =  Jcomp.createRadio("=", "Show collinear sets = N");  
		radCoSetLE =  Jcomp.createRadio("<=" , "Show collinear sets <= N"); 
		radCoSetIgn = Jcomp.createRadio("Ignore"); 
		
		ButtonGroup g = new ButtonGroup();
		g.add(radCoSetGE); g.add(radCoSetEQ); g.add(radCoSetLE); g.add(radCoSetIgn);
		radCoSetIgn.setSelected(true);
		crow.add(radCoSetGE); crow.add(Box.createHorizontalStrut(1));
		crow.add(radCoSetEQ); crow.add(Box.createHorizontalStrut(1));
		crow.add(radCoSetLE); crow.add(Box.createHorizontalStrut(1));
		crow.add(radCoSetIgn);
		panel.add(crow); 
		
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
	private JPanel createFilterPairChkPanel() {
		JPanel panel = Jcomp.createPagePanelNB();
		JPanel row = Jcomp.createRowPanel();
		
		chkOnBlkNum = Jcomp.createCheckBox("Block#", "A single block number", true); 
		chkOnBlkNum.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setRadChkLoc(chkOnBlkNum.isSelected(), nChkBlk, SpeciesSelectPanel.locOnly);
			}
		});
		txtBlkNum = Jcomp.createTextField("", "Enter Block# only", 4, true); 
		row.add(chkOnBlkNum); row.add(Box.createHorizontalStrut(5));
		row.add(txtBlkNum);   row.add(Box.createHorizontalStrut(15));
		
		chkOnCoSetNum = Jcomp.createCheckBox("Collinear set#", "A single collinear set number", true); 
		chkOnCoSetNum .addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setRadChkLoc(chkOnCoSetNum.isSelected(), nChkCoSet, SpeciesSelectPanel.locOnly);
			}
		});
		txtCoSetNum  = Jcomp.createTextField("", "Enter Collinear# only", 4, true); 
		row.add(chkOnCoSetNum); row.add(Box.createHorizontalStrut(5));
		row.add(txtCoSetNum);   row.add(Box.createHorizontalStrut(15));
		
		chkOnHitNum = Jcomp.createCheckBox("Hit#", "A hit number (column Hit#)", true); 
		chkOnHitNum.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setRadChkLoc(chkOnHitNum.isSelected(), nChkHit, SpeciesSelectPanel.locOnly);
			}
		});
		txtHitNum = Jcomp.createTextField("", "Enter Hit# only", 4, true); 
		row.add(chkOnHitNum); row.add(Box.createHorizontalStrut(5));
		row.add(txtHitNum);   row.add(Box.createHorizontalStrut(15));
		
		chkOnGeneNum = Jcomp.createCheckBox("Gene#", "A gene# with or without a suffix", true);
		chkOnGeneNum.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setRadChkLoc(chkOnGeneNum.isSelected(), nChkGene, SpeciesSelectPanel.locChr);
			}
		});
		txtGeneNum = Jcomp.createTextField("", "Enter Gene# only", 4, true); 
		row.add(chkOnGeneNum); row.add(Box.createHorizontalStrut(5));
		row.add(txtGeneNum); 	
		
		row.setMaximumSize(row.getPreferredSize());
		row.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(row);
		
		return panel;
	}
	
	/** Filter Punitive gene family and Multi-hit **/
	private JPanel createFilterGroupPanel() {		
		JPanel panel = Jcomp.createPagePanelNB(); 	
	
		JPanel mrow = Jcomp.createRowPanel();
		chkOnMultiN = Jcomp.createCheckBox("Multi-hit Genes ", "Genes with multiple hits; sets Grp# and GrpSize columns", false);
		chkOnMultiN.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				if (chkOnMultiN.isSelected()) setChkEnable(nChkMulti);	// checks off except this one	
				else 						  setChkEnable(nChkNone);
				setRefresh();
		}});
		mrow.add(chkOnMultiN); 
		
		lblMultiN = Jcomp.createLabel(">=  "); // CAS548 add
		mrow.add(lblMultiN);  
		
		txtMultiN = Jcomp.createTextField(multiDefNum, 3); 
		mrow.add(txtMultiN); 
		mrow.add(Box.createHorizontalStrut(5));
		
		chkMultiMinor = Jcomp.createCheckBox("Minor*", "If checked, include minor gene hits", false);
		mrow.add(chkMultiMinor); 
		
		mrow.add(Box.createHorizontalStrut(6));
		lblOpposite = Jcomp.createLabel("  Opposite: ", "Opposite chromosome");
		mrow.add(lblOpposite);
		
		radMultiTandem = Jcomp.createRadio("Tandem", "If checked, the N hits for a gene must be to sequential genes");
		mrow.add(radMultiTandem); mrow.add(Box.createHorizontalStrut(1));
		
		radMultiSame = Jcomp.createRadio("Same Chr", "If checked, the N hits for a gene must be to the same opposite chromosome");
		mrow.add(radMultiSame); mrow.add(Box.createHorizontalStrut(1));
		
		radMultiDiff = Jcomp.createRadio("Diff Chr", "If checked, the N hits for a gene can be to any set of chromosomes");
		mrow.add(radMultiDiff); mrow.add(Box.createHorizontalStrut(1));
		
		ButtonGroup ag = new ButtonGroup();
		ag.add(radMultiDiff); ag.add(radMultiTandem); ag.add(radMultiSame); radMultiSame.setEnabled(true);
		
		panel.add(mrow); panel.add(Box.createVerticalStrut(5));
		
		/////////////
		Vector<Mproject> theProjects = theParentFrame.getProjects();
		chkOnPgeneF = Jcomp.createCheckBox("PgeneF (Putative gene families)", "Putative gene families; sets Grp# and GrpSize columns", false);
		chkOnPgeneF.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				if (chkOnPgeneF.isSelected()) 	setChkEnable(nChkPgene);
				else 							setChkEnable(nChkNone);
				setRefresh();
			}
		});
		chkOnPgeneF.setSelected(false);
		panel.add(chkOnPgeneF);
		
		speciesName = new String [theProjects.size()];
		incSpecies = new JCheckBox[theProjects.size()]; // vector of checkboxes
		for(int x=0; x<incSpecies.length; x++) {
			speciesName[x] = theProjects.get(x).getDisplayName();
			incSpecies[x] = Jcomp.createCheckBox(speciesName[x], false);
		}
		exSpecies = new JCheckBox[theProjects.size()];
		for(int x=0; x<exSpecies.length; x++) {
			exSpecies[x] = Jcomp.createCheckBox(speciesName[x], false);
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

			JPanel row = Jcomp.createRowPanel();
			lblInclude = Jcomp.createLabel("Include: ");
			row.add(lblInclude);
			for(int x=0; x<incSpecies.length; x++) {
				if(catName.equals(theProjects.get(x).getdbCat())) {
					if(firstOne) {
						panel.add(Box.createVerticalStrut(10));
						if(sortedGroups.size() > 1)
							panel.add(Jcomp.createLabel(catName.toUpperCase()));
						firstOne = false;
					}
					row.add(incSpecies[x]);
					row.add(Box.createHorizontalStrut(5));
				}
			}
			panel.add(row);
	
			row = Jcomp.createRowPanel();
			lblExclude = Jcomp.createLabel("Exclude: ");
			row.add(lblExclude);
			for(int x=0; x<exSpecies.length; x++) {
				if(catName.equals(theProjects.get(x).getdbCat())) {
					if(firstOne) {
						panel.add(Box.createVerticalStrut(10));
						if(sortedGroups.size() > 1)
							panel.add(Jcomp.createLabel(catName.toUpperCase()));
						firstOne = false;
					}
					row.add(exSpecies[x]);
					row.add(Box.createHorizontalStrut(5));
				}
			}
			if (nSpecies>2) panel.add(row);
		}
		
		chkUnannotInc = Jcomp.createCheckBox("No annotation to included species", false);
		chkLinkageInc = Jcomp.createCheckBox("Complete linkage of included species", false);
		chkOnlyInc = 	Jcomp.createCheckBox("At least one hit for each included species", false);
		
		panel.add(Box.createVerticalStrut(10));
		lblMustHave = Jcomp.createLabel("PgeneF must have: ");
		panel.add(lblMustHave); 		panel.add(Box.createVerticalStrut(2));
		panel.add(chkUnannotInc); 		panel.add(Box.createVerticalStrut(2));
		if (nSpecies>2) {
			panel.add(chkLinkageInc);	panel.add(Box.createVerticalStrut(2));
			panel.add(chkOnlyInc);
		}
		panel.setMaximumSize(panel.getPreferredSize());
	
		return panel;
	}
	/**************************************************************
	 * XXX Clears and enables;
	 */
	private void setClearDefaults() {
		// Anno/Chr 
		txtAnno.setText(""); txtAnno.setEnabled(true); annoLabel.setEnabled(true);
		speciesPanel.setClear();  
		
		// PairRad
		setRadEnable(true);
		radBlkIgn.setSelected(true);   radAnnoIgn.setSelected(true);  radCoSetIgn.setSelected(true); 
		if (txtCoSetN.getText().isEmpty()) txtCoSetN.setText(coDefNum); 
		
		// PairChk and Group 
		setChkEnable(nChkNone);
		txtCoSetNum.setText(""); txtBlkNum.setText(""); txtHitNum.setText(""); 	txtGeneNum.setText(""); 	
		if (txtMultiN.getText().isEmpty()) txtMultiN.setText(multiDefNum); 
		radMultiSame.setSelected(true);
		
		setRefresh();
	}
	private void setChkEnable(int nChk) { // All checks and clear
		boolean b, bn = (nChk==nChkNone);
		
		b = (nChk==nChkSingle);
		chkOnSingle.setEnabled(bn || b); chkOnSingle.setSelected(b);
		radOrphan.setEnabled(b);  radSingle.setEnabled(b);
		
		b = (nChk==nChkBlk);
		chkOnBlkNum.setEnabled(bn || b);  chkOnBlkNum.setSelected(b);    
		txtBlkNum.setEnabled(b); 	
		
		b = (nChk==nChkCoSet);
		chkOnCoSetNum.setEnabled(bn || b); chkOnCoSetNum.setSelected(b);  
		txtCoSetNum.setEnabled(b); 	
		
		b = (nChk==nChkHit);
		chkOnHitNum.setEnabled(bn || b);  chkOnHitNum.setSelected(b);    
		txtHitNum.setEnabled(b);	
		
		b = (nChk==nChkGene);
		chkOnGeneNum.setEnabled(bn || b);  chkOnGeneNum.setSelected(b);   
		txtGeneNum.setEnabled(b);	
		
		b = (nChk==nChkMulti);
		chkOnMultiN.setEnabled(bn || b);  chkOnMultiN.setSelected(b);  
		lblMultiN.setEnabled(b);    txtMultiN.setEnabled(b); 
		lblOpposite.setEnabled(b);  chkMultiMinor.setEnabled(b); 
		radMultiSame.setEnabled(b);  radMultiTandem.setEnabled(b); radMultiDiff.setEnabled(b);
		
		b = (nChk==nChkPgene);
		chkOnPgeneF.setEnabled(bn || b);  chkOnPgeneF.setSelected(b); 
		chkLinkageInc.setEnabled(b); chkUnannotInc.setEnabled(b); chkOnlyInc.setEnabled(b);
		lblInclude.setEnabled(b); lblExclude.setEnabled(b); lblMustHave.setEnabled(b);
		
		for(int x=0; x<exSpecies.length; x++)  exSpecies[x].setEnabled(b);
		for(int x=0; x<incSpecies.length; x++) incSpecies[x].setEnabled(b);
	}
	private void setRadEnable(boolean b) { // Single, Block, Hit, CoSet, Gene and Clear 
		lblBlkOpts.setEnabled(b); radBlkYes.setEnabled(b);radBlkNo.setEnabled(b);radBlkIgn.setEnabled(b);
		
		lblAnnoOpts.setEnabled(b); radAnnoEvery.setEnabled(b);radAnnoEveryStar.setEnabled(b);
		radAnnoOne.setEnabled(b);  radAnnoBoth.setEnabled(b); radAnnoOlap.setEnabled(b); 
		radAnnoNone.setEnabled(b); radAnnoIgn.setEnabled(b);
		
		lblCoSetN.setEnabled(b);  txtCoSetN.setEnabled(b); 	
		radCoSetGE.setEnabled(b); radCoSetEQ.setEnabled(b);
		radCoSetLE.setEnabled(b); radCoSetIgn.setEnabled(b);
		txtCoSetN.setEnabled(!radCoSetIgn.isSelected());
	}
	// Everything necessary of Single, Block, Hit, Collinear, Gene checks
	private void setRadChkLoc(boolean isSel, int nChk, int type) {
		if (isSel) setChkEnable(nChk);
		else       setChkEnable(nChkNone);
			
		setRadEnable(!isSel); 
	
		if (nChk!=nChkSingle) {
			annoLabel.setEnabled(!isSel); 
			txtAnno.setEnabled(!isSel); 
		}
		speciesPanel.setChkEnable(isSel, type);
		
		setRefresh();
	}
	// CAS561 - I change some stuff around and all of a sudden the panels do not repaint right on enable,
	// This fixed it, hopefully once and for all.
	private void setRefresh() {
		searchPanel.revalidate(); searchPanel.repaint();
		singlePanel.revalidate(); singlePanel.repaint();
		pairRadPanel.revalidate(); pairRadPanel.repaint();
		pairChkPanel.revalidate(); pairChkPanel.repaint();
		groupPanel.revalidate(); groupPanel.repaint();
	}
	protected void showWarnMsg(String msg) {// CAS561 the Utilities one prints to stdout
		JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE);
	}
	private int getValidNum(String text) {
		try {
			return Integer.parseInt(text);
		}
		catch (Exception e) {return -1;}
	}
     /************************************************************
      * Rules: chkOn enables some and disables others
      *******************************************************/
	JPanel buttonPanel, searchPanel, singlePanel, pairRadPanel, pairChkPanel, groupPanel;
	private JTextField txtAnno = null; // Used with all but HitNum
	private JLabel annoLabel = null;
	
	protected SpeciesSelectPanel speciesPanel = null;	
	protected int nSpecies=0;
	private HashMap <Integer, String> grpCoords = new HashMap <Integer, String> ();
	
	// FilterSingle
	private JCheckBox chkOnSingle = null; 
	private JRadioButton radOrphan = null, radSingle=null;
		
	// FilterPairRad
	private JLabel lblBlkOpts = null;	
	private JRadioButton radBlkYes, radBlkNo, radBlkIgn;
	
	private JLabel lblAnnoOpts = null;
	private JRadioButton radAnnoEvery, radAnnoEveryStar, radAnnoOne, radAnnoBoth,  radAnnoNone, 
			radAnnoOlap, radAnnoIgn;	// CAS560 add Olap/Minor for -ii 
	
	private JTextField txtCoSetN = null; 
	private JLabel     lblCoSetN = null;
	private JRadioButton radCoSetGE, radCoSetEQ, radCoSetLE, radCoSetIgn;
	
	// FilterPairChk
	private JCheckBox  chkOnBlkNum = null, chkOnCoSetNum = null, chkOnHitNum = null, chkOnGeneNum = null;
	private JTextField txtBlkNum = null, txtCoSetNum = null, txtHitNum = null, txtGeneNum = null;
	
	// FilterGroup
	private JCheckBox chkOnMultiN = null;
	private JTextField txtMultiN = null;// CAS548 add multi
	private JLabel     lblMultiN = null, lblOpposite = null;
	private JRadioButton  radMultiSame=null, radMultiTandem=null, radMultiDiff=null;
	private JCheckBox  chkMultiMinor = null;
	
	private JCheckBox chkOnPgeneF = null;
	private JCheckBox [] incSpecies = null, exSpecies = null;
	private String [] speciesName = null;
	private JCheckBox chkOnlyInc = null, chkUnannotInc = null, chkLinkageInc = null;
	private JLabel    lblInclude = null, lblExclude = null, lblMustHave = null;
	
	private boolean bValidQuery=true;
	
	private JButton btnExecute = null;
	
	// Not big enough to bother adding buttons to pnlStepOne.expand(); pnlStepOne.collapse()
	private CollapsiblePanel pnlStep1 = null, pnlStep2 = null, pnlStep3 = null, pnlStep4 = null; 
	private SyMAPQueryFrame theParentFrame = null;
}
