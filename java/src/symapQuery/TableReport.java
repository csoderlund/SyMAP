package symapQuery;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
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
 */
public class TableReport extends JDialog {
	private static final long serialVersionUID = 1L;
	private static String filePrefix = "ReportExport";
	private static final int pop_html = 0, ex_html = 1, ex_csv = 2;
	private static final int run_col = 0,  run_hit =2;
	private static final String splitC = ";"; 	// local delimiter for lists
	private static final String keyC = ","; 	// input key parameter
	private static final String semiC = ";"; 	// fixed delimiter
	
	private TableDataPanel tdp;
	private SpeciesSelectPanel ssp;
	private JPanel mainPanel;
	
	private int nModeOut=pop_html, nModeRun=run_hit, nRows=0;
	private String title;
	
	private int nCoGenes=2;		
	private JRadioButton [] radSpecies=null;
	private JTextField [] txtSpKey=null;
	
	private JCheckBox chkCol; // Collinear
	private JTextField txtNum;
	
	private JButton btnOK=null, btnCancel=null, btnInfo=null;
	
	private PrintWriter outFH = null;
	
	private int nSpecies=0, nRefSp=0;
	private String [] spKey; 
	private String refSp="", refSpAbbr=""; // for trace output and file name
	private boolean hasKey=false;
	private HashMap <Integer, Integer> spIdxMap = new HashMap <Integer, Integer> (); // idx, index
	private HashMap <Integer, Integer> spOrdMap = new HashMap <Integer, Integer> (); // index, idx
	
	private Vector <Gene> geneVec = new Vector <Gene> ();
	private HashMap <Integer, Anno> spAnnoMap = new HashMap <Integer, Anno> ();
	
