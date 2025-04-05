package symapQuery;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import symap.Globals;
import util.ErrorReport;
import util.Jcomp;
import util.Jhtml;
import util.Utilities;

/***********************************************
 * Create report of collinear genes or genes matches
 * CAS562 rewrote union logic so unions are exact groups to show. Add TSV, Exact, Union header
 * CAS563 add links; make Output a class and merge some TSV/HTML methods
 * 	Cluster was too weird being reference based, so created UtilReportNR to simply create non-ref report
 *  Renamed from TableReport.
 */
public class UtilReport extends JDialog {
	private static final long serialVersionUID = 1L;
	private final int defTxtWidth=40, padWithSpace=10;
	private static String filePrefix = "Report";
	
	private static final String splitC = ";", 	// local delimiter for lists
								keyC = ",", 	// input key parameter
								semiC = ";",	// fixed delimiter for output
								keyVal = "=",	// between keyword and value
								sDot   = "\\.", // split chr.gene#
								keyDot = "."; 	// use for hash key
	private static final String isRef="*", 		// used in TSV for reference header; HTML uses italics
								isAll="+", 		// no all species per row/union, add this to show all species
								isCoSet="#", 	// Coset
								isMulti="#";	// Multi
	private static final String isLink="=", 	// Gene: linkage with direct neighbor
							    isLink3="+", 	// Gene: linkage with one over
							    isNone="---",
							    htmlSp="&nbsp;", htmlBr="<br>", tsvBr = "| ";			
	private String brLine;	// Between Keyword values and Merge sets: Set in okay: "|" tsv, <br> html
	
	// Okay: From input menu: the setting are used as the defaults, except bIs settings
	// Object is reused - so reset anything not wanted from last time
	private boolean bIsGene1=false, bIsCoSet2=false, bIsMulti3=false; // the 4 types of reports
	
	// Default values the first time; set like this in clear()
	private boolean bPopHtml=true, bExHtml=false, bExTsv=false;
	private boolean bAllSpRow13=false,  // Gene, Multi: all species must be in each row; must be false for species=2
					bLinkRow1=false,    // Gene: all species and at least one link
					bAllLinkRow1=false, // Gene: complete graph of linked hits
					bAllSpIgn13=false,	// Gene, Multi: print all, add link if Gene && bShowLink1
					bShowLink1=true,    // Gene: show links
					bAllUnion2=true,	// CoSet: all species must be in the union
					bShowNum23=true,    // CoSet & Multi: show the collinear/group set number
					bShowBorder=true, 	// All: html table border
					bTruncate=false, 	// All: Truncate description
					bShowGene=false;	// All: Show gene# before anno
	
	private int queryMultiN3=0;		// the cutoff for Multi-hit gene; not all Multi-hit are to genes, report requires N gene pairs
	private int descWidth=40;
	private boolean hasKey=false;

	private int refPos=0;			// posRef is the index into Gene.oTag array for reference
	private int refSpIdx=0;			
	
	private PrintWriter outFH = null;
	private String title;
	private String refSpName="", refSpAbbr="";     // for trace output and file name
	private String note="";						   // put in header (only used by Clust)
	
	// Anno Keys:
	private int nSpecies=0;														  //  createSpecies
	private HashMap <Integer, Integer> spIdxPosMap = new HashMap <Integer, Integer> (); // idx, pos
	private HashMap <Integer, Integer> spPosIdxMap = new HashMap <Integer, Integer> (); // pos, idx
	private String [] spInputKey; 												  // okay
	// buildCompute puts genes in map; buildXannoMap() add annotation
	private HashMap <Integer, Anno> spAnnoMap = new HashMap <Integer, Anno> (); 
	
	// build and output
	private ArrayList <Union> unionVec = new ArrayList <Union> ();
	private ArrayList <Gene> geneVec = new ArrayList <Gene> ();
	
	// CoSet: each gene points to its Union, which contains Collinear sets; see buildXunion: 
	//  CoSet key = rd.chrIdx[i] + "." + rd.chrIdx[j] + "." + rd.collinearN; 
	//  if bCoSetAll key = rd.spIdx[j] . rd.chrIdx[i] + "." + rd.chrIdx[j] + "." + rd.collinearN; 
	// 	            need spIdx to count species in the union
	// GroupN: see buildCompute
	//	tmpRefMap key; a Gene# can be in the table multiple times, hence, the search key is "tag grpN"
	/***************************************************************************/
	protected UtilReport(TableMainPanel tdp) { // this is called 1st time for Query
		this.tdp = tdp;
		spPanel = tdp.queryPanel.speciesPanel;
		
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		
		if (tdp.isCollinear)   {bIsCoSet2=true; title="Collinear Genes";}
		else if (tdp.isMultiN) {bIsMulti3=true; title="Multi-hit Genes"; }
		else 				   {bIsGene1 =true; title="Gene Pair";}
		
		title = title + " Report";
		setTitle(title);
		title = "SyMAP " + title;

		mainPanel = Jcomp.createPagePanel();
		createSpecies();
		createDisplay();
		createOutput();
		
		pack();
		
		setModal(true); // nothing else can happen until ok,cancel
		toFront();
		setResizable(false);
		setLocationRelativeTo(null); 
	}
	
