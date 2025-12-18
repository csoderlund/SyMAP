package symapQuery;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
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
import util.Popup;

/***********************************************
 * Gene Report when there is no reference; 
 * 		most of this (e.g. anno) is duplicated from UtilReport; its easier then zillions of if-then-else
 * There is no TSV file
 */
public class UtilReportNR  extends JDialog {
	private static final long serialVersionUID = 1L;
	private final int defTxtWidth=40, padWithSpace=10;
	private static String filePrefix = "Report";
	private int cntFullLink=0;
	
	private static final String keyC = ",", 	// input key parameter for anno
								semiC = ";",	// fixed delimiter for anno output
								keyVal = "=", 	// between keyword and value
								tup = ":",		// char between tuple tags
								keyDot = ".",   // create hash key
								isGrp="#",		// Group number
								nLink="(";		// opening parenthesis for cluster link count
	private static final String noAnno="---",	
								htmlSp="&nbsp;",
								htmlBr="<br>";
	
	// Okay: From input menu: the setting are used as the defaults
	// Object is reused - so reset anything not wanted from last time
	private boolean bPopHtml=true, bExHtml=false;
	private int descWidth=40;
	private boolean hasKey=false;
	private boolean bTruncate=false,
					bShowGene=false,
					bLinkRow=false,
					bShowBorder=true,
					bShowGrp=true;

	private PrintWriter outFH = null;
	private String title="";					  
	
	// Anno Keys:
	private int nSpecies=0;														  //  createSpecies
	private HashMap <Integer, Integer> spIdxPosMap = new HashMap <Integer, Integer> (); // idx, pos
	private HashMap <Integer, Integer> spPosIdxMap = new HashMap <Integer, Integer> (); // pos, idx
	private String [] spInputKey; 												  // okay
	// buildCompute puts genes in map; buildXannoMap() add annotation
	private HashMap <Integer, Anno> spAnnoMap = new HashMap <Integer, Anno> (); 
	
	// build and output
	private ArrayList <Clust> clustArr = new ArrayList <Clust> ();
	