	protected TableReport(TableDataPanel tdp) {
		this.tdp = tdp;
		ssp = tdp.theQueryPanel.speciesPanel;
		
		setModal(false);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		
		if (tdp.isCollinearSz) 	{nModeRun=run_col; title="Collinear";}
		//else if (tdp.isGroup) 	{nModeRun=run_grp; title="Group";}
		else 					{nModeRun=run_hit; title="Gene";}
		
		title = title + " Report";
		setTitle(title);
		title = "SyMAP " + title;
		
		mainPanel = Jcomp.createPagePanel();
		createOptions();
		createOutput();
		
		pack();
		this.setResizable(false);
		setLocationRelativeTo(null); 
	}
	// Reference and keywords
	private void createOptions() {
		nSpecies = tdp.theQueryPanel.nSpecies;
		spKey = new String [nSpecies];
		for (int i=0; i<nSpecies; i++) spKey[i]="";
		
		radSpecies = new JRadioButton [nSpecies];
		txtSpKey =   new JTextField [nSpecies];
		
		ButtonGroup bg = new ButtonGroup();
		int width=100;
		JPanel row = Jcomp.createRowPanel();
		JLabel label = new JLabel("Select Ref");
		row.add(label);
		if (width > label.getPreferredSize().width) 
			row.add(Box.createHorizontalStrut(width-label.getPreferredSize().width));
		row.add(new JLabel("Gene Annotation Keywords"));
		mainPanel.add(row);
		
		for(int x=0; x<nSpecies; x++) {
			row = Jcomp.createRowPanel();
			
			radSpecies[x] = new JRadioButton(ssp.getSpName(x)); radSpecies[x].setBackground(Color.white);
			bg.add(radSpecies[x]);
			radSpecies[x].setSelected(false);
			
			txtSpKey[x] = new JTextField(15);
			
			row.add(radSpecies[x]); 
			if (width > radSpecies[x].getPreferredSize().width) 
				row.add(Box.createHorizontalStrut(width-radSpecies[x].getPreferredSize().width));
			row.add(txtSpKey[x]);
			mainPanel.add(row);
			
			int spIdx = ssp.getSpIdx(x);
			spIdxMap.put(spIdx, x);
			spOrdMap.put(x, spIdx);
		}
		radSpecies[nRefSp].setSelected(true);
		
		mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
	}
	// output and bottom buttons
	private void createOutput() {
		JPanel optPanel = Jcomp.createPagePanel();
		
		chkCol = Jcomp.createCheckBox("Show collinear set", true); 
		txtNum = Jcomp.createTextField(nCoGenes+"", 3);
		txtNum.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				String num = txtNum.getText();
				try {nCoGenes = Integer.parseInt(num);}
				catch (Exception e) {txtNum.setText(nCoGenes+"");};
			}
		});
		
		
		JPanel row = Jcomp.createRowPanel();
		
		row.add(new JLabel("Output")); row.add(Box.createHorizontalStrut(5));
		
		JRadioButton btnPop = new JRadioButton("Popup"); btnPop.setBackground(Color.white);
		btnPop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				nModeOut=pop_html;
			}
		});
		row.add(btnPop); row.add(Box.createHorizontalStrut(5));
		
		JRadioButton btnHTML =  new JRadioButton("HTML File");btnHTML.setBackground(Color.white); //CAS547 white for linux
		btnHTML.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				nModeOut=ex_html;
			}
		});
		row.add(btnHTML); row.add(Box.createHorizontalStrut(5));
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(btnHTML);
        grp.add(btnPop);  
    	btnPop.setSelected(true);

        optPanel.add(row);
        
        if (nModeRun==run_col) {
			JPanel row0 = Jcomp.createRowPanel();
			//row0.add(new JLabel("Collinear "));
			//row0.add(Box.createHorizontalStrut(10));
			//row0.add(new JLabel("Minumum#"));
			//row0.add(txtNum);
			//row0.add(Box.createHorizontalStrut(10));
			row0.add(chkCol); 
			
			optPanel.add(Box.createVerticalStrut(5));
			optPanel.add(row0);
			optPanel.add(Box.createVerticalStrut(5));
		}
    // buttons
    	btnOK = new JButton("OK");
    	btnOK.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				okay();
			}
		});
		btnCancel = new JButton("Cancel");
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
		btnOK.setPreferredSize(btnCancel.getPreferredSize());
		btnOK.setMaximumSize(btnCancel.getPreferredSize());
		btnOK.setMinimumSize(btnCancel.getPreferredSize());
		
		btnInfo = Jcomp.createIconButton("/images/info.png", "Quick Help Popup");
		btnInfo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				popupInfo();
			}
		});
		btnInfo.setPreferredSize(btnInfo.getPreferredSize());
		btnInfo.setMaximumSize(btnInfo.getPreferredSize());
		btnInfo.setMinimumSize(btnInfo.getPreferredSize());
		
		JPanel buttonPanel = Jcomp.createRowPanel();
		buttonPanel.add(btnOK);			buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnCancel);		buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnInfo);
		
		mainPanel.add(optPanel); 	mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
		mainPanel.add(buttonPanel);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		add(mainPanel);
	}
	private void popupInfo() {
		String msg = 
				"Reference: All relevant gene matches to the reference gene will be listed.";
		msg +=  "\n\nBy default, only the Gene# will be shown."
				+ "\nEnter a comma-separated list of keyword found in the 'All_Anno' column; "
				+ "\n   a column in the HTML table will be listed with the values."
				+ "\n   The keywords are NOT checked for correctness.";
		msg +=  "\n\nIf the 'Collinear Size' query option was checked, the following option will be shown:"
				+ "\n   Show Collinear set: if checked, the output will include the set number and size.";
		msg += "\n\nSee ? for details.\n";
		
		util.Utilities.displayInfoMonoSpace(this, "Quick Help", msg, false);
	}
	/**************************************************************8
	 * Shared
	 */
	private void okay() {	
		for (int x=0; x<nSpecies; x++) { // ref and keywords
			if (radSpecies[x].isSelected()) nRefSp=x;
			
			spAnnoMap.put(x, new Anno()); // for possible gene anno per species
			
			spKey[x] = txtSpKey[x].getText().trim(); 
			if (spKey[x].contains(" ") && !spKey[x].contains(keyC)) {
				Utilities.showInfoMessage("Bad Key",
					"Keys must be separated by ',' and no space within a key '" + spKey[x] + "'");
				return;
			}
			if (!spKey[x].isEmpty()) hasKey=true;
		}
		refSp = ssp.getSpName(nRefSp);	   // use selected set above
		refSpAbbr = ssp.getSpAbbr(nRefSp); // used in file name
		
		if (nModeOut!=pop_html) {			// file
			outFH = getFileHandle();
			if (outFH==null) return;
		}
		
		setVisible(false);	// can do other things if set false here
		
		Thread inThread = new Thread(new Runnable() {
    		public void run() {
			try {
				tdp.setPanelEnabled(false);
				btnOK.setEnabled(false); btnCancel.setEnabled(false); 
				
				if (nModeOut==pop_html) 		makeHTMLpopup();
				else if (nModeOut==ex_html) 	makeHTMLexport();
				else if (nModeOut==ex_csv) 		makeCSVexport();
				
				tdp.setPanelEnabled(true);
			} catch(Exception e) {ErrorReport.print(e, "Ok report");}
	    	}
	    });
	    inThread.start();
	}
	private PrintWriter getFileHandle() {
    	String saveDir = Globals.getExport(); // CAS547 change to call globals
		
    	String fname = (nModeOut==ex_html) ? filePrefix + ".html" :  filePrefix + ".csv";
    	fname = refSpAbbr + fname;
    	
    	boolean bAppend = (nModeOut==ex_csv);
    	
		JFileChooser chooser = new JFileChooser(saveDir);
		chooser.setSelectedFile(new File(fname));
		if(chooser.showSaveDialog(tdp.theParentFrame) != JFileChooser.APPROVE_OPTION) return null;
		if(chooser.getSelectedFile() == null) return null;
		
		String saveFileName = chooser.getSelectedFile().getAbsolutePath();
		if (nModeOut==ex_csv) {
			if(!saveFileName.endsWith(".csv")) saveFileName += ".csv";
			symap.Globals.prt("Exporting CSV to " + saveFileName);
		}
		else if (nModeOut==ex_html) {
			if(!saveFileName.endsWith(".html")) saveFileName += ".html";
			symap.Globals.prt("Exporting HTML to " + saveFileName);
		}
		
		boolean append=true;
		if (new File(saveFileName).exists()) {
			if (bAppend) {
				int rc = Utilities.showConfirmFile(saveFileName);
				if (rc==0) return null;
				if (rc==1) append=false;
			}
			else {
				if (!Utilities.showConfirm2("File exists","File '" + saveFileName + "' exists.\nOverwrite?")) return null;
				append=false;
			}
		}
		PrintWriter out=null;
		try {
			out = new PrintWriter(new FileOutputStream(saveFileName, append));
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot open file - " + saveFileName);}
		return out;
    }
    
	/**************************************************************
	 * Create output
	 */
	private void makeHTMLpopup() {
		String html = buildHTML();
		util.Jhtml.showHtmlPanel(getInstance(), (title+" " + refSp), html);
	}
	private void makeHTMLexport() {
		String html = buildHTML();
		outFH.println(html);
		outFH.close();
	}
	private void makeCSVexport() {
		buildGeneVec();
		// TODO make into csv
	}
	private TableReport getInstance() { return this; }
	/***********************************************************
	 * build HTML for popup and file; uses geneVec and spAnnoMap
	 */
	private String buildHTML() {
	try {
		buildGeneVec();
		
		nRows = tdp.getNumResults();
		String tab = (nModeOut==pop_html) ? title : (refSpAbbr + filePrefix);
		String head = "<!DOCTYPE html><html>\n<head>\n"
				+ "<title>" + tab + "</title>\n"
				+ "<style>\n"
				+  "body {font-family: monospaced;  font-size: 10px; }\n"
				+ ".ty    {border: 1px  solid black; border-spacing: 1px; border-collapse: collapse;margin-left: auto; margin-right: auto;}\n"
				+ ".ty td {border: 1px  solid black; padding: 5px; white-space: nowrap; }" 
				+ "</style>\n</head>\n<body>\n"
				+ "<a id='top'></a>\n";
		head   += "<p><center><b>" + title + "</b></center>"
		        + "<br><center>Filter: " + tdp.theSummary + "</center>\n"
		        + "<p><center><table class='ty'>\n";
		
		String tail = "</table></center>";
		if (nRows>100 && nModeOut==ex_html) tail += "<p><a href='#top'>Go to top</a>\n";
		tail += "</body></html>\n";
		
		if (geneVec.size()==0) return head + "<p><big>No gene pairs from selected species</big>" + tail;

		// column headings
		String hColStr="";
		StringBuffer hBodyStr= new StringBuffer();
		
		int colSpan=1;
		hColStr += "<tr><th>&nbsp;"; // for row number
		for (int x=0; x<nSpecies; x++) {
			colSpan++;
			String ref = (x==nRefSp) ? "*" : "";
			hColStr += "<td><center><b>" + ssp.getSpName(x) + ref + "</b></center></td>";
		}
		for (int x=0; x<nSpecies; x++) {
			if (!spKey[x].isEmpty()) {
				hColStr += "<td><center><b>" + ssp.getSpName(x)+ " " + spKey[x].trim() + "</b></center></td>";
				colSpan++;
			}
		}
		hColStr += "\n";
		
		////////// table ////////////////////
		final int sp=10;
		int cnt=0, row=0;
		
		for (Gene gObj : geneVec) { // This gene row with associated genes {collinear}
			if (++cnt%100 == 0) symap.Globals.rprt("Processed " + cnt + " genes");
			
			if (gObj.bBreak) {
				hBodyStr.append("<tr><td colspan=" + colSpan + ">&nbsp;</td>");
				row=0;
			}
			row++;
			hBodyStr.append("<tr><td>" + row + "</td>");
			
			for (int nSp=0; nSp<nSpecies; nSp++) { // Columns
				if (nSp==nRefSp) hBodyStr.append("<td>" + gObj.oTag[nSp] + "</td>"); // only one gene for ref
				else if (gObj.oTag[nSp]=="") hBodyStr.append("<td>&nbsp;</td>");     // no gene for this species
				else {
					hBodyStr.append("<td style='white-space: nowrap;'>");
					String [] gnTok = gObj.oTag[nSp].split(splitC); // list of species genes; chr.genenum
					
					if (nModeRun==run_col) { 
						String [] nctok = gObj.oCoN[nSp].split(splitC); // list of collinear Sz.N
						for (int j=0; j<gnTok.length; j++) {
							if (j>0) hBodyStr.append("<br>");
							hBodyStr.append(gnTok[j] + "&nbsp;");
							
							if (chkCol.isSelected()) {
								if (gnTok[j].length()<=sp)
									for (int k=gnTok[j].length(); k<sp; k++)  
										hBodyStr.append("&nbsp;");
								hBodyStr.append("c" + nctok[j]);
							}
						}
					}
					else {
						for (int j=0; j<gnTok.length; j++) {
							if (j>0) hBodyStr.append("<br>");
							hBodyStr.append(gnTok[j]);
						}
					}
				}		
			}
			
			if (hasKey) { // already has <br> between a species keyword values, e.g. ID<br>Desc
				for (int nSp=0; nSp<nSpecies; nSp++) {
					if (spKey[nSp].isEmpty()) continue;
					if (gObj.oTag[nSp].isEmpty()) {
						hBodyStr.append("<td>&nbsp;</td>");
						continue;
					}
					
					HashMap <String, String> nAnnoMap = spAnnoMap.get(nSp).nAnnoMap;
					String anno="";
					String [] glist = gObj.oTag[nSp].split(splitC); // already has keyword values delimited by <br>
					for (String gn : glist) {
						if (!anno.isEmpty()) anno +=  semiC + "<br>";
						anno += nAnnoMap.get(gn).trim();
					}
					hBodyStr.append("<td>" + anno + "</td>");
				}
			}
			hBodyStr.append("\n");
		}
		String out = head + hColStr + hBodyStr.toString() + tail;
		symap.Globals.prt("Complete " + refSp + " HTML                                ");
		clear();
		return out;
	} catch (Exception e) {ErrorReport.print(e, "Build HTML"); return null;}
	}
	
	/***********************************************************
	 * Used by all output to build rows of the report
	 */
	private void buildGeneVec() {
	try {	
		// Get all rows
		Vector <TmpRowData> rdVec = new Vector <TmpRowData>  ();
		for (int r=0; r<tdp.theTableData.getNumRows(); r++) {
			TmpRowData trd = new TmpRowData(tdp);
			trd.loadRow(r);
			if (!trd.geneTag[0].contains(Q.empty) && 
				!trd.geneTag[1].contains(Q.empty)) rdVec.add(trd);
		}
		// build set of reference genes 
		HashMap <String, Integer> geneRowMap = new HashMap <String, Integer> ();
		HashSet <String> tmpSet = new HashSet <String> ();
		
		int spIdx = spOrdMap.get(nRefSp);
		HashMap <String, String> nAnnoMap = spAnnoMap.get(nRefSp).nAnnoMap;
		
		for (TmpRowData rd : rdVec) {
			String tag = null;
			if      (rd.spIdx[0]==spIdx)  tag = rd.geneTag[0];
			else if (rd.spIdx[1]==spIdx)  tag = rd.geneTag[1];	
			
			if (tag!=null && !tmpSet.contains(tag)) {
				Gene g = new Gene(nRefSp, tag);
				geneVec.add(g);
				tmpSet.add(tag);
				
				if (hasKey && !spKey[nRefSp].isEmpty()) {
					nAnnoMap.put(g.rTag, ""+rd.nRow); // nRow will be replaced with Anno from nRow
				}
			}
		}
		
		tmpSet.clear();
		if (geneVec.size()==0) return;
			
		Collections.sort(geneVec, new Comparator<Gene> () {
			public int compare(Gene a, Gene b) {
				if (a.rChr.equals(b.rChr)) {
					if (a.rGnum==b.rGnum) return a.rSuffix.compareTo(b.rSuffix);
					else 				  return a.rGnum-b.rGnum;
				}
				int c1=0, c2=0;
				try {
					c1 = Integer.parseInt(a.rChr);
					c2 = Integer.parseInt(b.rChr);
				}
				catch (Exception e) {c1=c2=0;}
				
				if (c1==0) return a.rChr.compareTo(b.rChr);
				else return c1-c2;
			}
		});
		// Build union set of genes (these may shown as multiple unions if they share no coSet)	
		int r=0;
		if (nModeRun==run_col) {
			Gene last=null;
			for (Gene gn : geneVec) {
				geneRowMap.put(gn.rTag, r++); // row assigned to gene
				if (last==null || Math.abs(gn.rGnum-last.rGnum)>1) {
					Union cl = new Union();
					gn.unObj = cl;
				}
				else gn.unObj = last.unObj;
				last = gn;
			}
		}
		else {
			for (Gene g : geneVec) geneRowMap.put(g.rTag, r++); // row assigned to gene
		}
		
		// Add other species
		for (int nSp=0; nSp<nSpecies; nSp++) {
			if (nSp==nRefSp) continue;
			nAnnoMap = spAnnoMap.get(nSp).nAnnoMap;
			
			for (TmpRowData rd : rdVec) {
				int ix = -1;
				if      (rd.spIdx[0]==spIdx && geneRowMap.containsKey(rd.geneTag[0])) ix = 0;
				else if (rd.spIdx[1]==spIdx && geneRowMap.containsKey(rd.geneTag[1])) ix = 1;
				else continue;
				
				int iy = (ix==0) ? 1 : 0;
				int spIdx2 = spOrdMap.get(nSp);
				if (rd.spIdx[iy] != spIdx2) continue;
				
				int spOrd = spIdxMap.get(rd.spIdx[iy]);	
				
				r = geneRowMap.get(rd.geneTag[ix]);
				
				geneVec.get(r).add(spOrd, rd.geneTag[iy], rd.chrNum[iy], rd.collinearSz, rd.collinearN);
				
				if (hasKey && !spKey[nSp].isEmpty() && !nAnnoMap.containsKey(rd.geneTag[iy])) {
					nAnnoMap.put(rd.geneTag[iy],""+rd.nRow);
				}
			}
		}
		
		rdVec.clear();
		geneRowMap.clear();
		
		if (nModeRun==run_col) analyzeCollinear();
		
		if (hasKey) buildAnnoMap();
	} 
	catch (Exception e) {ErrorReport.print(e, "Build Gene Vec"); }
	}
	/******************************************************
	 * Get All_Anno key/values; uses spAnnoMap; puts HTML <br> between keyword values
	 */
	private void buildAnnoMap() {
	try {
		TmpRowData rd = new TmpRowData(tdp);
		
		for (int nSp=0; nSp<nSpecies; nSp++) {
			HashMap <String, String> nAnnoMap = spAnnoMap.get(nSp).nAnnoMap;
			if (nAnnoMap.size()==0) continue;
			
			if (spKey[nSp].isEmpty()) continue;
			
			HashSet <String> keySet = new HashSet <String> ();
			String [] set = spKey[nSp].split(keyC);
			for (int i=0; i<set.length; i++) 
				keySet.add(set[i].trim().toLowerCase());
			
			String spAbbr = ssp.getSpAbbr(nSp);
					
			for (String gnum : nAnnoMap.keySet()) { // process each gene from this species
				int row = Integer.parseInt(nAnnoMap.get(gnum));
				String allanno = rd.getAnnoFromKey(spAbbr, row);
				String hval = "";
				String [] tok = allanno.trim().split(semiC); // GFF input e.g. ID=one; Desc=foo bar; 
				for (String kv : tok) {
					
					String [] vals = kv.split("=");
					if (vals.length==2 && keySet.contains(vals[0].toLowerCase())) {
						String v = vals[1].trim();
						
						String wval = (v.isEmpty() || v.equals(Q.empty)) ? "-" : 
							Jhtml.wrapLine(v, 40, "<br>");
						
						if (hval!="") hval += "<br>"; 
						hval += wval;
					}
				}
				if (hval=="") hval="---";
				nAnnoMap.put(gnum, hval);
				
			}
		}
	} catch (Exception e) {ErrorReport.print(e, "Build Anno Map"); }
	}
	/************************************************************
	 * Determine if any are not sequential (not happening since fixed collinear algo, but leaving to be sure)
	 * Set break line for builtHTML and set < nCoGenes to space 
	 */
	private void analyzeCollinear() {
	try {
		String rmSetStr="";
		int cntRm=0;
		Gene lastg=null;
		
		for (Gene gObj : geneVec) {
			for (int nSp=0; nSp<nSpecies; nSp++) {
				if (nSp==nRefSp) continue;
				if (gObj.oTag[nSp].isEmpty()) continue;
				
				String [] gnTok  = gObj.oTag[nSp].split(splitC); // list of species genes; chr.genenum
				String [] costok = gObj.oCoN[nSp].split(splitC); // list of collinear Sz.N
				String [] chrtok = gObj.oChr[nSp].split(splitC); // list of chr alphanumeric
				
				// Go through species nSp list of genes to check if more then nCoGenes
				for (int n=0; n<gnTok.length; n++) { 
					String key = nSp + "." + chrtok[n] + "." + costok[n]; // species:chr:co#
					HashMap <String, CoSet> coSet = gObj.unObj.coSet;
					
					if (coSet.containsKey(key)) {	
						if (coSet.get(key).gnVec.size()>=nCoGenes) continue;
						
						String chrs = (nRefSp<nSp) ? gObj.rChr+"."+chrtok[n] : chrtok[n]+"."+gObj.rChr;
			
						cntRm++;
						if (cntRm<8) rmSetStr += chrs + "." + costok[n] + " ";
						
						gObj.oTag[nSp]=gObj.oCoN[nSp]=gObj.oChr[nSp]="";
						if (gnTok.length==1) continue;
						
						for (int k=0; k<gnTok.length; k++) { // all but j entries
							if (k!=n) {
								gObj.oTag[nSp]+=gnTok[k] + splitC ;
								gObj.oCoN[nSp]+=costok[k] + splitC;
								gObj.oChr[nSp]+=chrtok[k] + splitC;
							}
						}
					}
					else symap.Globals.dprt("No " + key + " for " + gObj.oTag[n] + " " + gnTok[n]);	
				}
			}
			gObj.bBreak = (lastg!=null && (lastg.unObj!=gObj.unObj || noSharedCo(lastg, gObj))); // BREAK
			lastg = gObj;
		}
		if (cntRm>0) { // this does not seem to happen since v556 collinear fix
			if (cntRm>9) rmSetStr += "...";
			symap.Globals.prt("Remove " + cntRm + ": " + rmSetStr);
		}
	}
	catch (Exception e) {ErrorReport.print(e, "Analyze map"); }
	}
	////////////////////////////////////////////////////////////
	private boolean noSharedCo(Gene gA, Gene gB) {
	try {
		for (int n=0; n<nSpecies; n++) {
			if (n==nRefSp) continue;
			
			String [] nA = gA.oCoN[n].split(splitC);
			String [] nB = gB.oCoN[n].split(splitC);
			String [] cA = gA.oChr[n].split(splitC);
			String [] cB = gB.oChr[n].split(splitC);
			
			for (int a=0; a<nA.length; a++) {
				for (int b=0; b<nB.length; b++) {
				if (!(nA[a].equals("") && cA[a].equals(""))) // neither empty
					if (nA[a].equals(nB[b]) && cA[a].equals(cB[b])) return false;
				}
			}
		}
		return true;
	} 
	catch (Exception e) {ErrorReport.print(e, "Build HTML"); return false;}
	}
	
	/******************************************************************
	 * Classes
	 */
	private class Gene {
		private Gene(int spOrd, String tag) {
			oTag = new String [nSpecies];
			for (int i=0; i<nSpecies; i++) oTag[i] = "";
			
			if (nModeRun==run_col) {
				oCoN = new String [nSpecies];
				oChr = new String [nSpecies];
				for (int i=0; i<nSpecies; i++) oTag[i] = oCoN[i] = oChr[i]="";
			}
			
			if (tag.endsWith("\\.")) tag = tag.substring(0, tag.length()-1);
			rTag = oTag[spOrd] = tag;
			
			String [] tok = tag.split("\\.");
			rChr = tok[0];
			rGnum = Integer.parseInt(tok[1]);
			rSuffix =  (tok.length==3) ? tok[2] : "";
		}
		private void add(int spOrd, String tag, String chr, int coSz, int coN) {
			if (tag.endsWith("\\.")) tag = tag.substring(0, tag.length()-1);
			oTag[spOrd] += tag + splitC;
			
			if (nModeRun!=run_col) return;
			
			String coSzN = coSz + "." + coN;
			
			oCoN[spOrd] += coSzN + splitC;
			oChr[spOrd] += chr  + splitC;
			
			String key = spOrd + "." + chr + "." + coSzN;
			CoSet cs;
			if (unObj.coSet.containsKey(key)) cs = unObj.coSet.get(key);
			else {
				cs = new CoSet(spOrd, coN, coSz, chr);
			    unObj.coSet.put(key, cs);
			}
			cs.add(tag);
		}
		private String rChr, rSuffix, rTag; // ref only
		private int rGnum;
		
		private String [] oTag; // [0]1.62  [1] 1.63.b   [2] 5.78; 8.5522
		private String [] oCoN; // [0]      [1] #7.1     [2] #5.1; 3.182
		private String [] oChr; // [0]      [1] 1		 [2] 5; 8
		private Union unObj=null;     // owned by all genes in "Union"; assigned in buildGeneVec
		private boolean bBreak=false; // space in HTML
	}

	// pointed to by all genes in Union; i.e. consecutive reference genes, which may be different collinear
	private class Union { 
		private HashMap <String, CoSet> coSet = new HashMap <String, CoSet> ();
	}
	private class CoSet {
		private CoSet (int ord, int n, int sz, String chr) {
			spOrd=ord;
			setN=n;
			setSz=sz;
			setChr=chr; // chromosome of non-ref
		}
		private void add(String geneTag) {
			String [] tok = geneTag.split("\\.");
			int gn = Integer.parseInt(tok[1]);
			gnVec.add(gn);
		}
		private int setN=0, setSz=0, spOrd;
		private String setChr;
		private Vector <Integer> gnVec = new Vector <Integer> ();
	}
	private class Anno { // one per species where nAnnoMap contains gene values of keyword
		private HashMap <String, String> nAnnoMap  = new HashMap <String, String> (); // gene#, value
	}
	///////////////////////////////////////////////////////////////
	private void clear() { // spKey is the only DS reused 
		geneVec.clear();
		for (Anno aObj : spAnnoMap.values()) aObj.nAnnoMap.clear();
		spIdxMap.clear();
		spOrdMap.clear();
	}
}