	// Reference and keywords
	private void createSpecies() {
		int width = 100;
		JPanel optPanel = Jcomp.createPagePanel();
		
		nSpecies = tdp.queryPanel.nSpecies;
		
		radSpecies = new JRadioButton[nSpecies];
		txtSpKey =   new JTextField[nSpecies];
		
		ButtonGroup bg = new ButtonGroup();
		
		JPanel row = Jcomp.createRowPanel();
		JLabel label = Jcomp.createLabel("Reference");
		row.add(label);
		if (Jcomp.isWidth(width, label)) row.add(Box.createHorizontalStrut(Jcomp.getWidth(width, label)));
		
		row.add(Jcomp.createLabel("Gene Annotation Columns", 
				"Enter comma-delimited list of keywords from All_Anno column for one or more species."));
		optPanel.add(row);
		
		// width may change if longer species name; CAS563 added to make constant width columns
		for(int x=0; x<nSpecies; x++) {
			radSpecies[x] = Jcomp.createRadio(spPanel.getSpName(x)); 
			width = Math.max(width, radSpecies[x].getPreferredSize().width);
			bg.add(radSpecies[x]);
		}
		
		for(int pos=0; pos<nSpecies; pos++) {
			row = Jcomp.createRowPanel();
			row.add(radSpecies[pos]); 
			if (Jcomp.isWidth(width, radSpecies[pos])) row.add(Box.createHorizontalStrut(Jcomp.getWidth(width, radSpecies[pos])));
			
			txtSpKey[pos] = Jcomp.createTextField("",18);
			row.add(txtSpKey[pos]);
			
			optPanel.add(row);
			
			int spIdx = spPanel.getSpIdx(pos);
			spIdxPosMap.put(spIdx, pos);
			spPosIdxMap.put(pos, spIdx);
		}
		radSpecies[refPos].setSelected(true);
		
		JPanel descPanel = Jcomp.createRowPanel();
		descPanel.add(Box.createHorizontalStrut(width));
		
		txtDesc  = Jcomp.createTextField(descWidth+"",3);
		chkTrunc = Jcomp.createCheckBox("Truncate", "Truncate description to value Width", false);
		chkShowGene = Jcomp.createCheckBox("Gene#", "If selected, the Gene# will proceed the annotation", bShowGene); 	
		
		descPanel.add(Jcomp.createLabel("Width", "Length of Gene Annotation Column"));
		descPanel.add(Box.createHorizontalStrut(1));
		descPanel.add(txtDesc); descPanel.add(Box.createHorizontalStrut(3));
		descPanel.add(chkTrunc); descPanel.add(Box.createHorizontalStrut(3));
		descPanel.add(chkShowGene);
		optPanel.add(descPanel);
			
		mainPanel.add(optPanel);
		mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
	}
	private void createDisplay() {
		radAllSpRow   = Jcomp.createRadio("Per Row", "Each row must have all species"); 
		radLinkRow    = Jcomp.createRadio("+Link", "Each row must have all species and at least one link"); 
		radAllLinkRow= Jcomp.createRadio("All Link", "Each row must have all genes linked (joined by a hit)");
		radAllSpUnion = Jcomp.createRadio("Per Union", "Union of overlapping collinear sets must have all species"); 
		radAllSpIgn   = Jcomp.createRadio("Ignore", "Show everything, regardless of missing species"); 
		
		chkShowLinks = Jcomp.createCheckBox("Links","If selected, a '=' will indicate hits between non-ref genes", bShowLink1); 
		if (bIsCoSet2) chkShowNum = Jcomp.createCheckBox("Collinear", "If selected, the collinear set# will be shown", bShowNum23); 	
		else 		   chkShowNum = Jcomp.createCheckBox("Group", "If selected, the group size:num will be shown", bShowNum23); 	
		chkBorder = Jcomp.createCheckBox("Border", "If selected, the HTML table will contain borders around rows", bShowBorder); 	
		
		JPanel optPanel = Jcomp.createPagePanel();
		JLabel asLabel = Jcomp.createLabel("All species");
		int width = asLabel.getPreferredSize().width;
		
		if (nSpecies>2) {
			optPanel.add(Box.createVerticalStrut(5));
			if (bIsGene1) {
				JPanel row1 = Jcomp.createRowPanel();
				ButtonGroup ba = new ButtonGroup();
				
				row1.add(asLabel); 	row1.add(Box.createHorizontalStrut(1));
				row1.add(radAllSpRow); ba.add(radAllSpRow); row1.add(Box.createHorizontalStrut(3));
				row1.add(radLinkRow);  ba.add(radLinkRow);  row1.add(Box.createHorizontalStrut(3));
				if (nSpecies<5) {
					row1.add(radAllLinkRow);  ba.add(radAllLinkRow);  row1.add(Box.createHorizontalStrut(3));
				}
				row1.add(radAllSpIgn); ba.add(radAllSpIgn);	radAllSpIgn.setSelected(true);
				optPanel.add(row1); optPanel.add(Box.createVerticalStrut(5));
			}
			else if (bIsCoSet2) {
				JPanel row1 = Jcomp.createRowPanel();
				ButtonGroup ba = new ButtonGroup();
				
				row1.add(asLabel); 								row1.add(Box.createHorizontalStrut(1));
				row1.add(radAllSpUnion); ba.add(radAllSpUnion); row1.add(Box.createHorizontalStrut(3));
				row1.add(radAllSpIgn);   ba.add(radAllSpIgn);	radAllSpIgn.setSelected(true);
				optPanel.add(row1);
			}
			else if (bIsMulti3) {
				JPanel row1 = Jcomp.createRowPanel();
				ButtonGroup ba = new ButtonGroup();
				row1.add(asLabel); 	row1.add(Box.createHorizontalStrut(1));
				row1.add(radAllSpRow); ba.add(radAllSpRow); 		row1.add(Box.createHorizontalStrut(3));
				row1.add(radAllSpIgn); ba.add(radAllSpIgn);			radAllSpIgn.setSelected(true);
				optPanel.add(row1); optPanel.add(Box.createVerticalStrut(5));
			}
		} 
		optPanel.add(Box.createVerticalStrut(2));
		
		JPanel row2 = Jcomp.createRowPanel();
		JLabel sh = Jcomp.createLabel("Show  ");
		row2.add(sh); 
		if (nSpecies>2) row2.add(Box.createHorizontalStrut(width - sh.getPreferredSize().width));
		else            row2.add(Box.createHorizontalStrut(4));
		row2.add(chkBorder); row2.add(Box.createHorizontalStrut(3));
		if (bIsGene1 && nSpecies>2) row2.add(chkShowLinks);
		if (bIsCoSet2 || bIsMulti3) row2.add(chkShowNum);
		optPanel.add(row2);
		
		mainPanel.add(optPanel);
		mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());	
	}
	// output and bottom buttons
	private void createOutput() {
		JPanel optPanel = Jcomp.createPagePanel();
		
		JPanel row = Jcomp.createRowPanel();
		row.add(Jcomp.createLabel("Output")); row.add(Box.createHorizontalStrut(5));
		
		btnPop = Jcomp.createRadio("Popup", "Popup of results"); 
		btnPop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				bPopHtml = btnPop.isSelected();
				if (bPopHtml) bExHtml = bExTsv = false;
			}
		});
		row.add(btnPop); row.add(Box.createHorizontalStrut(5));
		
		JRadioButton btnHTML =  Jcomp.createRadio("HTML File", "HTML file of results");
		btnHTML.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				bExHtml = btnHTML.isSelected();
				if (bExHtml) bPopHtml = bExTsv = false;
			}
		});
		row.add(btnHTML); row.add(Box.createHorizontalStrut(5));
		
		JRadioButton btnTSV =  Jcomp.createRadio("TSV File", "File of tab separated values");
		btnTSV.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				bExTsv = btnTSV.isSelected();
				if (bExTsv) bPopHtml = bExHtml = false;
			}
		});
		row.add(btnTSV); row.add(Box.createHorizontalStrut(5));
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(btnHTML); grp.add(btnPop);   grp.add(btnTSV);
    	btnPop.setSelected(true);

        optPanel.add(row); 
        	
    // buttons
    	btnOK = Jcomp.createButton("Create", "Create gene pair report");
    	btnOK.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				okay();
			}
		});
		btnCancel = Jcomp.createButton(Jcomp.cancel, "Cancel action");
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
		
		btnClear = Jcomp.createButton("Clear", "Clear values and reset defaults");
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				clear();
				
			}
		});
		
		btnInfo = Jcomp.createIconButton("/images/info.png", "Quick Help Popup");
		btnInfo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				popupHelp();
			}
		});
		
		JPanel buttonPanel = Jcomp.createRowPanel();
		buttonPanel.add(Box.createHorizontalStrut(25));
		buttonPanel.add(btnOK);			buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnCancel);		buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnClear);		buttonPanel.add(Box.createHorizontalStrut(20));
		buttonPanel.add(btnInfo);
		
		mainPanel.add(optPanel); 	mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
		mainPanel.add(buttonPanel);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		add(mainPanel);
	}
	private void popupHelp() {
		String msg = 
				"Reference: List all reference gene pairs from the table.";
		msg +=  "\n\nBy default, only the Gene# will be shown."
				+ "\nEnter a comma-separated list of keyword found in the 'All_Anno' column; "
				+ "\n   a column in the resutling table will be listed with the values."
				+ "\n   The keywords are NOT checked for correctness.";
		msg +=  "\n\nThere will be a extra options if there are >2 species being displayed."
				+ "\n   Hover over an option for more details.";
		msg += "\n\nSee ? for details.\n";
		
		util.Utilities.displayInfoMonoSpace(this, "Quick Help", msg, false);
	}
	/**************************************************************8
	 * Shared
	 */
	private void okay() {
	/*-------- Settings ---------*/
		spInputKey = new String [nSpecies];				// List of input keywords per species
		for (int i=0; i<nSpecies; i++) spInputKey[i]=""; 
		for (int x=0; x<nSpecies; x++) { 
			if (radSpecies[x].isSelected()) refPos=x; 	// set position in bGene tagLists that is ref
			
			spAnnoMap.put(x, new Anno()); 				// in buildCompute add gene; in buildXannoMap add anno
			
			spInputKey[x] = txtSpKey[x].getText().trim(); 
			if (spInputKey[x].contains(" ") && !spInputKey[x].contains(keyC)) {
				Utilities.showInfoMessage("Bad Key", "Keys must be separated by ',' and no space within a key '" + spInputKey[x] + "'");
				return;
			}
			if (!spInputKey[x].isEmpty()) hasKey=true;	// if any have key
		}
		refSpName = spPanel.getSpName(refPos); // use in report title
		refSpAbbr = spPanel.getSpAbbr(refPos); // used in file name
		refSpIdx  = spPosIdxMap.get(refPos);
		
		// Other settings
		String txt = txtDesc.getText();
		try {descWidth = Integer.parseInt(txt);}
		catch (Exception e){
			descWidth = defTxtWidth;
			JOptionPane.showMessageDialog(null, "Incorrect desc width number: '" + txt + "'", "Incorrect width", JOptionPane.WARNING_MESSAGE);
			return;
		}
		bTruncate    = chkTrunc.isSelected();
		bShowGene    = chkShowGene.isSelected();
		bShowBorder  = chkBorder.isSelected();
		bAllSpRow13  = radAllSpRow.isSelected();
		bLinkRow1 	 = radLinkRow.isSelected();
		bAllLinkRow1 = radAllLinkRow.isSelected();
		bAllSpIgn13	 = radAllSpIgn.isSelected();
		if (bLinkRow1 || bAllLinkRow1) bAllSpRow13 = true;
		
		bAllUnion2   = radAllSpUnion.isSelected(); 
		bShowNum23   = chkShowNum.isSelected();
		bShowLink1   = chkShowLinks.isSelected();
		queryMultiN3 = tdp.queryPanel.getMultiN();
		
		brLine = (bExTsv) ? tsvBr : htmlBr;
			
	/*-------- Build report and display ---------*/
		setVisible(false);	// can do other things if set false here
		
		Thread inThread = new Thread(new Runnable() {
    		public void run() {
			try {
				tdp.setBtnReport(false);
				btnOK.setEnabled(false); btnCancel.setEnabled(false); 
				
				buildCompute();
				
				if (geneVec.size()==0) {
					String msg = "No rows with both genes for reference " + refSpName;
					JOptionPane.showMessageDialog(null, msg, "No results", JOptionPane.WARNING_MESSAGE);
				}
				else {
					new Zoutput().runReport();
				}
				
				btnOK.setEnabled(true); btnCancel.setEnabled(true); 
				tdp.setBtnReport(true);
			} catch(Exception e) {ErrorReport.print(e, "Ok report");}
	    	}
	    });
	    inThread.start();
	}
	private void clear() {
		radSpecies[0].setSelected(true);
		for (int i=0; i<nSpecies; i++) txtSpKey[i].setText("");
		
		for (JTextField t : txtSpKey) t.setText("");
		txtDesc.setText(defTxtWidth+""); 
		chkTrunc.setSelected(false);
		chkShowGene.setSelected(false);
		
		chkBorder.setSelected(true);
		chkShowNum.setSelected(true);
		
		radAllSpIgn.setSelected(true);
		
		btnPop.setSelected(true);
	}
	/***********************************************************
	 * XXX Used by all output to build rows of the report
	 */
	private void buildCompute() {
	try {	
		note = ""; // may have been used last time
		// tmpRowVec: Get all rows with both genes and Ref is one of them
		ArrayList <TmpRowData> rowDataVec = new ArrayList <TmpRowData>  ();
		for (int row=0; row<tdp.theTableData.getNumRows(); row++) {
			TmpRowData trd = new TmpRowData(tdp);
			trd.loadRow(row);
			
			if (!trd.geneTag[0].contains(Q.dash) && !trd.geneTag[1].contains(Q.dash)) {
				if (trd.spIdx[0]==refSpIdx || trd.spIdx[1]==refSpIdx) {
					rowDataVec.add(trd);	
				}
			}
		}
		if (rowDataVec.size()==0) return;
		
		// build geneVec from rowDataVec: create Gene object from reference genes
		HashMap <String, Gene> tmpRefMap = new HashMap <String, Gene> (); // tag to gene 
		
		for (TmpRowData rd : rowDataVec) { 	// CAS563 nonRef was added separately
			int i = -1;						// if >2 species and a middle one selected, may be in 1st or 2nd column
			if      (rd.spIdx[0]==refSpIdx)  i=0;
			else if (rd.spIdx[1]==refSpIdx)  i=1;	
			
			String refTag = (bIsMulti3) ? (rd.geneTag[i] + " " + rd.groupN) : rd.geneTag[i]; // TmpRef key
			
			Gene gnObj=null;
			if (!tmpRefMap.containsKey(refTag)) {
				gnObj = new Gene(refPos, rd.geneTag[i],  rd.groupN, rd.groupSz); 
				geneVec.add(gnObj);				// gene can be in geneVec multiple times with different groups
				tmpRefMap.put(refTag, gnObj);
				
				if (hasKey && !spInputKey[refPos].isEmpty()) { // All geneVec get put in nAnnoMap
					HashMap <String, String> nAnnoMap = spAnnoMap.get(refPos).nAnnoMap;
					nAnnoMap.put(rd.geneTag[i], ""+rd.nRow); // nRow will be replaced with Anno from nRow
				}
			}
			else gnObj = tmpRefMap.get(refTag);
			
			// Add other half
			int j = (i==0) ? 1 : 0;
			int posNon = spIdxPosMap.get(rd.spIdx[j]);
			gnObj.addNonRef(posNon, rd.geneTag[j], rd.collinearSz, rd.collinearN, rd.groupN);
			
			if (hasKey && !spInputKey[posNon].isEmpty()) {
				HashMap <String, String> nAnnoMap = spAnnoMap.get(posNon).nAnnoMap;
				if (!nAnnoMap.containsKey(rd.geneTag[j]))
					nAnnoMap.put(rd.geneTag[j],""+rd.nRow);
			}
		}
		tmpRefMap.clear();
		if (geneVec.size()==0) return;
		
		// geneVec: sort by chr, by geneNum, by suffix
		Collections.sort(geneVec, new Comparator<Gene> () {
			public int compare(Gene a, Gene b) {
				if (a.rGrpN!=b.rGrpN) return a.rGrpN-b.rGrpN;	// CAS563 stop for groupN, start for groupP
				if (!a.rChr.equals(b.rChr)) {
					int c1=0, c2=0;
					try {
						c1 = Integer.parseInt(a.rChr);
						c2 = Integer.parseInt(b.rChr);
					}
					catch (Exception e) {c1=c2=0;}
					
					if (c1==0) return a.rChr.compareTo(b.rChr);
					else return c1-c2;
				}
				if (a.rGeneNum==b.rGeneNum) return a.rSuffix.compareTo(b.rSuffix);
				else 				  return a.rGeneNum-b.rGeneNum;
			}
		});
				
		for (Gene gn : geneVec) gn.sortTags();
			
		if (bIsCoSet2)      buildXcoSet(rowDataVec);// create Unions of coSets; needs original rows with ref
		else if (bIsMulti3) buildXmulti(); 		    // Merge rows 
		else 				buildXrow();
		
		rowDataVec.clear();
		
		if (hasKey)  buildXannoMap();		 	   // Annotations from 1st loop
	} 
	catch (Exception e) {ErrorReport.print(e, "Build Gene Vec"); }
	}
	/*********************************************************
	 * Rows - set links and filter;
	 * 		all filtering is done here to by setting cntHits=0 if it is to be filtered
	 */
	private void buildXrow() {
		try {
		/*-- filter 1: all species --*/
			if (!bAllSpIgn13) {
				for (Gene refGn: geneVec) {
					if (refGn.cntSp != nSpecies) { 
						refGn.cntHits=0;
						continue;
					}
				}
			}
			if ((!bShowLink1 && bAllSpIgn13) || nSpecies==2) return;	
		
		/*-- Add links --*/
			// Union per non-ref gene; contains ugeneVec of Refs that it is linked to
			HashMap <String, Union> nrTagUnMap = new HashMap <String, Union> ();
			for (Gene refGn: geneVec) {
				for (int pos=0; pos<nSpecies; pos++) { // get all gene hits to this refGn
					if (pos==refPos) continue;
					if (refGn.cntHits==0) continue;    // filter 1
					
					String [] tagList = refGn.oTagList[pos].split(splitC);
					for (String tag : tagList) {
						String key = pos + keyDot + tag; 		// sp.chr.gene#
						Union unObj = (nrTagUnMap.containsKey(key)) ? nrTagUnMap.get(key) : new Union();
						unObj.ugeneVec.add(refGn); // add refGn to then non-ref gene that it hits
						
						if (!nrTagUnMap.containsKey(tag)) nrTagUnMap.put(key, unObj);
					}
				}
			}
			// Using original rows, find links between non-ref
			int cntLinks=0;
			for (int row=0; row<tdp.theTableData.getNumRows(); row++) { 
				TmpRowData trd = new TmpRowData(tdp);
				trd.loadRow(row);
				
				if (trd.geneTag[0].endsWith(Q.dash) || trd.geneTag[1].endsWith(Q.dash)) continue;
				if (trd.spIdx[0]==refSpIdx          || trd.spIdx[1]==refSpIdx) continue;
				
				int pos0 = spIdxPosMap.get(trd.spIdx[0]), pos1 = spIdxPosMap.get(trd.spIdx[1]);
				
				String tag0 = trd.geneTag[0], tag1 = trd.geneTag[1];
				String key0 = pos0 + keyDot + tag0,  key1 = pos1 + keyDot + tag1;
	
				if (!nrTagUnMap.containsKey(key0) || !nrTagUnMap.containsKey(key1)) continue;
					
				// Find shared refGn, if one exists
				Union unObj0 = nrTagUnMap.get(key0), unObj1 = nrTagUnMap.get(key1);
				
				Gene refGn=null;
				for (Gene gn0 : unObj0.ugeneVec) {
					for (Gene gn1 : unObj1.ugeneVec) {
						if (gn0==gn1) {
							refGn=gn0;
							break;
						}
					}
					if (refGn!=null) break;
				}
				if (refGn==null) continue;	// no shared gene
				
				// Find the two non-ref genes
				String [] tagList0 = refGn.oTagList[pos0].split(splitC);
				String [] tagList1 = refGn.oTagList[pos1].split(splitC);
				
				// Add link to each in the gn taglists
				int i0 = -1, i1 = -1;
				for (int i=0; i<tagList0.length; i++) {
					String tag = tagList0[i];
					if (tag.equals(tag0)) {
						i0 = i;
					}
					else if (tag.contains(isLink) || tag.contains(isLink3)) { // added a link already
						tag = tag.replace(isLink, ""); tag = tag.replace(isLink3, "");
						
						if (tag.equals(tag0)) i0 = i;
					}
				}
				if (i0!=-1) { 
					for (int i=0; i<tagList1.length; i++) {
						String tag = tagList1[i];
						if (tag.equals(tag1)) {
							i1 = i;
						}
						else if (tag.contains(isLink) || tag.contains(isLink3)) {
							tag = tag.replace(isLink, ""); tag = tag.replace(isLink3, "");
							
							if (tag.equals(tag1)) i1 = i;
						}
					}
				}
				// rebuild lists with extra isLink symbol
				if (i0==-1 || i1==-1) {
					Globals.prt(tag0 + " " + tag1 + " share " + refGn.rTag + " but not linked");
					continue;
				}
				// add link and rebuild list
				String link = isLink;
				if (nSpecies>=4 && pos1-pos0!=1) {
					if (refPos==0 || refPos==3) link = isLink3;
					else if (pos1-pos0>2) link = isLink3; // jump over refPos 
				}
				
				tagList0[i0] = tagList0[i0] + link;
				refGn.oTagList[pos0] = "";
				for (String t0 : tagList0) refGn.oTagList[pos0] += t0 + splitC;
				
				tagList1[i1] = link + tagList1[i1];
				refGn.oTagList[pos1] = "";
				for (String t1 : tagList1) refGn.oTagList[pos1] += t1 + splitC;				
				cntLinks++;
			} // end loop through DBdata rows
			nrTagUnMap.clear();
			note = String.format("%,d Links", cntLinks);
			
	/*-- Filter2 - every gene must have a least one link; can check oTagList string w/o splitting --*/
			if (bLinkRow1 || bAllLinkRow1) {
				int cnt1to1=0, cntHard=0;
				
				for (Gene refGn: geneVec) {
					if (refGn.cntHits==0) continue;	// Filter1 failed
					
					for (int pos=0; pos<nSpecies; pos++) { 
						if (pos==refPos) continue;
						
						boolean    bHas = refGn.oTagList[pos].contains(isLink);
						if (!bHas) bHas = refGn.oTagList[pos].contains(isLink3);

						if (!bHas) {
							refGn.cntHits=0;
							break;
						}
					}
					if (refGn.cntHits>0) {
						if (refGn.cntHits==nSpecies-1) cnt1to1++;
						else cntHard++;
					}
				}
				note = String.format("%,d single; %,d multi", cnt1to1, cntHard);
			}	
			if (!bAllLinkRow1) return;
		
	/* -- Filter 3: Each gene must link to at least one gene in each cell ------- */
			int cnt1to1=0, cntHard=0;
			for (Gene refGn: geneVec) {
				if (refGn.cntHits==0) continue;	// already failed number of species test
				
				for (int pos=0; pos<nSpecies; pos++) { 
					if (pos==refPos) continue;
					
					String [] tagList = refGn.oTagList[pos].split(splitC);
					for (String tag : tagList) {
						boolean bHas = tag.contains(isLink);
						if (bHas && nSpecies>=4) {
							if (pos==2) {
								bHas = (tag.startsWith(isLink) && tag.endsWith(isLink));
							}
							else {
								bHas = tag.contains(isLink3);
							}
						}
						if (!bHas) {
							refGn.cntHits=0;
							break;
						}
					}
					if (refGn.cntHits==0) break;	
				}
				if (refGn.cntHits>0) {
					if (refGn.cntHits==nSpecies-1) cnt1to1++;
					else cntHard++;
				}
			}
			note = String.format("%,d single; %,d multi", cnt1to1, cntHard);
		} 
		catch (Exception e) {ErrorReport.print(e, "Build/Filter row"); }
	}
	/*********************************************************
	 * For multi-group, merge species
	 * geneVec does not have merged lists because 
	 *    tag = rd.geneTag[i] + " " + rd.groupN in order to not merge from same species during build
	 */
	private void buildXmulti() {
	try {	
		HashMap <String, Integer> tagRowMap = new HashMap <String, Integer>  ();
		ArrayList <Gene> newGeneVec = new ArrayList <Gene> ();
		
		int ix=0, cntMerged=0;
		for (Gene gnObj : geneVec) {
			if (gnObj.cntHits<queryMultiN3) {
				gnObj.cntHits=0;
				continue; 			// There are gnObj genes from other species N-group; this weeds them out
			}
			if (tagRowMap.containsKey(gnObj.rTag)) {	// merge
				int i = tagRowMap.get(gnObj.rTag);
				Gene gn = newGeneVec.get(i);
				gn.merge3(gnObj);
				gnObj.cntHits=0;
				cntMerged++;
			}
			else {
				tagRowMap.put(gnObj.rTag, ix++); // all others with this tag will be merged with this gnObj
				newGeneVec.add(gnObj);
			}	
		}
		if (bAllSpRow13) {						// check for all species
			for (Gene refGn: geneVec) {
				refGn.resetSpCnt3();
				if (refGn.cntSp != nSpecies) {  
					refGn.cntHits=0;
					continue;
				}
			}
		}
		else note = String.format("%,d merged rows", cntMerged);
		
		tagRowMap.clear();
		geneVec.clear();
		geneVec = newGeneVec;
	}
	catch (Exception e) {ErrorReport.print(e, "Build Gene Vec for Group"); }
	}
	
	/********************************************
	 * Create Unions of CoSets. geneVec is sorted by tag.
	 */
	private void buildXcoSet(ArrayList <TmpRowData> tmpRowVec)  {	// tmpRowVec has only TmpRowData of Ref rows
	try {
		HashMap <String, Gene> refMap = new HashMap <String, Gene> ();
		for (Gene gn : geneVec) {
			gn.tmpCoSetArr = new ArrayList <String> ();
			refMap.put(gn.rTag, gn);
		}
		// Add CoSets to each gene
		for (TmpRowData rd : tmpRowVec) {
			int i = -1;
			if      (rd.spIdx[0]==refSpIdx)  i=0;
			else if (rd.spIdx[1]==refSpIdx)  i=1;	
			String tag = (i!=-1) ? rd.geneTag[i] : null;
			if (tag==null) continue;					
			
			int j = (i==0) ? 1 : 0;
			String key = rd.chrIdx[i] + keyDot + rd.chrIdx[j] + keyDot + rd.collinearN; 	// Collinear set key
			if (bAllUnion2) key = rd.spIdx[j] + keyDot + key; 						  // need spIdx to count species 
			
			Gene gn = refMap.get(tag);
			gn.tmpCoSetArr.add(key);
		}
		refMap.clear();
		
		// create Unions
		HashMap <String, Union> unionMap = new HashMap <String, Union> ();
		
		for (Gene gn : geneVec) {
			Union unObj=null;		// find if one exists for one of its coSets
			
			for (String key : gn.tmpCoSetArr) {
				Union keyUnObj = (unionMap.containsKey(key)) ? unionMap.get(key) : null;
				if (unObj==null && keyUnObj!=null) {
					unObj = keyUnObj;
				}
				else if (unObj!=null && keyUnObj!=null && unObj!=keyUnObj) {// two keys for gene have separate unions
					if (Globals.INFO) Globals.prt("Conflict for " + gn.rTag + " " + key);
					Globals.tprt(">> Ref " + gn.rTag + " "+ key + "\n1. " + unObj.toString() + "\n2. " + keyUnObj.toString());
				}
			}
			if (unObj==null) {
				unObj = new Union();
				unionVec.add(unObj);
			}
			gn.unionObj = unObj;	
			
			for (String key : gn.tmpCoSetArr) {
				unObj.updateOne(key, gn);				// counts number of species number in union
				if (!unionMap.containsKey(key)) unionMap.put(key, unObj);
			}
			gn.tmpCoSetArr.clear();
		}
		
		if (!bAllUnion2) {
			unionMap.clear();
			return; 							// 'Row all' does gn.cntCol in reportHTML
		}
		
		// Count species for each union
		HashSet <String> spSet = new HashSet <String> ();
		for (Union unObj : unionMap.values()) {	
			for (String key : unObj.cosetCntMap.keySet()) {
				String sp = key.split(sDot)[0];
				if (!spSet.contains(sp)) spSet.add(sp);
			}
			unObj.unSpCnt = spSet.size();
			spSet.clear();
		}
		unionMap.clear();
	}
	catch (Exception e) {ErrorReport.print(e, "Build Gene Vec for Collinear"); }
	}
	
	/******************************************************
	 * Intialized in Okay; genes added in buildCompute; add anno here
	 * Get All_Anno key/values; uses spAnnoMap; puts HTML <br> between keyword values
	 */
	private boolean buildXannoMap() {
	try {
		TmpRowData rd = new TmpRowData(tdp);	  // to get anno for current row  
		String xBreakSp = htmlBr+htmlSp;           // indent when wraparound for HTML; none for TSV
		
		String notFoundKey="";
		for (int nSp=0; nSp<nSpecies; nSp++) {
			if (spInputKey[nSp].isEmpty()) continue;
			
			HashMap <String, String> nAnnoMap = spAnnoMap.get(nSp).nAnnoMap; // annoMap for this species
			
			HashMap <String, Integer> keyCntMap = new HashMap <String, Integer> (); // lookup; cnt to make sure all are found
			String [] set = spInputKey[nSp].split(keyC);
			for (int i=0; i<set.length; i++) keyCntMap.put(set[i].trim().toLowerCase(), 0);	
			
			String spAbbr = spPanel.getSpAbbr(nSp);
					
			for (String tag : nAnnoMap.keySet()) { // process each gene from this species
				int row = Integer.parseInt(nAnnoMap.get(tag));    // value is row number
				String allanno = rd.getAnnoFromKey(spAbbr, row);  // get anno for this row
				
				String allVal = "";
				String [] annoTok = allanno.trim().split(semiC); // GFF input e.g. ID=one; Desc=foo bar; 
				
				for (String kv : annoTok) {			// process keyword values for gene
					String [] vals = kv.split(keyVal);
					String key = vals[0].toLowerCase();
					
					if (vals.length==2 && keyCntMap.containsKey(key)) {
						String v = vals[1].trim();
						if (bTruncate && v.length()>=descWidth) v = v.substring(0, descWidth-3)+"...";
							
						String wval;
						if (v.isEmpty() || v.equals(Q.empty)) 	wval = Q.dash;
						else if (bExTsv) 						wval = v;
						else if (v.length()>=descWidth) 		wval = Jhtml.wrapLine(v, descWidth, xBreakSp);
						else 									wval = v;
						
						if (allVal!="") allVal += brLine; // | or <br> between Keyword values
						allVal += wval;
						
						keyCntMap.put(key, keyCntMap.get(key)+1); 
					}
				}
				if (allVal=="") allVal = isNone; 
				nAnnoMap.put(tag, allVal);		// replace row number with anno 
			}
			int cutoff = (int) ((double)nAnnoMap.size()*0.01); // CAS563 a keyword may not show on Column because <50; but not show as missing
			Globals.tprt("Keyword cutoff: " + cutoff + " from " + nAnnoMap.size());
			for (String key : keyCntMap.keySet()) {	
				if (keyCntMap.get(key)<cutoff) 	
					notFoundKey +=  "\n   " + spAbbr + ": " + key + " Count: " + keyCntMap.get(key);
			}
		}
		if (!notFoundKey.equals("")) {
			Utilities.showWarningMessage("Keywords not found or low usage: " + notFoundKey);
			return false;
		}
		return true;
	} catch (Exception e) {ErrorReport.print(e, "Build Anno Map"); return false;}
	}
	
	private void clearDS() {
		unionVec.clear(); geneVec.clear(); 
		spAnnoMap.clear();
	}
	/******************************************************************
	 * Classes for Build
	 * Multi: a Gene+GroupN is the key
	 */
	private class Gene {
		private String rChr, rSuffix, rTag; // ref only; 
		private int rGeneNum;		   	// number only for integer sort, equal sorts on rSuffix
		private int rGrpN=0, rGrpSz=0; 	// Multi & Clust: #, computed N hits from interface
		private int cntHits=0;			// number of ref hits (0 means filtered)
		private int cntSp=0; 			// used for AllSpRow 			
		
		// List (splitC delimited) of all genes ref aligns to on speciesN; the two arrays are 1-1 for collinear
		// pair genes:		  e.g. ref tag      tag          2 tags/cosets 
		private String [] oTagList; // [0]1.62  [1] 1.63.b   [2] 5.78; 8.5522	// Chr.genenum.suffix
		private String [] oCoNList; // [0]      [1] 7.1      [2] 4.1;  3.182	// collinear sz.coset#
		
		private Union unionObj=null;  					// all coSet pair genes with ref; 
		private ArrayList <String> tmpCoSetArr = null; 	// used for buildXcoSet only, cleared at end
		
		private Gene(int spPos, String tag, int grpN, int grpSz) {
			oTagList = new String [nSpecies];
			for (int i=0; i<nSpecies; i++) oTagList[i] = "";
			if (bIsCoSet2) { 							// For non-ref (ref column will be empty)
				oCoNList = new String [nSpecies];
				for (int i=0; i<nSpecies; i++) oCoNList[i] = "";
			};
			
			if (tag.endsWith(sDot)) tag = tag.substring(0, tag.length()-1);
			rTag = oTagList[spPos] = tag;				// chr.gnum.suffix
			
			String [] tok = tag.split(sDot);			// split tag (gene#) into chr, gnum, suffix
			rChr = tok[0];
			rGeneNum = Integer.parseInt(tok[1]);
			rSuffix =  (tok.length==3) ? tok[2] : "";
			
			this.rGrpN = grpN;
			this.rGrpSz = grpSz;
			cntSp=1;
		}
		private void addNonRef(int spPos, String tag, int coSz, int coN, int grpN) {
			if (oTagList[spPos].equals("")) cntSp++; // first time for this species
			cntHits++;								 // used by Multi3 and bAllLinkRow1
			
			if (bIsCoSet2) {
				oCoNList[spPos] += coSz + Q.COSET + coN + splitC;
			}
			else if (bIsMulti3 && bShowNum23) {		// 1st tag is grp-sz for multi-hit-gene
				if (oTagList[spPos].equals("")) 
					oTagList[spPos] = isMulti + rGrpSz + Q.GROUP + rGrpN + splitC;
			}
			if (tag.endsWith(sDot)) tag = tag.substring(0, tag.length()-1);
			oTagList[spPos] += tag + splitC;
		}
		
		private void merge3(Gene gnMerge) { 
			for (int i=0; i<nSpecies; i++) {
				if (i==refPos) continue;
				if (gnMerge.oTagList[i].equals("")) continue;
				
				if (!oTagList[i].equals("")) oTagList[i] += brLine; // multiple groups (diff chr) for this pos
				oTagList[i] += gnMerge.oTagList[i];
				return;
			}
		}
		private void resetSpCnt3() { // it turns out wrong after merge
			cntSp=0;
			for (int i=0; i<nSpecies; i++)
				if (!oTagList[i].equals("")) cntSp++;
		}
		
		/*---- Sort each oTagList[sp] ----*/
		// annotations are found by geneTag HashMap lookup, so do not need switching
		private void sortTags() { 
			for (int sp=0; sp<nSpecies; sp++) {
				if (sp==refPos || oTagList[sp].equals("")) continue;
						
				String [] ntags = oTagList[sp].split(splitC);
				if (ntags.length==1) continue;
		
				String [] cosets = (bIsCoSet2) ? oCoNList[sp].split(splitC) : null;
				boolean swap=false;
				
				for (int i=0; i<ntags.length-1; i++) { // good ole bubble sort on chr.genenum.suffix
					if (bIsMulti3 && ntags[i].startsWith(isMulti)) continue;
					
					String [] tokI = ntags[i].split(sDot);
					boolean bSort=false;
					
					for (int j=i+1; j<ntags.length; j++) {
						String [] tokJ = ntags[j].split(sDot);
						
						if (tokI[0].equals(tokJ[0])) { 
							int gnI  = Utilities.getInt(tokI[1]);
							int gnJ = Utilities.getInt(tokJ[1]);
							bSort = (gnI>gnJ);
						}
						else {
							int chrI = Utilities.getInt(tokI[0]);
							int chrJ = Utilities.getInt(tokJ[0]);
							if (chrI>0 && chrJ>0) bSort = chrI>chrJ;
							else                  bSort = (tokI[0].compareTo(tokJ[0])>0); // chrs no int
						}
						if (bSort) {
							swap = true;
							String x = ntags[i];   ntags[i] = ntags[j];     ntags[j] = x;
							if (bIsCoSet2) {
								x = cosets[i]; cosets[i] = cosets[j]; cosets[j] = x;
							}
							tokI = ntags[i].split(sDot);	// tags[i] was just changed
						}
					}
				}
				if (swap) {
					oTagList[sp] = "";
					for (int i=0; i<ntags.length; i++) oTagList[sp] += ntags[i] + splitC;
		
					if (bIsCoSet2) {
						oCoNList[sp] = "";
						for (int i=0; i<ntags.length; i++) oCoNList[sp] += cosets[i] + splitC;
					}
				}
			}
		}
		public String toString() {
			String tags="";
			for (int i=0; i<nSpecies; i++) {
				if (i==refPos) continue;
				tags += i + ". " + oTagList[i] + "     ";
			}
			if (bIsMulti3)  return String.format("%10s Grp#%d.%d   %s", rTag, rGrpSz, rGrpN, tags);
			else            return String.format("%10s %s species  %s", rTag, cntSp, tags);
		}
	} // end gene class

	/*********************************************
	 * Collinear: Gene contains a pointer to union
	 * Row: temporary for geneVec to find links
	 * keyCntMap: Collinear - count rd.chrIdx[i].rd.chrIdx[j].rd.collinearN
	 *            Rows - complete graph, count number of link genes that share ref gene tag
	 */
	private class Union { 
		private ArrayList <Gene> ugeneVec = new ArrayList <Gene> ();
		private HashMap <String, Integer> cosetCntMap = null;
		private int unSpCnt=0;  // Computed in Xcoset; cnt of species in union
		
		private Union() {
			if (bIsCoSet2 || bAllLinkRow1) cosetCntMap = new HashMap <String, Integer> ();
		}
		private void updateOne(String key, Gene gn) {  // CoSet only: count of collinear sets
			if (!ugeneVec.contains(gn)) ugeneVec.add(gn);
			
			if (!cosetCntMap.containsKey(key)) cosetCntMap.put(key, 1);
			else cosetCntMap.put(key, cosetCntMap.get(key)+1);
		}
		private void clear() {
			ugeneVec.clear();
			if (cosetCntMap!=null) cosetCntMap.clear();
		}
	}
	// one per species where nAnnoMap contains gene values of keyword
	private class Anno { 
		private HashMap <String, String> nAnnoMap  = new HashMap <String, String> (); // gene#, value
	}
	/***********************************************************************
	 * XXX Output CAS563 make class and share more between tsv and html
	 */
	private class Zoutput {
		int gnIdx=0, unIdx=0;	// Index into geneVec; index into unionVec
		int gnNum=0, unNum=0;   // Print row/union number
		int cntOut=0, nAllNonRefSp=nSpecies-1;
		Union lastUn=null, unObj=null;
		String lastTag="";
		
		String tsvTab = "\t"; // everything but row gets tab in front
		
		StringBuffer hBodyStr= new StringBuffer();
		
		private void runReport() {
			String report=null;
			if (bExTsv) 	report = reportTSV();
			else 					report = reportHTML();
			if (report==null) return;
			
			if (bPopHtml) {
				util.Jhtml.showHtmlPanel(null, (title+" " + refSpName), report); // CAS563 weird behavior if attached to this
			}
			else {
				outFH = getFileHandle();
				if (outFH==null) return;
			
				outFH.println(report);
				outFH.close();
			}
		}
		
		/**************************************************
		 * build CSV for file; uses geneVec and spAnnoMap; CAS562 add
		 * Does not have repetitive Ref Tag isAll
		 */
		private String reportTSV() {
			try {
				// Column headings
				String hColStr= "Row#";
				for (int x=0; x<nSpecies; x++) {
					String ref = (x==refPos) ? isRef : "";
					hColStr += tsvTab + spPanel.getSpName(x) + ref;
				}
				for (int x=0; x<nSpecies; x++) {
					if (!spInputKey[x].isEmpty()) {
						hColStr += tsvTab + spPanel.getSpName(x)+ " " + spInputKey[x].trim();
					}
				}
				
				// rows
				while (true) {
					if (bIsCoSet2 && unIdx==unionVec.size() && gnIdx==unObj.ugeneVec.size()) break;
					if (gnIdx==geneVec.size()) break;
				
					Gene gnObj = isFiltered();
					if (gnObj==null) continue;
					
					if (bIsCoSet2 && unObj!=lastUn) {// divider between unions	
						unNum++;
						String setsz = String.format("%d [%d]", unNum, unObj.cosetCntMap.size());	
						if (!bAllSpRow13 && !bAllUnion2 && unObj.unSpCnt==nAllNonRefSp) setsz += isAll;
						hBodyStr.append(setsz+ "\n");
						lastUn = unObj;
					}

				/* Row columns */
					hBodyStr.append(gnNum);
					
					for (int nSp=0; nSp<nSpecies; nSp++) { // Columns
						hBodyStr.append(tsvTab);
						
						if (nSp==refPos) {
							hBodyStr.append(String.format("%s", gnObj.oTagList[nSp])); 
						}
						else if (gnObj.oTagList[nSp]=="") {
							hBodyStr.append("    ");   					    // no gene for this species
						}
						else {
							String [] gnTok = gnObj.oTagList[nSp].split(splitC); // list of species genes; chr.genenum
							
							if (bIsCoSet2 && bShowNum23) { 
								String [] coSetTok = gnObj.oCoNList[nSp].split(splitC); // list of collinear Sz.N
								
								for (int j=0; j<gnTok.length; j++) {
									if (j>0) hBodyStr.append(semiC+ " ");
									if (bShowNum23) 
										 hBodyStr.append(String.format("%s %s", gnTok[j], isCoSet + coSetTok[j]));
									else hBodyStr.append(String.format("%s", gnTok[j]));
								}
							}
							else {
								for (int j=0; j<gnTok.length; j++) { 
									if (j>0) hBodyStr.append(semiC+ " ");
									hBodyStr.append(gnTok[j]);
								}
							}
						}		
					}
					if (hasKey) addAnnoCols(gnObj);
					hBodyStr.append("\n");
				} // End loop thru geneVec
			
				clearDS(); Globals.rclear();
				
				String head = strTitle("### ", "\n", unNum, gnNum) + "\n";
				if (cntOut==0) {
					String msg = "No reference genes fit the display options\n" + head;
					JOptionPane.showMessageDialog(null, msg, "No results", JOptionPane.WARNING_MESSAGE);
					return null;
				}
				return head + hColStr + "\n" + hBodyStr.toString();
			}
			catch (Exception e) {ErrorReport.print(e, "Build HTML"); return null;}
		}
		
		/***********************************************************
		 * build HTML for popup and file; uses geneVec and spAnnoMap
		 */
		private String reportHTML() {
		try {
		// column headings
			String hColStr="";
			int colSpan=1;
			
			hColStr += "<tr><td><b><center>#</center></b>"; // for row/union number
			for (int x=0; x<nSpecies; x++) {
				colSpan++;
				String name = (x==refPos) ? "<i>" + spPanel.getSpName(x) + "</i>" : spPanel.getSpName(x);
				hColStr += "<td><b><center>" + name + "</center></b>";
			}
			for (int x=0; x<nSpecies; x++) { // Annotation
				if (!spInputKey[x].isEmpty()) {
					String name = (x==refPos) ? "<i>" + spPanel.getSpName(x) + "</i>" : spPanel.getSpName(x);
					hColStr += "<td><b><center>" + name + " " + spInputKey[x].trim() + "</center></b>";
					colSpan++;
				}
			}
			hColStr += "\n";
			
		// Table
			while (true) { // loop through geneVec; while loop because the counts are different for Coset and Gene
				if (bIsCoSet2 && unIdx>=unionVec.size() && gnIdx>=unObj.ugeneVec.size()) break;
				if (gnIdx>=geneVec.size()) break;
				
				Gene gnObj = isFiltered();
				if (gnObj==null) continue;
				
				if (bIsCoSet2 && unObj!=lastUn) {// divider between unions	
					unNum++;
					String setsz = String.format("<b>%d [%d sets]</b>", unNum, unObj.cosetCntMap.size());	
					if (!bAllSpRow13 && !bAllUnion2 && unObj.unSpCnt==nAllNonRefSp) setsz += isAll; 
					if (!bShowBorder) hBodyStr.append("<tr><td colspan=" + colSpan + "><hr>");
					hBodyStr.append("<tr><td colspan=" + colSpan + ">" + setsz);
					lastUn = unObj;
				}
				
		/* Output row  of columns for current gnObj */
				if (!bShowBorder) hBodyStr.append("<tr><td colspan=" + colSpan + "><hr>");
				hBodyStr.append("<tr><td>" + gnNum + htmlSp);
				
				for (int posSp=0; posSp<nSpecies; posSp++) { // Columns
					if (posSp==refPos) {							// Reference: only one gene for ref
						String mark = (gnObj.rTag.equals(lastTag)) ? isAll : "";
						lastTag = gnObj.rTag;
						hBodyStr.append("\n  <td><i>" + gnObj.oTagList[posSp] + mark + "</i>"); 
					}
					else if (gnObj.oTagList[posSp].equals("")) {	// no gene for this species
						hBodyStr.append("\n  <td>" + htmlSp);      
					}
					else {										    // one or more genes in non-ref column
						hBodyStr.append("\n  <td style='white-space: nowrap;'>");
						String [] gnTok = gnObj.oTagList[posSp].split(splitC); // list of species genes; chr.genenum
						
						if (bIsCoSet2) { 
							String [] coSetTok = gnObj.oCoNList[posSp].split(splitC); // list of collinear Sz.N
							for (int j=0; j<gnTok.length; j++) {
								if (j>0) hBodyStr.append(htmlBr);
								hBodyStr.append(gnTok[j] + htmlSp);
								if (bShowNum23) {
									for (int k=gnTok[j].length(); k<padWithSpace; k++) hBodyStr.append(htmlSp);
									hBodyStr.append(isCoSet + coSetTok[j]);
								}
							}
						}
						else if (bIsMulti3){
							for (int j=0; j<gnTok.length; j++) {
								String tag = gnTok[j];
								if (bShowNum23) {// can be multi Groups for same Species diff chr
									if (tag.startsWith(isMulti) || tag.startsWith(htmlBr)) 
										tag = "<b>" + tag + "</b>";
								}
								if (j==0) hBodyStr.append(tag);
								else      hBodyStr.append(htmlBr + tag);
							}
						}
						else {
							for (int j=0; j<gnTok.length; j++) {
								String tag = gnTok[j];
								if (!bShowLink1) {
									tag = tag.replace(isLink,"");
									tag = tag.replace(isLink3,"");
								}
								if (j==0) hBodyStr.append(tag);
								else      hBodyStr.append(htmlBr + tag);
							}
						}
					}		
				} // end species loop to write gene# columns for this row
				
				if (hasKey) addAnnoCols(gnObj);
				hBodyStr.append("\n");
			} // End loop geneVec
			
			clearDS(); Globals.rclear();
			
			String htitle = (title + " for " + refSpName);
			String head = strHeadHTML(htitle);
			String tail = strTailHTML();
			
			if (cntOut==0) {
				if (!bPopHtml) {
					String msg = "No reference genes fit the display options\n" + strTitle("", "\n", gnNum, unNum);
					JOptionPane.showMessageDialog(null, msg, "No results", JOptionPane.WARNING_MESSAGE);
					return null;
				}
				return head + "<p><big>No reference genes fit the display options</big>" + tail;
			}
			return head + hColStr + hBodyStr.toString() + tail; // for file or popup
		} 
		catch (Exception e) {ErrorReport.print(e, "Build HTML"); return null;}
		}
		private String strHeadHTML(String htitle) {
			// border-collapse: collapse does not work in popup; making border-spacing: 0px makes it too think
			String head = "<!DOCTYPE html><html>\n<head>\n"
					+ "<title>" + htitle + "</title>\n" + "<style>\n" + "body {font-family: monospaced;  font-size: 10px; }\n";		
			if (bShowBorder) {
				head += ".ty {border: 1px solid black; border-spacing: 1px; border-collapse: separate; margin-left: auto; margin-right: auto;}\n";
				head += ".ty td {border: 1px solid black; padding: 5px; border-collapse: separate; white-space: nowrap; }";
			}
			else  {
				head += ".ty {border: 1px solid black; border-spacing: 10px 0px; border-collapse: separate; margin-left: auto; margin-right: auto;}\n";
				head += ".ty td {border: 0px solid black; white-space: nowrap; }";
			}
			head += "\n</style>\n</head>\n<body>\n" + "<a id='top'></a>\n";
			
			head += "<center>" + strTitle("", htmlBr, unNum, gnNum); // </center> in tail
			head += "</head><p><table class='ty'>\n"; 
			
			return head;
		}
		private String strTailHTML() {
			String tail="</table>";
			if ((gnNum>100 || unNum>10) && bExHtml) tail += "<p><a href='#top'>Go to top</a>\n";
			tail += "</center></body></html>\n";
						
			return tail;
		}
		/*********************************************************
		 * Filters a row based on options set; Works for both HTML and TSV
		 */
		private Gene isFiltered() {
		try {
			Gene gnObj;
			
			if (bIsGene1 || bIsMulti3) {	// gene report
				gnObj = geneVec.get(gnIdx++);
				
				if (gnObj.cntHits==0) return null;  // everything filtered already in buildXrow and buildXmult
			}
			else if (bIsCoSet2) {				
				if (unObj==null || gnIdx==unObj.ugeneVec.size()) { 
					if (unObj!=null) unObj.clear();
					unObj = unionVec.get(unIdx++);
					gnNum=0; gnIdx=0; 
				}
				gnObj = unObj.ugeneVec.get(gnIdx++); 
				if (bAllUnion2 && gnObj.unionObj.unSpCnt!=nAllNonRefSp) return null;
			}	
			else {
				Globals.tprt("No Mode");
				gnObj = geneVec.get(gnIdx++);
			}
			
			gnNum++;
			if (++cntOut%100 == 0) Globals.rprt("Processed " + cntOut + " genes");
			return gnObj;
		}
		catch (Exception e) {ErrorReport.print(e, "Build HTML"); return null;}
		}
		/***********************************************************
		 * Columns of the user input keyword=value
		 * 	already has <br> between a species keyword values, e.g. ID<br>Desc
		 */
		private void addAnnoCols(Gene gnObj) {
		try {
			for (int nSp=0; nSp<nSpecies; nSp++) {
				if (spInputKey[nSp].isEmpty()) continue; // no keyword set
				
				if (gnObj.oTagList[nSp].isEmpty()) {	 // no genes
					if (bExTsv) 		hBodyStr.append(tsvTab + isNone);
					else if (bExHtml)	hBodyStr.append("\n  <td>" + htmlSp);  // nl make file manually editable
					else 				hBodyStr.append("<td>" + htmlSp); 
					continue;
				}
				HashMap <String, String> nAnnoMap = spAnnoMap.get(nSp).nAnnoMap;
				String anno="";
				String [] taglist = gnObj.oTagList[nSp].split(splitC); // already has keyword values delimited by <br> or |
				
				for (String tag : taglist) {
					if (bIsMulti3) {
						if (tag.startsWith(isMulti)) continue;
						if (tag.startsWith(isMulti) || tag.startsWith(htmlBr)) {
							anno += brLine;
							continue;
						}
					}
					if (bIsGene1 && tag.contains(isLink) || tag.contains(isLink3) ) {
						tag = tag.replace(isLink, ""); 		// remove link for search & display
						tag = tag.replace(isLink3, ""); 	
					}	
					String showtag = "";
					if (bShowGene) {
						if (bExTsv) showtag = tag;
						else {
							String sp="";
							for (int i=0; i<padWithSpace-tag.length(); i++) sp+=htmlSp;
							showtag = tag + " " + sp;
						}
					}
					if (!anno.isEmpty()) { 	
						if (bExTsv) anno += " ";
						else        anno += htmlBr; 
					}
					if (nAnnoMap.containsKey(tag))  anno += showtag +nAnnoMap.get(tag).trim() + semiC;
					else 							anno += showtag + isNone + semiC;
				}
				if (bExTsv) hBodyStr.append(tsvTab + anno);
				else        hBodyStr.append("\n  <td>" + anno);
			} // end species loop to write annotation columns for this row
		}
		catch (Exception e) {ErrorReport.print(e, "Build Anno Columns");}
		}
		/**************************************************************
		 * First lines: title in HTML and remarks in TSV
		 * Works for both HTML and TSV by using:  remark = "", "###"  and br = <br>, "\n"
		 */
		private String strTitle(String remark, String br, int unNum, int gnNum) {
			String head = remark + title + " for " + refSpName ;
			if (!bExTsv) head = "<b>" + head + "</b>";
			else br = br + remark;				// for TSV, i.e. \n###, HTML says <br>
			head += br + "Filter: " + tdp.theSummary;
		
			if (bIsCoSet2)      head += String.format("%s%,d Unions of Collinear Sets", br, unNum);
			else if (bIsMulti3) head += String.format("%s%,d Rows with Multi-hit Genes", br, gnNum);
			else                head += String.format("%s%,d Gene Rows", br, gnNum);
			
			if (nSpecies>2) {
				String x="";
				if (bIsGene1) { // order is important
					if (bAllLinkRow1)	  x = "; All species linked";
					else if (bLinkRow1)	  x = "; All species +link";
					else if (bAllSpRow13) x = "; All species per row";
					else 				  x = "; All rows";
					if (!note.equals("")) x += "; " + note;
				}
				else if (bIsCoSet2) {
					if (bAllUnion2) x += "; All species per union";
					else 			x = "; All rows";
					if (!note.equals("")) x += "; " + note;
				}
				else if (bIsMulti3) {
					if (bAllSpRow13) x = "; All species per row";
					x += "; " + note;
				}
				head += x;
			}
			return head;
		}
		
		/* getFileHandle */
		private PrintWriter getFileHandle() {
	    	String saveDir = Globals.getExport(); 
			
	    	String type;
	    	if (bIsCoSet2) type = "CoSet_";
	    	else if (bIsMulti3) type = "Multi_";
	    	else type = "Gene_";
	    	
	    	String name =  filePrefix + type + refSpAbbr;
	    	String fname = (bExHtml) ? name + ".html" :  name + ".tsv";
	    	
			JFileChooser chooser = new JFileChooser(saveDir);
			chooser.setSelectedFile(new File(fname));
			if(chooser.showSaveDialog(tdp.queryFrame) != JFileChooser.APPROVE_OPTION) return null;
			if(chooser.getSelectedFile() == null) return null;
			
			String saveFileName = chooser.getSelectedFile().getAbsolutePath();
			if (bExTsv) {
				if(!saveFileName.endsWith(".tsv")) saveFileName += ".tsv";
			}
			else {
				if(!saveFileName.endsWith(".html")) saveFileName += ".html";
			}
			
			if (new File(saveFileName).exists()) {
				if (!Utilities.showConfirm2("File exists","File '" + saveFileName + "' exists.\nOverwrite?")) return null;
			}
			PrintWriter out=null;
			try {
				out = new PrintWriter(new FileOutputStream(saveFileName, false));
			}
			catch (Exception e) {ErrorReport.print(e, "Cannot open file - " + saveFileName);}
			
			return out;
	    }
	}
	/*********************************************************************/
	// Interface 
	private TableMainPanel tdp;
	private SpeciesPanel spPanel;
	private JPanel mainPanel;
	
	private JRadioButton [] radSpecies=null;
	private JTextField [] txtSpKey=null;
	
	private JTextField txtDesc = null;
	private JCheckBox chkTrunc = null, chkShowGene = null;
	
	private JRadioButton radAllSpRow, radLinkRow, radAllLinkRow, radAllSpUnion, radAllSpIgn;
	private JCheckBox chkShowNum=null, chkShowLinks = null, chkBorder = null;
	
	private JRadioButton btnPop = null;
	private JButton btnOK=null, btnCancel=null, btnClear=null, btnInfo=null;	
}