	/***************************************************************************/
	protected UtilReportNR(TableMainPanel tdp) { // this is called 1st time for Query
		this.tPanel = tdp;
		spPanel = tdp.queryPanel.speciesPanel;
		
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		
		title = "SyMAP Cluster Gene Report";
		setTitle(title);
		
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
		JPanel optPanel = Jcomp.createPagePanel();
		
		nSpecies = tPanel.queryPanel.nSpecies;
		
		radSpecies = new JLabel [nSpecies];
		txtSpKey =   new JTextField [nSpecies];
		
		int width = 100;
		JPanel row = Jcomp.createRowPanel();
		JLabel label = Jcomp.createLabel("Species");
		row.add(label);
		if (Jcomp.isWidth(width, label)) row.add(Box.createHorizontalStrut(Jcomp.getWidth(width, label)));
		
		row.add(Jcomp.createLabel("Gene Annotation Columns", 
				"Enter comma-delimited list of keywords from All_Anno column for one or more species."));
		optPanel.add(row);
		
		int rwidth=width;
		for(int x=0; x<nSpecies; x++) {
			radSpecies[x] = Jcomp.createLabel(spPanel.getSpName(x)); 
			rwidth = Math.max(rwidth, radSpecies[x].getPreferredSize().width);
		}
		
		for(int pos=0; pos<nSpecies; pos++) {
			row = Jcomp.createRowPanel();
			row.add(radSpecies[pos]); 
			if (Jcomp.isWidth(rwidth, radSpecies[pos])) row.add(Box.createHorizontalStrut(Jcomp.getWidth(rwidth, radSpecies[pos])));
			
			txtSpKey[pos] = Jcomp.createTextField("",18);
			row.add(txtSpKey[pos]);
			
			optPanel.add(row);
			
			int spIdx = spPanel.getSpIdx(pos);
			spIdxPosMap.put(spIdx, pos);
			spPosIdxMap.put(pos, spIdx);
		}
		
		JPanel descPanel = Jcomp.createRowPanel();
		descPanel.add(Box.createHorizontalStrut(width));
		
		txtDesc  = Jcomp.createTextField(descWidth+"",3);
		chkTrunc = Jcomp.createCheckBox("Truncate", "Truncate description to value Width", false);
		chkShowGene = Jcomp.createCheckBox("Gene#", "If selected, the Gene# will proceed the annotation", bShowGene); 	
		
		descPanel.add(Jcomp.createLabel("Width", "Length of each Gene Annotation Column"));
		descPanel.add(Box.createHorizontalStrut(1));
		descPanel.add(txtDesc); descPanel.add(Box.createHorizontalStrut(3));
		descPanel.add(chkTrunc);descPanel.add(Box.createHorizontalStrut(3));
		descPanel.add(chkShowGene);
		optPanel.add(descPanel);
			
		mainPanel.add(optPanel);
		mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
	}
	private void createDisplay() {
		radOrder = Jcomp.createRadio("Unlinked unique", "Each gene is shown once per species");
		radLink  = Jcomp.createRadio("Linked rows", "Each row is a set of fully linked genes (slower)");
		chkBorder = Jcomp.createCheckBox("Border", "If selected, the HTML table will contain borders around rows", bShowBorder); 	
		chkShowNum = Jcomp.createCheckBox("Group", "If selected, show the group size:number", bShowGrp); 	
		
		JPanel optPanel = Jcomp.createPagePanel();
		JPanel row1 = Jcomp.createRowPanel();
		JLabel dp = Jcomp.createLabel("Display");
		int width = dp.getPreferredSize().width;
		row1.add(dp); row1.add(Box.createHorizontalStrut(width - dp.getPreferredSize().width));
		row1.add(radLink); row1.add(Box.createHorizontalStrut(5));
		row1.add(radOrder);
		optPanel.add(row1);
		
		ButtonGroup ba = new ButtonGroup();
		ba.add(radOrder); ba.add(radLink); radOrder.setSelected(true);
		
		optPanel.add(Box.createVerticalStrut(2));
		JPanel row2 = Jcomp.createRowPanel();
		JLabel sh = Jcomp.createLabel("Show");
		row2.add(sh); row2.add(Box.createHorizontalStrut(width - sh.getPreferredSize().width));
		row2.add(chkBorder); row2.add(Box.createHorizontalStrut(3)); 
		row2.add(chkShowNum);
		optPanel.add(row2);
		
		mainPanel.add(optPanel);
		mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
	}
	// output and bottom buttons
	private void createOutput() {
		JPanel optPanel = Jcomp.createPagePanel();
		
		JPanel row = Jcomp.createRowPanel();
		row.add(Jcomp.createLabel("Create")); row.add(Box.createHorizontalStrut(5));
		
		btnPop = Jcomp.createRadio("Popup", "Popup of results"); 
		btnPop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				bPopHtml = btnPop.isSelected();
				if (bPopHtml) bExHtml = false;
			}
		});
		row.add(btnPop); row.add(Box.createHorizontalStrut(5));
		
		JRadioButton btnHTML =  Jcomp.createRadio("HTML File", "HTML file of results");
		btnHTML.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				bExHtml = btnHTML.isSelected();
				if (bExHtml) bPopHtml = false;
			}
		});
		row.add(btnHTML); row.add(Box.createHorizontalStrut(5));
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(btnHTML); grp.add(btnPop);   
    	btnPop.setSelected(true);

        optPanel.add(row); 
        	
    // buttons
    	btnOK = Jcomp.createButton("Create", "Create gene pair report");
    	btnOK.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				doReport();
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
		
		btnInfo = Jcomp.createBorderIconButton("/images/info.png", "Quick Help Popup");
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
		String msg = "Each cluster group will be displayed.";
		msg +=  "\n\nBy default, only the Gene# will be shown."
				+ "\nEnter a comma-separated list of keyword found in the 'All_Anno' column; "
				+ "\n   a column in the HTML table will be listed with the values."
				+ "\n   The keywords are NOT checked for correctness.";
		msg +=  "\n\nThere will be a extra options if there are >2 species being displayed."
				+ "\n   Hover over option for more detail.";
		msg += "\n\nSee ? for details.\n";
		
		Popup.displayInfoMonoSpace(this, "Quick Help", msg, false);
	}
	/**************************************************************8
	 * Shared
	 */
	private void doReport() {
	/*-------- Settings ---------*/
		spInputKey = new String [nSpecies];				// List of input keywords per species
		for (int i=0; i<nSpecies; i++) spInputKey[i]=""; 
		for (int x=0; x<nSpecies; x++) { 
			spAnnoMap.put(x, new Anno()); 				// in buildCompute add gene; in buildXannoMap add anno
			
			spInputKey[x] = txtSpKey[x].getText().trim(); 
			if (spInputKey[x].contains(" ") && !spInputKey[x].contains(keyC)) {
				Popup.showInfoMessage("Bad Key", "Keys must be separated by ',' and no space within a key '" + spInputKey[x] + "'");
				return;
			}
			if (!spInputKey[x].isEmpty()) hasKey=true;	// if any have key
		}
		
		// Other settings
		String txt = txtDesc.getText();
		try {descWidth = Integer.parseInt(txt);}
		catch (Exception e){
			descWidth = defTxtWidth;
			JOptionPane.showMessageDialog(null, "Incorrect desc width number: '" + txt + "'", "Incorrect width", JOptionPane.WARNING_MESSAGE);
			return;
		}
		bLinkRow = (radLink.isSelected()) ? true : false;
		bShowBorder = chkBorder.isSelected();
		bShowGene = chkShowGene.isSelected();
		bTruncate = chkTrunc.isSelected();
		bShowGrp  = chkShowNum.isSelected();
		
	/*-------- Build report and display ---------*/
		setVisible(false);	// can do other things if set false here
		
		Thread inThread = new Thread(new Runnable() {
    		public void run() {
			try {
				tPanel.setBtnReport(false);
				btnOK.setEnabled(false); btnCancel.setEnabled(false); 
				
				buildCompute();
				
				if (clustArr.size()==0) {
					String msg = "No clusters pass filters";
					JOptionPane.showMessageDialog(null, msg, "No results", JOptionPane.WARNING_MESSAGE);
				}
				else {
					new Zoutput().runReport();
				}
				
				btnOK.setEnabled(true); btnCancel.setEnabled(true); 
				tPanel.setBtnReport(true);
			} catch(Exception e) {ErrorReport.print(e, "Ok report");}
	    	}
	    });
	    inThread.start();
	}
	private void clear() {
		for (int i=0; i<nSpecies; i++) txtSpKey[i].setText("");
		
		for (JTextField t : txtSpKey) t.setText("");
		txtDesc.setText(defTxtWidth+""); 
		chkTrunc.setSelected(false);
		chkShowGene.setSelected(true);
		
		radOrder.setSelected(true);
		chkBorder.setSelected(true);
		chkShowNum.setSelected(true);
		
		btnPop.setSelected(true);
	}
	private void clearDS() {
		clustArr.clear(); 
		spAnnoMap.clear();
	}
	/***********************************************************
	 * XXX Used by all output to build rows of the report
	 */
	private void buildCompute() {
	try {	
		// reused variables
		cntFullLink=0; 	
		if (title.endsWith("Report")) title += " " + tPanel.theTableData.getNumRows();
		
		HashMap <Integer, Clust> grpMap = new HashMap <Integer, Clust>  ();
		HashSet <String> gnTagSet = new HashSet <String> ();
		System.gc();
		
		for (int row=0; row<tPanel.theTableData.getNumRows(); row++) {
			if (row % Q.INC == 0) Globals.rprt("Process " + row + " rows"); // CAS579 add rprts and gc
			
			TmpRowData rd = new TmpRowData(tPanel);
			rd.loadRow(row);		// only numbered pseudos will be in the clusters, as screened in ComputeClust
			
			Clust grpObj;
			if (grpMap.containsKey(rd.groupN)) grpObj = grpMap.get(rd.groupN); 
			else {
				grpObj = new Clust(rd.groupN, rd.groupSz);
				grpMap.put(rd.groupN, grpObj);
				clustArr.add(grpObj);
			}
			
			int pos0 = spIdxPosMap.get(rd.spIdx[0]);
			String key0 = rd.groupN + keyDot + pos0 + keyDot + rd.geneTag[0]; // can be in multiple groups
			if (!gnTagSet.contains(key0)) {
				gnTagSet.add(key0);
				if (hasKey && !spInputKey[pos0].isEmpty()) { // All geneVec get put in nAnnoMap
					HashMap <String, String> nAnnoMap = spAnnoMap.get(pos0).nAnnoMap;
					nAnnoMap.put(rd.geneTag[0], ""+rd.nRow); // nRow will be replaced with Anno from nRow
				}
			}
				
			int pos1 = spIdxPosMap.get(rd.spIdx[1]);
			String key1 = rd.groupN + keyDot + pos1 + keyDot + rd.geneTag[1];
			if (!gnTagSet.contains(key1)) {
				gnTagSet.add(key1);
				if (hasKey && !spInputKey[pos1].isEmpty()) { // All geneVec get put in nAnnoMap
					HashMap <String, String> nAnnoMap = spAnnoMap.get(pos1).nAnnoMap;
					nAnnoMap.put(rd.geneTag[1], ""+rd.nRow); // nRow will be replaced with Anno from nRow
				}
			}
			grpObj.add(pos0, rd.geneTag[0], pos1, rd.geneTag[1]);
		}
		if (clustArr.size()==0) return;
		
		Globals.rprt("Sort results");
		Collections.sort(clustArr, new Comparator<Clust> () {
			public int compare(Clust a, Clust b) {
				return a.rGrpN - b.rGrpN;
			}
		});
		if (hasKey)  {
			Globals.rprt("Add annotations");
			buildXannoMap();		 	   // Annotations from 1st loop
		}
		
		Globals.rprt("Finish");
		System.gc();
		int cnt=0;
		if (!bLinkRow) {
			for (Clust grp : clustArr) {
				if (++cnt % Q.INC == 0) Globals.rprt("Finish " + cnt);
				grp.finish();
			}
		}
		else {
			for (Clust grp : clustArr) {
				if (++cnt % Q.INC == 0) Globals.rprt("Finish link " + cnt);
				new Links(grp);
			}
		}
		Globals.rclear();
	} 
	catch (Exception e) {ErrorReport.print(e, "Creating cluster report"); }
	catch (OutOfMemoryError e) {ErrorReport.print(e, "Out of memory creating cluster report");}
	}

	
	/******************************************************
	 * Intialized in Okay; genes added in buildCompute; add anno here
	 * Get All_Anno key/values; uses spAnnoMap; puts HTML <br> between keyword values
	 */
	private boolean buildXannoMap() {
	try {
		TmpRowData rd = new TmpRowData(tPanel);
		
		String notFoundKey="";
		for (int nSp=0; nSp<nSpecies; nSp++) {
			if (spInputKey[nSp].isEmpty()) continue;
			
			HashMap <String, String> nAnnoMap = spAnnoMap.get(nSp).nAnnoMap; 
			
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
						else if (v.length()>=descWidth) 		wval = Jhtml.wrapLine(v, descWidth, htmlBr+htmlSp);
						else 									wval = v;
						
						if (!allVal.equals("")) allVal += "<br"; // between Keyword values
						allVal += wval;
						
						keyCntMap.put(key, keyCntMap.get(key)+1); 
					}
				}
				if (allVal=="") allVal = noAnno; 
				nAnnoMap.put(tag, allVal);		// replace row number with anno 
			}
			int cutoff = (int) ((double)nAnnoMap.size()*0.01); 
			Globals.tprt("Keyword cutoff: " + cutoff + " from " + nAnnoMap.size());
			for (String key : keyCntMap.keySet()) {	
				if (keyCntMap.get(key)<cutoff) 	
					notFoundKey +=  "\n   " + spAbbr + ": " + key + " Count: " + keyCntMap.get(key);
			}
		}
		if (!notFoundKey.equals("")) {
			Popup.showWarningMessage("Keywords not found or low usage: " + notFoundKey);
			return false;
		}
		return true;
	} catch (Exception e) {ErrorReport.print(e, "Build Anno Map"); return false;}
	}
	/*************************************************
	 * During buildCompute, the links[grpSz][nSpecies] was build; 
	 * 		this merges the rows that share links, and creates new ArrayList for display
	 * Is a class to consolidate its methods
	 */
	private class Links {
		private Clust grp;
		private ArrayList <String []> mrows = new ArrayList <String []> (); // merged links; 
		private HashMap <String, Boolean> tupleMap; // tag:tag, true - can add, false - been added (still used for lookup)
		
		private Links(Clust grp) {
			this.grp = grp;
			tupleMap = grp.tupleMap; // created in Clust.add
		
			merge();
			finish();
		}
		
		/***********************************************/
		private void merge() {
		try {
			for (int spR=0; spR<nSpecies-1; spR++) {// last sp will be auto added by others
				TreeMap <String, Integer> spTagSetR = grp.spTagMap.get(spR); // process tags in sorted tree order
				
				for (String tagR : spTagSetR.keySet()) {
					boolean found=true;			// a tag can be in multiple linked rows
					while (found) {
						String [] rowTags = new String [nSpecies];
						for (int i=0; i<nSpecies; i++) rowTags[i]="";
						
						found = mergeOneRow(rowTags, tagR, spR);
					
						if (found) mrows.add(rowTags);
					}
				}
				if (tupleMap.size()==0) break;
			}
			for (String tuple: tupleMap.keySet()) 
				if (tupleMap.get(tuple)) Globals.eprt("Tuple not added: " + tuple);
		}
		catch (Exception e) {ErrorReport.print(e, "Merge linked rows"); }
		}
		// Fill rowTags where tagR is one of the pair and all added tags are linked 
		private boolean mergeOneRow(String [] rowTags, String tagR, int spR) {//sp=pos=col
			try {	
				rowTags[spR] = tagR;
				int cntAdd = 1; 						// 1st has been added
				
				for (int spP=0; spP<nSpecies; spP++) { 	// for each species list of tags, add one linked if available
					if (spP==spR) continue;
					
					TreeMap <String, Integer> spTagSetP = grp.spTagMap.get(spP); 
					for (String tagP : spTagSetP.keySet()) {
						String tuple = (spR<spP) ? tagR + tup + tagP : tagP + tup + tagR;
						if (!tupleMap.containsKey(tuple)) continue;
						if (!tupleMap.get(tuple)) continue; 		// been added
						
						// does this tagP have pairs with all other tags in row?
						boolean bPass = (cntAdd>0) ? isLinked(rowTags, spR, spP, tagP) : true; 
						if (!bPass) continue;
						
						rowTags[spP] = tagP;
						tupleMap.put(tuple, false); 
						cntAdd++;
						
						break; 
					}
				}
				if (cntAdd==1) return false;			// nothing added
				if (cntAdd==nSpecies) {cntFullLink++; grp.cntFull++; return true;} 		// all column with tag
				
				/* For each empty column, is there an untouched linked tag that can be added? */
				for (int spE=0; spE<nSpecies; spE++) { 
					if (!rowTags[spE].equals("")) continue; 
					
					TreeMap <String, Integer> spTagSet = grp.spTagMap.get(spE); // check tags for this column
					for (String tagE : spTagSet.keySet()) {
						if (isLinked(rowTags, spE, tagE)) { 
							cntAdd++;
							rowTags[spE] = tagE; 						
							break;
						}
					}
				}
				if (cntAdd==nSpecies) {cntFullLink++;  grp.cntFull++;}
				return true;
			}
			catch (Exception e) {ErrorReport.print(e, "Merge linked rows"); return false;}
		}
		/* is tagP with all other tags in row excluding ref? */
		private boolean isLinked(String [] rowTags, int spR, int spP, String tagP) {
			HashSet <String> rmTup = new HashSet <String> (); // do not set removed until tuple gets added
			
			for (int sp=0; sp<nSpecies; sp++) {
				if (sp==spR || sp==spP) continue; 		// this pair is linked
				if (rowTags[sp].equals("")) continue; 
				
				String tuple = (sp<spP) ? rowTags[sp] + tup + tagP : rowTags[sp] + tup + tagP;
	
				if (!tupleMap.containsKey(tuple)) return false;
				
				rmTup.add(tuple);
			}
			for (String tuple : rmTup) tupleMap.put(tuple, false);
			return true;
		}
		/* is tagE linked with all other column tags including ref? */
		private boolean isLinked(String [] rowTags, int spP, String tagP) {
			HashSet <String> rmTup = new HashSet <String> ();
			
			for (int sp=0; sp<nSpecies; sp++) {
				if (sp==spP || rowTags[sp].equals("")) continue; // fill rowTag[spN] if possible
				
				String tuple = (sp<spP) ? rowTags[sp] + tup + tagP : tagP + tup + rowTags[sp];
					
				if (!tupleMap.containsKey(tuple)) return false;
				
				rmTup.add(tuple);
			}
			for (String tuple : rmTup) tupleMap.put(tuple, false);
			
			return true;
		}
		
		private void finish() {	
			for (int sp=0; sp<nSpecies; sp++) {
				ArrayList <String> tagArr = new ArrayList <String> (); // one per species
				
				for (int r=0; r<mrows.size(); r++) {	// entry for each row
					String [] row = mrows.get(r);
					
					if (row[sp]==null || row[sp].equals("")) tagArr.add(htmlSp);
					else tagArr.add(row[sp]);
				}
				grp.finish(tagArr);
			}	
		}
	}
	
	/******************************************************************
	 * Classes for Build data structures
	 * e.g. spTagSet
	 * 		0 17.45
	 * 		1 11.22 11.23
	 * 		2 14.5
	 * spTagArr ordered				unordered;
	 * 		0 17.45 17.45 17.45		0 17.45 	
	 * 		1 11.22 11.23			1 11.22 11.23
	 * 		2 ""     ""   14.5		2 14.5
	 * Print by column 				
	 * 		17.45 11.22 ""			17.45  11.22  14.5
	 * 		17.45 11.23 ""			 ""    11.23  ""
	 * 		17.45 ""    14.5
	 */
	private class Clust {
		private int rGrpN=0, rGrpSz=0; 	
		private ArrayList <TreeMap <String, Integer>> spTagMap;    // pos, merged unique tags; pos=species column, entries no row based
		private ArrayList <ArrayList <String>> spTagArr;	// pos, not unique tags; pos=species column, entry for each row
		private HashMap <String, Boolean> tupleMap; // tag:tag, true - can add, false - been added (still need for lookup)
		private int cntFull=0;
		
		private Clust(int grpN, int grpSz) {
			rGrpN = grpN;
			rGrpSz = grpSz;
			spTagMap = new ArrayList <TreeMap <String, Integer>> (nSpecies); 
			for (int i=0; i<nSpecies; i++) spTagMap.add(new TreeMap <String, Integer> ());
			
			spTagArr = new ArrayList <ArrayList <String>> (nSpecies); // do not initialize; see finish
			
			if (bLinkRow) {
				try {
					tupleMap = new HashMap <String, Boolean> (grpSz);
				}
				catch (OutOfMemoryError e) {
					String msg = "Out of memory: cannot run Gene Links with this Cluster results" + 
							"\nGroup too big: Group " + grpN + " size " + grpSz +
							"\nIncrease memory or use Sorted Order";
					Globals.eprt(msg);
					Popup.showWarningMessage(msg);
					bLinkRow=false;
				}
			}
		}
		private void add(int pos0, String tag0, int pos1, String tag1) {
			if (!spTagMap.get(pos0).containsKey(tag0)) spTagMap.get(pos0).put(tag0, 1);	
			else {
				TreeMap <String, Integer> map = spTagMap.get(pos0);
				map.put(tag0, map.get(tag0)+1);
			}
			if (!spTagMap.get(pos1).containsKey(tag1)) spTagMap.get(pos1).put(tag1, 1);
			else {
				TreeMap <String, Integer> map = spTagMap.get(pos1);
				map.put(tag1, map.get(tag1)+1);
			}
			
			if (bLinkRow) {
				String tuple = (pos0<pos1) ? (tag0 + tup + tag1) : (tag1 + tup + tag0);
				tupleMap.put(tuple, true);
			}
		}	
		// Transfer to geneVec; this is so an ArrayList can be used for bShowLink; but also add #occurrences here
		private void finish() {
			for (int sp=0; sp<nSpecies; sp++) {
				TreeMap <String, Integer>  tagMap = spTagMap.get(sp);
				
				ArrayList <String> tagArr = new ArrayList <String> (tagMap.size());
				for (String tag : tagMap.keySet()) {// Zoutput.addAnnoCols splits on  "(" for tag lookup
					String xtag = String.format("%s %s%d)", tag, nLink, tagMap.get(tag)); // don't pad; nLink="("
					tagArr.add(xtag);
				}	
				spTagArr.add(tagArr);
			}
		}
		// Newly created by Links
		private void finish(ArrayList <String> tagArr) {// enter in species position order
			spTagArr.add(tagArr);
		}
		
	} // end group class
	
	// one per species where nAnnoMap contains gene values of keyword
	private class Anno { 
		private HashMap <String, String> nAnnoMap  = new HashMap <String, String> (); // gene#, value
	}
	/***********************************************************************
	 * XXX Output Is a class to consolidate its methods
	 */
	private class Zoutput {
		int gnNum=0; 
		
		StringBuffer hBodyStr= new StringBuffer();
		
		private void runReport() {
			String report = reportHTML();
			if (report==null) return;
			
			if (bPopHtml) {
				util.Jhtml.showHtmlPanel(null, title, report); 
			}
			else {
				outFH = getFileHandle();
				if (outFH==null) return;
			
				outFH.println(report);
				outFH.close();
			}
		}
		
		/***********************************************************
		 * build HTML for popup and file; uses geneVec and spAnnoMap
		 */
		private String reportHTML() {
		try {
		// column headings
			String hColStr="";
			int colSpan=0;
			
			hColStr += "<tr>"; // no row column
			for (int x=0; x<nSpecies; x++) {
				colSpan++;
				String name = spPanel.getSpName(x);
				hColStr += "<td><b><center>" + name + "</center></b>";
			}
			for (int x=0; x<nSpecies; x++) { // Annotation
				if (!spInputKey[x].isEmpty()) {
					String name = spPanel.getSpName(x);
					hColStr += "<td><b><center>" + name + " " + spInputKey[x].trim() + "</center></b>";
					colSpan++;
				}
			}
			hColStr += "\n";
			
		// Table
			while (true) { // loop through geneVec; while loop because the counts are different for Coset and Gene
				if (gnNum>=clustArr.size()) break;
				
				Clust grpObj = clustArr.get(gnNum++);
				if (gnNum%100 == 0) Globals.rprt("Processed " + gnNum + " groups");
				
				if (!bShowBorder) hBodyStr.append("<tr><td colspan=" + colSpan + "><hr>");
				if (bShowGrp) {
					String x = (grpObj.cntFull>0 && nSpecies>2) ? String.format(" (%d asr)", grpObj.cntFull) : "";
					String setsz = String.format("<b>%s%s</b> %s", isGrp, (grpObj.rGrpSz + Q.GROUP + grpObj.rGrpN), x);	
					hBodyStr.append("<tr><td colspan=" + colSpan + ">" + setsz);
				}
		/* Output row  of columns for current gnObj */
				hBodyStr.append("<tr>"); // no row number
				
				for (int posSp=0; posSp<nSpecies; posSp++) { // Print by Column
					ArrayList <String> tagArr = grpObj.spTagArr.get(posSp);
					
					if (tagArr.size()==0) {	// no gene for this species
						hBodyStr.append("\n  <td>" + htmlSp + "</td>");      
					}
					else {										    // one or more genes in non-ref column
						hBodyStr.append("\n  <td style='white-space: nowrap;'>");
						
						int j=0;
						for (String tag : tagArr) {				// all tags in this column; &nbsp has been used for blank
							if (j>0) hBodyStr.append(htmlBr);
							hBodyStr.append(tag);
							j++;
						}
					}		
				} // end species loop to write gene# columns for this row
				if (hasKey) addAnnoCols(grpObj);
				hBodyStr.append("\n");
			} // End loop geneVec
			
			clearDS(); Globals.rclear();
			
			// Finish
			String head = strHeadHTML(); // title, filters, stats, table start
			String tail= strTailHTML();
			
			if (gnNum==0) { // this may happen, as all included must be in a cluster
				if (!bPopHtml) {
					String msg = "No reference genes fit the Display options\n" + strTitle("", "\n", gnNum);
					JOptionPane.showMessageDialog(null, msg, "No results", JOptionPane.WARNING_MESSAGE);
					return null;
				}
				return head + "No reference genes fit the Display options" + tail;
			}
			return head + hColStr + hBodyStr.toString() + tail; // for file or popup
		} 
		catch (Exception e) {ErrorReport.print(e, "Build HTML"); return null;}
		}
		private String strHeadHTML() {
			String head = "<!DOCTYPE html><html>\n<head>\n"
					+ "<title>" + title + "</title>\n" + "<style>\n" + "body {font-family: monospaced;  font-size: 10px; }\n";		
			if (bShowBorder) {
				head += ".ty {border: 1px solid black; border-spacing: 1px; border-collapse: separate; margin-left: auto; margin-right: auto;}\n";
				head += ".ty td {border: 1px solid black; padding: 5px; border-collapse: separate; white-space: nowrap; }";
			}
			else  {
				head += ".ty {border: 1px solid black; border-spacing: 10px 0px; border-collapse: separate; margin-left: auto; margin-right: auto;}\n";
				head += ".ty td {border: 0px solid black; white-space: nowrap; }";
			}
			head += "\n</style>\n</head>\n<body>\n" + "<a id='top'></a>\n";
			
			head += "<center>" + strTitle("", htmlBr, gnNum) + "</head>"; // </center> in tail
			
			head +=  String.format("%s%s%,d Cluster Hits Groups", htmlBr, htmlBr, gnNum);
			String note = (bLinkRow && cntFullLink>0 && nSpecies>2) ? String.format("; %,d All species rows (asr)", cntFullLink) : "";
			head +=  note;
			
			head += "<table class='ty'>\n"; 
			
			return head;
		}
		private String strTailHTML() {
			String tail="</table>";
			if ((gnNum>100) && bExHtml) tail += "<p><a href='#top'>Go to top</a>\n";
			tail += "</center></body></html>\n";
						
			return tail;
		}
		/***********************************************************
		 * Columns of the user input keyword=value
		 * 	already has <br> between a species keyword values, e.g. ID<br>Desc
		 */
		private void addAnnoCols(Clust gnObj) {
		try {
			HashSet <String> tagsAdd = new HashSet <String> ();
			for (int nSp=0; nSp<nSpecies; nSp++) {
				if (spInputKey[nSp].isEmpty()) continue; // no keyword set
				
				ArrayList <String> tagSet = gnObj.spTagArr.get(nSp); // print in same order as species column
				
				if (tagSet.size()==0) {	 // no genes
					if (bExHtml)	hBodyStr.append("\n  <td>" + htmlSp);  // nl make file manually editable
					else 			hBodyStr.append("<td>" + htmlSp); 
					continue;
				}
				HashMap <String, String> nAnnoMap = spAnnoMap.get(nSp).nAnnoMap;
				String anno="";
				
				for (String tag : tagSet) {
					if (tag.equals("") || tag.startsWith(htmlSp)) continue; // blank lines in Link rows as place holders
					if (tagsAdd.contains(tag)) continue; // lots of dups with Link Row
					tagsAdd.add(tag);
					
					String maptag = (!tag.contains(nLink)) ? tag 
						    : tag.substring(0, tag.indexOf(" ")).trim(); // (n) was added in Clust.finish
					String showtag = "";
					if (bShowGene) {
						String sp="";
						for (int i=0; i<padWithSpace-tag.length(); i++) sp+=htmlSp;
						showtag = maptag + " " + sp;
					}
					if (!anno.equals("")) anno += htmlBr;
					
					if (nAnnoMap.containsKey(maptag))  
						 anno += showtag + nAnnoMap.get(maptag).trim() + semiC;
					else Globals.tprt("Error: no nAnnoMap for " + tag + " (" + maptag + ")");
				}
				if (bLinkRow) hBodyStr.append("\n  <td style=\"vertical-align: text-top;\">" + anno);
				else          hBodyStr.append("\n  <td>" + anno);
			} // end species loop to write annotation columns for this row
		}
		catch (Exception e) {ErrorReport.print(e, "Build Anno Columns");}
		}
		/**************************************************************
		 * First lines: title in HTML and remarks in TSV
		 */
		private String strTitle(String remark, String br, int gnNum) { // br is "\n" if showing error, else it is <br>
			String head = remark + title ;
			br = br + remark;	
			
			String sum = tPanel.theSummary;
			if (sum.length()>40) {
				int index = sum.indexOf("Cluster");
				String part1 = sum.substring(0,index);
				String part2 = sum.substring(index);
				sum = part1 + br + "    " + part2;
			}
			head += br + "Query: " + sum;
			String x = (bLinkRow) ? "Link rows" : "Unlinked unique";
			x =   br + "Display: " + x;
			
			return head + x;
		}
		
		/* getFileHandle */
		private PrintWriter getFileHandle() {
	    	String saveDir = Globals.getExport(); 
			
	    	String type;
	    	type = "Clust";
	    	
	    	String name =  filePrefix + type;
	    	String fname = (bExHtml) ? name + ".html" :  name + ".tsv";
	    	
			JFileChooser chooser = new JFileChooser(saveDir);
			chooser.setSelectedFile(new File(fname));
			if(chooser.showSaveDialog(tPanel.queryFrame) != JFileChooser.APPROVE_OPTION) return null;
			if(chooser.getSelectedFile() == null) return null;
			
			String saveFileName = chooser.getSelectedFile().getAbsolutePath();
			if (!saveFileName.endsWith(".html")) saveFileName += ".html";
			
			if (new File(saveFileName).exists()) {
				if (!Popup.showConfirm2("File exists","File '" + saveFileName + "' exists.\nOverwrite?")) return null;
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
	private TableMainPanel tPanel;
	private SpeciesPanel spPanel;
	private JPanel mainPanel;
	
	private JLabel [] radSpecies=null;
	private JTextField [] txtSpKey=null;
	
	private JTextField txtDesc = null;
	private JCheckBox chkTrunc = null, chkShowGene = null;
	
	private JRadioButton radOrder, radLink;
	private JCheckBox chkBorder = null, chkShowNum = null;
	
	private JRadioButton btnPop = null;
	private JButton btnOK=null, btnCancel=null, btnClear=null, btnInfo=null;	
}
