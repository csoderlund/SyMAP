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
import java.util.Vector;

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
 * CS562 rewrote union logic so unions are exact groups to show. Add TSV, Exact, Union header
 */
public class TableReport extends JDialog {
	private static final long serialVersionUID = 1L;
	private static String filePrefix = "ReportExport";
	private static final int pop_html = 0, ex_html = 1, ex_tsv = 2; 
	private static final String splitC = ";", keyC = ",", semiC = ";"; 	// local delimiter for lists, input key parameter, fixed delimiter
	private static final String isRef="*", isAll="+", isCoSet="#";
	private final int width=100, descWidth=40;
	
	private int outType=pop_html;
	private boolean hasKey=false;
	private boolean bModeGene=false, bModeCoSet=false, bModeGroup=false;
	private boolean bRowAll=false, bCoSetAll=false, bShowCoSet=false ;
	private int grpSz=0;
	private PrintWriter outFH = null;
	private String title;
			
	private int nSpecies=0, posRef=0;  // posRef is the index into Gene.oTag array for reference
	private String refSpName="", refSpAbbr=""; // for trace output and file name
	private String [] spKey; 
	private HashMap <Integer, Integer> spIdxMap = new HashMap <Integer, Integer> (); // idx, index
	private HashMap <Integer, Integer> spOrdMap = new HashMap <Integer, Integer> (); // index, idx
	
	private Vector <Union> unionVec = new Vector <Union> ();
	private Vector <Gene> geneVec = new Vector <Gene> ();
	private HashMap <Integer, Anno> spAnnoMap = new HashMap <Integer, Anno> ();
	
	// CoSet: each gene points to its Union, which contains Collinear sets; see buildXunion: 
	//  CoSet key = rd.chrIdx[i] + "." + rd.chrIdx[j] + "." + rd.collinearN; 
	//  if bCoSetAll key = rd.spIdx[j] . rd.chrIdx[i] + "." + rd.chrIdx[j] + "." + rd.collinearN; 
	// 	            need spIdx to count species in the union
	// Group: see buildCompute
	//	tmpRefMap key; a Gene# can be in the table multiple times, hence, the search key is "tag grpN"
	/***************************************************************************/
	protected TableReport(TableDataPanel tdp) {
		this.tdp = tdp;
		spPanel = tdp.theQueryPanel.speciesPanel;
		
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		
		if (tdp.isCollinearSz) 									 {bModeCoSet=true; title="Collinear";}
		else if (tdp.isGroup && tdp.theQueryPanel.getMultiN()>1) {bModeGroup=true; title="Group"; }
		else 													 {bModeGene =true; title="Gene";}
		
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
		nSpecies = tdp.theQueryPanel.nSpecies;
		spKey = new String [nSpecies];
		for (int i=0; i<nSpecies; i++) spKey[i]="";
		
		radSpecies = new JRadioButton [nSpecies];
		txtSpKey =   new JTextField [nSpecies];
		
		ButtonGroup bg = new ButtonGroup();
		
		JPanel row = Jcomp.createRowPanel();
		JLabel label = Jcomp.createLabel("Reference");
		row.add(label);
		if (Jcomp.isWidth(width, label)) row.add(Box.createHorizontalStrut(Jcomp.getWidth(width, label)));
		
		row.add(Jcomp.createLabel("Gene Annotation Columns", 
				"Enter comma-delimited list of keywords from All_Anno column for one or more species."));
		mainPanel.add(row);
		
		for(int x=0; x<nSpecies; x++) {
			row = Jcomp.createRowPanel();
			
			radSpecies[x] = Jcomp.createRadio(spPanel.getSpName(x)); 
			bg.add(radSpecies[x]);
			row.add(radSpecies[x]); 
			if (Jcomp.isWidth(width, radSpecies[x])) row.add(Box.createHorizontalStrut(Jcomp.getWidth(width, radSpecies[x])));
			
			txtSpKey[x] = Jcomp.createTextField("",15);
			row.add(txtSpKey[x]);
			
			mainPanel.add(row);
			
			int spIdx = spPanel.getSpIdx(x);
			spIdxMap.put(spIdx, x);
			spOrdMap.put(x, spIdx);
		}
		radSpecies[posRef].setSelected(true);
		
		mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
	}
	private void createDisplay() {
		radRowAll = Jcomp.createRadio("Per Row", "Each row must have all species"); 
		radCoSetAll = Jcomp.createRadio("Per Collinear", "Union of overlapping collinear sets must have all species"); 
		radIgnAll = Jcomp.createRadio("Ignore", "Show everything, regardless of missing species"); 
		chkShowCoSet = Jcomp.createCheckBox("Show collinear set", true); 
		
		if (nSpecies==2 && !bModeCoSet) return;
		
		JPanel optPanel = Jcomp.createPagePanel();
		if (nSpecies>2) {	
			JPanel row1 = Jcomp.createRowPanel();
			ButtonGroup ba = new ButtonGroup();
			
			row1.add(Jcomp.createLabel("All species")); 	row1.add(Box.createHorizontalStrut(1));
			row1.add(radRowAll); ba.add(radRowAll); 		row1.add(Box.createHorizontalStrut(3));
			if (bModeCoSet) {
				row1.add(radCoSetAll); ba.add(radCoSetAll); row1.add(Box.createHorizontalStrut(3));
			} 
			row1.add(radIgnAll); ba.add(radIgnAll);			radIgnAll.setSelected(true);
			optPanel.add(row1);
		}
		
        if (bModeCoSet) {
			JPanel row2 = Jcomp.createRowPanel();
			row2.add(chkShowCoSet); 
			optPanel.add(Box.createVerticalStrut(5));
			optPanel.add(row2);
		}
        
        mainPanel.add(optPanel);
		mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
	}
	// output and bottom buttons
	private void createOutput() {
		JPanel optPanel = Jcomp.createPagePanel();
		
		JPanel row = Jcomp.createRowPanel();
		row.add(Jcomp.createLabel("Create")); row.add(Box.createHorizontalStrut(5));
		
		JRadioButton btnPop = Jcomp.createRadio("Popup", "Popup of results"); 
		btnPop.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				outType=pop_html;
			}
		});
		row.add(btnPop); row.add(Box.createHorizontalStrut(5));
		
		JRadioButton btnHTML =  Jcomp.createRadio("HTML File", "HTML file of results");
		btnHTML.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				outType=ex_html;
			}
		});
		row.add(btnHTML); row.add(Box.createHorizontalStrut(5));
		
		JRadioButton btnTSV =  Jcomp.createRadio("TSV File", "File of tab separated values");
		btnTSV.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				outType=ex_tsv;
			}
		});
		row.add(btnTSV); row.add(Box.createHorizontalStrut(5));
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(btnHTML); grp.add(btnPop);   grp.add(btnTSV);
    	btnPop.setSelected(true);

        optPanel.add(row);
    	
    // buttons
    	btnOK = Jcomp.createButton("OK", "Create results");
    	btnOK.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				okay();
			}
		});
		btnCancel = Jcomp.createButton("Cancel", "Cancel action");
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
		
		btnInfo = Jcomp.createIconButton("/images/info.png", "Quick Help Popup");
		btnInfo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				popupInfo();
			}
		});
		
		JPanel buttonPanel = Jcomp.createRowPanel();
		buttonPanel.add(Box.createHorizontalStrut(35));
		buttonPanel.add(btnOK);			buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnCancel);		buttonPanel.add(Box.createHorizontalStrut(45));
		buttonPanel.add(btnInfo);
		
		mainPanel.add(optPanel); 	mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
		mainPanel.add(buttonPanel);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		add(mainPanel);
	}
	private void popupInfo() {
		String msg = 
				"Reference: All displayed gene matches to each displayed reference gene will be listed.";
		msg +=  "\n\nBy default, only the Gene# will be shown."
				+ "\nEnter a comma-separated list of keyword found in the 'All_Anno' column; "
				+ "\n   a column in the HTML table will be listed with the values."
				+ "\n   The keywords are NOT checked for correctness.";
		msg +=  "\n\nThere will be a middle panel if the number of species>2 or Collinear sets will be shown."
				+ "\n   Hover over option for more detail.";
		msg += "\n\nSee ? for details.\n";
		
		util.Utilities.displayInfoMonoSpace(this, "Quick Help", msg, false);
	}
	/**************************************************************8
	 * Shared
	 */
	private void okay() {	
		if (bModeGroup) grpSz = tdp.theQueryPanel.getMultiN();
		bRowAll = radRowAll.isSelected();
		bCoSetAll = radCoSetAll.isSelected();
		bShowCoSet = chkShowCoSet.isSelected();
		
		for (int x=0; x<nSpecies; x++) { // ref and keywords
			if (radSpecies[x].isSelected()) posRef=x;
			
			spAnnoMap.put(x, new Anno()); // for possible gene anno per species
			
			spKey[x] = txtSpKey[x].getText().trim(); 
			if (spKey[x].contains(" ") && !spKey[x].contains(keyC)) {
				Utilities.showInfoMessage("Bad Key",
					"Keys must be separated by ',' and no space within a key '" + spKey[x] + "'");
				return;
			}
			if (!spKey[x].isEmpty()) hasKey=true;
		}
		refSpName     = spPanel.getSpName(posRef); // use selected set above
		refSpAbbr = spPanel.getSpAbbr(posRef); // used in file name
	
		setVisible(false);	// can do other things if set false here
		
		Thread inThread = new Thread(new Runnable() {
    		public void run() {
			try {
				tdp.setPanelEnabled(false);
				btnOK.setEnabled(false); btnCancel.setEnabled(false); 
				
				if (outType==pop_html) 		runHTMLpopup();
				else if (outType==ex_html) 	runHTMLexport();
				else if (outType==ex_tsv) 		runTSVexport();
				
				tdp.setPanelEnabled(true);
			} catch(Exception e) {ErrorReport.print(e, "Ok report");}
	    	}
	    });
	    inThread.start();
	}
	/* getFileHandle */
	private PrintWriter getFileHandle() {
    	String saveDir = Globals.getExport(); // CAS547 change to call globals
		
    	String name =  filePrefix + "_" + refSpAbbr;
    	String fname = (outType==ex_html) ? name + ".html" :  name + ".tsv";
    	
		JFileChooser chooser = new JFileChooser(saveDir);
		chooser.setSelectedFile(new File(fname));
		if(chooser.showSaveDialog(tdp.theParentFrame) != JFileChooser.APPROVE_OPTION) return null;
		if(chooser.getSelectedFile() == null) return null;
		
		String saveFileName = chooser.getSelectedFile().getAbsolutePath();
		if (outType==ex_tsv) {
			if(!saveFileName.endsWith(".tsv")) saveFileName += ".tsv";
		}
		else if (outType==ex_html) {
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
    
	/**************************************************************
	 * Create output
	 */
	private void runHTMLpopup() {
		String html = reportHTML();
		util.Jhtml.showHtmlPanel(getInstance(), (title+" " + refSpName), html); 
	}
	private void runHTMLexport() {
		String html = reportHTML();
		if (html==null) return;
		
		outFH = getFileHandle();
		if (outFH==null) return;
	
		outFH.println(html);
		outFH.close();
	}
	private void runTSVexport() {
		String tsv = reportTSV();
		if (tsv==null) return;
		
		outFH = getFileHandle();
		if (outFH==null) return;
		
		outFH.println(tsv);
		outFH.close();	
	}
	private TableReport getInstance() { return this; }
	/**************************************************
	 * build CSV for file; uses geneVec and spAnnoMap; CAS562 add
	 */
	private String reportTSV() {
		try {
			buildCompute();
			if (geneVec.size()==0) {
				String msg = "No rows with both genes for reference " + refSpName;
				JOptionPane.showMessageDialog(null, msg, "No results", JOptionPane.WARNING_MESSAGE);
				return null;
			}
			
			String tab = "\t"; // everything but row gets tab in front
			
			// Column headings
			String hColStr= "Row#";
			for (int x=0; x<nSpecies; x++) {
				String ref = (x==posRef) ? isRef : "";
				hColStr += tab + spPanel.getSpName(x) + ref;
			}
			for (int x=0; x<nSpecies; x++) {
				if (!spKey[x].isEmpty()) {
					hColStr += tab + spPanel.getSpName(x)+ " " + spKey[x].trim();
				}
			}
			
			// rows
			StringBuffer hBodyStr= new StringBuffer();
			int gnIdx=0, unIdx=0,  gnNum=0, unNum=0;
			int cntOut=0, nAllNonRefSp=nSpecies-1;
			Union lastUn=null, unObj=null;
			
			while (true) {
				if (bModeCoSet && unIdx==unionVec.size() && gnIdx==unObj.geneVec.size()) break;
				if (gnIdx==geneVec.size()) break;
				
				Gene gnObj;
				if (bModeGene) {	// gene report
					gnObj = geneVec.get(gnIdx++);
					if (bRowAll && gnObj.cntSpWithTag()!=nSpecies) continue; 
				}
				else if (bModeGroup) { // Group report
					gnObj = geneVec.get(gnIdx++);
					if (!gnObj.passGrpSz()) continue;
					if (bRowAll && gnObj.cntSpWithTag()!=nSpecies) continue; 
				} 
				else {				// coSet report
					if (unObj==null || gnIdx==unObj.geneVec.size()) { 
						if (unObj!=null) unObj.clear();
						unObj = unionVec.get(unIdx++);
						gnNum=0; gnIdx=0; 
					}
					gnObj = unObj.geneVec.get(gnIdx++); 
					if (bRowAll   && gnObj.cntSpWithTag()!=nSpecies) continue; 
					if (bCoSetAll && gnObj.unionObj.unSpCnt!=nAllNonRefSp) continue;
					 
					if (unObj!=lastUn) {
						unNum++;
						String setsz = String.format("%d [%d]", unNum, unObj.coSetMap.size());	
						if (!bRowAll && !bCoSetAll && unObj.unSpCnt==nAllNonRefSp) setsz += isAll;
						hBodyStr.append(setsz+ "\n");
						lastUn = unObj;
					}
				}
				gnNum++;
				
				if (++cntOut%100 == 0) Globals.rprt("Processed " + cntOut + " genes");
				///////////////////////////////////////////////////////////////////////////
				hBodyStr.append(gnNum);
				
				for (int nSp=0; nSp<nSpecies; nSp++) { // Columns
					hBodyStr.append(tab);
					
					if (nSp==posRef) {
						hBodyStr.append(String.format("%s", gnObj.oTagList[nSp])); // Reference: only one gene for ref
						if (bModeGroup) hBodyStr.append("  Grp# " + gnObj.strGrpN);
					}
					else if (gnObj.oTagList[nSp]=="") {
						hBodyStr.append("    ");   					    // no gene for this species
					}
					else {
						String [] gnTok = gnObj.oTagList[nSp].split(splitC); // list of species genes; chr.genenum
						
						if (bModeCoSet && bShowCoSet) { 
							String [] coSetTok = gnObj.oCoNList[nSp].split(splitC); // list of collinear Sz.N
							
							for (int j=0; j<gnTok.length; j++) {
								if (j>0) hBodyStr.append(semiC+ " ");
								if (bShowCoSet) 
									 hBodyStr.append(String.format("%s %s", gnTok[j], isCoSet + coSetTok[j]));
								else hBodyStr.append(String.format("%s", gnTok[j]));
							}
						}
						else {
							for (int j=0; j<gnTok.length; j++) { 
								if (j>0) hBodyStr.append(semiC+ " ");
								hBodyStr.append(String.format("%s", gnTok[j]));
							}
						}
					}		
				}
				if (hasKey) { // already has <br> between a species keyword values, e.g. ID<br>Desc
					for (int nSp=0; nSp<nSpecies; nSp++) {
						if (spKey[nSp].isEmpty()) continue; // no column for this species keywords
						
						hBodyStr.append(tab);
						if (gnObj.oTagList[nSp].isEmpty()) {// keywords but no values
							hBodyStr.append(" ");
							continue;
						}
						
						HashMap <String, String> nAnnoMap = spAnnoMap.get(nSp).nAnnoMap;
						String anno="";
						String [] glist = gnObj.oTagList[nSp].split(splitC); // already has keyword values delimited by <br>
						for (String gn : glist) {
							if (!anno.isEmpty()) anno +=  semiC + " ";
							anno += nAnnoMap.get(gn).trim();
						}
						hBodyStr.append(anno);
					}
				}
				hBodyStr.append("\n");
			} // End loop thru geneVec
		
			clearDS();
			Globals.rclear();
			
			String head = reportTSVhead((title + " for " + refSpName), gnNum, unNum);
			if (cntOut==0) {
				String msg = "No reference genes fit the display options\n" + head;
				JOptionPane.showMessageDialog(null, msg, "No results", JOptionPane.WARNING_MESSAGE);
				return null;
			}
			return head + hColStr + "\n" + hBodyStr.toString();
		}
		catch (Exception e) {ErrorReport.print(e, "Build HTML"); return null;}
	}
	private String reportTSVhead(String htitle, int gnNum, int unNum) {
		String head = "### " + htitle + "\n### Filter: " + tdp.theSummary;
		
		if (bModeCoSet)      head += String.format("\n### %,d Unions of Collinear Sets", unNum);
		else if (bModeGroup) head += String.format("\n### %,d Rows with Groups", gnNum);
		else                 head += String.format("\n### %,d Gene Rows", gnNum);
		
		if (bRowAll)        head += "; All species per row\n";
		else if (bCoSetAll) head += "; All species per union\n";
		else                head += "; All rows";
		
		return head + "\n";
	}
	/***********************************************************
	 * XXX build HTML for popup and file; uses geneVec and spAnnoMap
	 */
	private String reportHTML() {
	try {
		buildCompute();
	
	// Initial	
		String htitle = (title + " for " + refSpName);
		
		if (geneVec.size()==0)  {
			if (outType!=pop_html) {
				String msg =  "No rows with both genes for reference " + refSpName;
				JOptionPane.showMessageDialog(null, msg, "No results", JOptionPane.WARNING_MESSAGE);
				return null;
			}
			return reportHTMLhead(htitle, 0, 0) + "<p><big>No gene pairs from selected species</big>"
					+ "</table></center></body></html>";
		}
		
	// column headings
		String hColStr="";
		StringBuffer hBodyStr= new StringBuffer();
		int colSpan=1;
		
		hColStr += "<tr><td><b><center>#</center></b>"; // for row/union number
		for (int x=0; x<nSpecies; x++) {
			colSpan++;
			String ref = (x==posRef) ? isRef : "";
			hColStr += "<td><b><center>" + spPanel.getSpName(x) + ref + "</center></b>";
		}
		for (int x=0; x<nSpecies; x++) {
			if (!spKey[x].isEmpty()) {
				hColStr += "<td><b><center>" + spPanel.getSpName(x)+ " " + spKey[x].trim() + "</center></b>";
				colSpan++;
			}
		}
		hColStr += "\n";
		
	// Table
		final int padWithSpace=10;
		int gnIdx=0, unIdx=0;	// Index into geneVec; index into unionVec
		int gnNum=0, unNum=0;   // Print row/union number
		int cntOut=0, nAllNonRefSp=nSpecies-1;
		Union lastUn=null, unObj=null;
		
		while (true) { // loop through geneVec; while loop because the counts are different for Coset and Gene
			if (bModeCoSet && unIdx==unionVec.size() && gnIdx==unObj.geneVec.size()) break;
			if (gnIdx==geneVec.size()) break;
			
			/* Determine whether to skip row */
			Gene gnObj;
			if (bModeGene) {	// gene report
				gnObj = geneVec.get(gnIdx++);
				if (bRowAll && gnObj.cntSpWithTag()!=nSpecies) continue; 
			}
			else if (bModeGroup) { // Group report
				gnObj = geneVec.get(gnIdx++);
				if (!gnObj.passGrpSz()) continue;
				if (bRowAll && gnObj.cntSpWithTag()!=nSpecies) continue; 
			} 
			else {				// coSet report
				if (unObj==null || gnIdx==unObj.geneVec.size()) { 
					if (unObj!=null) unObj.clear();
					unObj = unionVec.get(unIdx++);
					gnNum=0; gnIdx=0; 
				}
				
				gnObj = unObj.geneVec.get(gnIdx++); 
				if (bRowAll   && gnObj.cntSpWithTag()!=nSpecies) continue; 
				if (bCoSetAll && gnObj.unionObj.unSpCnt!=nAllNonRefSp) continue;
				
				if (unObj!=lastUn) {// divider between unions
					unNum++;
					String setsz = String.format("%d [%d]", unNum, unObj.coSetMap.size());	
					if (!bRowAll && !bCoSetAll && unObj.unSpCnt==nAllNonRefSp) setsz += isAll;
					hBodyStr.append("<tr><td colspan=" + colSpan + ">" + setsz);
					lastUn = unObj;
				}
			}
			gnNum++;
			if (++cntOut%100 == 0) Globals.rprt("Processed " + cntOut + " genes");
			
			/* Output row */
			hBodyStr.append("<tr><td>" + gnNum);
			
			for (int posSp=0; posSp<nSpecies; posSp++) { // Columns
				if (posSp==posRef) {							// Reference: only one gene for ref
					hBodyStr.append("\n  <td>" + gnObj.oTagList[posSp]); 
					if (bModeGroup) hBodyStr.append("   <br>Grp# " + gnObj.strGrpN);
				}
				else if (gnObj.oTagList[posSp]=="") {
					hBodyStr.append("\n  <td>&nbsp;</td>");      // no gene for this species
				}
				else {										   // one or more genes 
					hBodyStr.append("\n  <td style='white-space: nowrap;'>");
					String [] gnTok = gnObj.oTagList[posSp].split(splitC); // list of species genes; chr.genenum
					
					if (bModeCoSet) { 
						String [] coSetTok = gnObj.oCoNList[posSp].split(splitC); // list of collinear Sz.N
						for (int j=0; j<gnTok.length; j++) {
							if (j>0) hBodyStr.append("<br>");
							hBodyStr.append(gnTok[j] + "&nbsp;");
							if (bShowCoSet) {
								for (int k=gnTok[j].length(); k<padWithSpace; k++)  hBodyStr.append("&nbsp;");
								hBodyStr.append(isCoSet + coSetTok[j]);
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
					if (gnObj.oTagList[nSp].isEmpty()) {
						hBodyStr.append("\n  <td>&nbsp;");
						continue;
					}
					
					HashMap <String, String> nAnnoMap = spAnnoMap.get(nSp).nAnnoMap;
					String anno="";
					String [] glist = gnObj.oTagList[nSp].split(splitC); // already has keyword values delimited by <br>
					for (String gn : glist) {
						if (!anno.isEmpty()) anno += "<br>";
						anno += nAnnoMap.get(gn).trim() + semiC;
					}
					hBodyStr.append("\n  <td>" + anno);
				}
			}
			hBodyStr.append("\n");
		}
		clearDS();
		Globals.rclear();
		
		// Finish
		String head = reportHTMLhead(htitle, gnNum, unNum);
		String tail="</table>";
		if ((gnNum>100 || unNum>10) && outType==ex_html) tail += "<p><a href='#top'>Go to top</a>\n";
		tail += "</center></body></html>\n";
		
		if (cntOut==0) {
			if (outType!=pop_html) {
				String msg = "No reference genes fit the display options\n" + reportTSVhead(htitle, gnNum, unNum);
				JOptionPane.showMessageDialog(null, msg, "No results", JOptionPane.WARNING_MESSAGE);
				return null;
			}
			return head + "<p><big>No reference genes fit the display options</big>" + tail;
		}
		return head + hColStr + hBodyStr.toString() + tail; // for file or popup
	} 
	catch (Exception e) {ErrorReport.print(e, "Build HTML"); return null;}
	}
	
	
	private String reportHTMLhead(String htitle, int gnNum, int unNum) {
		String head = "<!DOCTYPE html><html>\n<head>\n"
				+ "<title>" + htitle + "</title>\n" + "<style>\n" + "body {font-family: monospaced;  font-size: 10px; }\n"
				+ ".ty    {border: 1px  solid black; border-spacing: 1px; border-collapse: collapse;margin-left: auto; margin-right: auto;}\n"
				+ ".ty td {border: 1px  solid black; padding: 5px; white-space: nowrap; }" 
				+ "</style>\n</head>\n<body>\n" + "<a id='top'></a>\n";
		
		head   += "<center><b>" + htitle + "</b>"
		        + "<br>Filter: " + tdp.theSummary + "\n"; // The number of rows will be appended
		
		if (bModeCoSet)      head += String.format("<br>%,d Unions of Collinear Sets", unNum);
		else if (bModeGroup) head += String.format("<br>%,d Rows with Groups", gnNum);
		else                 head += String.format("<br>%,d Gene Rows", gnNum);
		
		if (bRowAll)        head += "; All species per row\n";
		else if (bCoSetAll) head += "; All species per union\n";
		else                head += "\n";
		
		head += "<p><table class='ty'>\n"; 		  // and this will finish the head 
		return head;
	}
	/***********************************************************
	 * XXX Used by all output to build rows of the report
	 */
	private void buildCompute() {
	try {	
		// tmpRowVec: Get all rows with both genes and Ref is one of them
		int spRefIdx = spOrdMap.get(posRef);
		
		Vector <TmpRowData> tmpRowVec = new Vector <TmpRowData>  ();
		for (int r=0; r<tdp.theTableData.getNumRows(); r++) {
			TmpRowData trd = new TmpRowData(tdp);
			trd.loadRow(r);
			if (!trd.geneTag[0].contains(Q.empty) && !trd.geneTag[1].contains(Q.empty)) {
				if (trd.spIdx[0]==spRefIdx || trd.spIdx[1]==spRefIdx) {
					tmpRowVec.add(trd);	
				}
			}
		}
		if (tmpRowVec.size()==0) return;
		
		// geneVec: create Gene object from reference genes
		HashMap <String, String> nAnnoMap = spAnnoMap.get(posRef).nAnnoMap;
		HashMap <String, Gene> tmpRefMap = new HashMap <String, Gene> ();
		
		for (TmpRowData rd : tmpRowVec) { 
			int i = -1;								// if >2 species and a middle one selected, may be in 1st or 2nd column
			if      (rd.spIdx[0]==spRefIdx)  i=0;
			else if (rd.spIdx[1]==spRefIdx)  i=1;	
			
			String tag = (bModeGroup) ? (rd.geneTag[i] + " " + rd.groupN) : rd.geneTag[i]; // TmpRef key
			
			if (!tmpRefMap.containsKey(tag)) {
				Gene g = new Gene(posRef, rd.geneTag[i],  rd.groupN);
				geneVec.add(g);
				tmpRefMap.put(tag, g);
				
				if (hasKey && !spKey[posRef].isEmpty()) {
					nAnnoMap.put(rd.geneTag[i], ""+rd.nRow); // nRow will be replaced with Anno from nRow
				}
			}
		}
		if (geneVec.size()==0) return;
			
		// geneVec: sort by chr, by geneNum, by suffix
		Collections.sort(geneVec, new Comparator<Gene> () {
			public int compare(Gene a, Gene b) {
				if (bModeGroup && a.rGrpN!=b.rGrpN) return a.rGrpN-b.rGrpN;
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
				if (a.rGnum==b.rGnum) return a.rSuffix.compareTo(b.rSuffix);
				else 				  return a.rGnum-b.rGnum;
			}
		});
		
		// Add other species
		for (int posSp=0; posSp<nSpecies; posSp++) {
			if (posSp==posRef) continue;
			
			nAnnoMap   = spAnnoMap.get(posSp).nAnnoMap;
			int spIdx2 = spOrdMap.get(posSp);
			
			for (TmpRowData rd : tmpRowVec) {	// loop rows for each species
				int i = -1;
				if      (rd.spIdx[0]==spRefIdx)  i=0;
				else if (rd.spIdx[1]==spRefIdx)  i=1;	
				int j = (i==0) ? 1 : 0;
				
				if (rd.spIdx[j] != spIdx2) continue;
	
				String tag = (bModeGroup) ? (rd.geneTag[i] + " " +  rd.groupN ) : rd.geneTag[i]; 
				Gene gn = tmpRefMap.get(tag);
				
				int spOrd = spIdxMap.get(rd.spIdx[j]);
				gn.addNonRef(spOrd, rd.geneTag[j], rd.chrNum[j], rd.collinearSz, rd.collinearN);
				
				if (hasKey && !spKey[posSp].isEmpty() && !nAnnoMap.containsKey(rd.geneTag[j])) {
					nAnnoMap.put(rd.geneTag[j],""+rd.nRow);
				}
			}
		} 
		tmpRefMap.clear();
		
		for (Gene gn : geneVec) gn.sortTags();
		
		if (bModeCoSet) buildXunion(spRefIdx, tmpRowVec); // create Unions
		tmpRowVec.clear();
		
		if (bModeGroup) buildXmerge();		// merge other species
			
		if (hasKey)  buildXannoMap();		// add annotation from keywords
	} 
	catch (Exception e) {ErrorReport.print(e, "Build Gene Vec"); }
	}
	
	/********************************************
	 * Create Unions of CoSets. geneVec is sorted by tag.
	 */
	private void buildXunion(int spRefIdx, Vector <TmpRowData> tmpRowVec)  {	
	try {
		HashMap <String, Gene> refMap = new HashMap <String, Gene> ();
		for (Gene gn : geneVec) {
			gn.coSetVec = new Vector <String> ();
			refMap.put(gn.rTag, gn);
		}
		// Add CoSets to each gene
		for (TmpRowData rd : tmpRowVec) {
			int i = -1;
			if      (rd.spIdx[0]==spRefIdx)  i=0;
			else if (rd.spIdx[1]==spRefIdx)  i=1;	
			String tag = (i!=-1) ? rd.geneTag[i] : null;
			if (tag==null) continue;					
			
			int j = (i==0) ? 1 : 0;
			String key = rd.chrIdx[i] + "." + rd.chrIdx[j] + "." + rd.collinearN; // Collinear set key
			if (bCoSetAll) key = rd.spIdx[j] + "." + key; 						  // need spIdx to count species 
			
			Gene gn = refMap.get(tag);
			gn.coSetVec.add(key);
		}
		refMap.clear();
		
		// create Unions
		HashMap <String, Union> unionMap = new HashMap <String, Union> ();
		
		for (Gene gn : geneVec) {
			Union unObj=null;		// find if one exists for one of its coSets
			
			for (String key : gn.coSetVec) {
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
			
			for (String key : gn.coSetVec) {
				unObj.updateOne(key, gn);				// counts number of species number in union
				if (!unionMap.containsKey(key)) unionMap.put(key, unObj);
			}
			gn.coSetVec.clear();
		}
		
		if (!bCoSetAll) {
			unionMap.clear();
			return; 							// 'Row all' does gn.cntCol in reportHTML
		}
		
		// Count species for each union
		HashSet <String> spSet = new HashSet <String> ();
		for (Union unObj : unionMap.values()) {	
			for (String key : unObj.coSetMap.keySet()) {
				String sp = key.split("\\.")[0];
				if (!spSet.contains(sp)) spSet.add(sp);
			}
			unObj.unSpCnt = spSet.size();
			spSet.clear();
		}
		unionMap.clear();
	}
	catch (Exception e) {ErrorReport.print(e, "Build Gene Vec for Collinear"); }
	}
	/*********************************************************
	 * For groups, merge species
	 * geneVec does not have merged lists because 
	 * tag = rd.geneTag[i] + " " + rd.groupN in order to not merge from same species
	 */
	private void buildXmerge() {
	try {
		HashMap <String, Integer> tagRowMap = new HashMap <String, Integer>  ();
		Vector <Gene> newGeneVec = new Vector <Gene> ();
		
		int gidx=0;
		for (Gene gnObj : geneVec) {
			if (tagRowMap.containsKey(gnObj.rTag)) {
				int i = tagRowMap.get(gnObj.rTag);
				Gene gn = newGeneVec.get(i);
				gn.merge(gnObj);
			}
			else {
				tagRowMap.put(gnObj.rTag, gidx++);
				newGeneVec.add(gnObj);
				gnObj.strGrpN = gnObj.rGrpN + " ";
			}	
		}
		tagRowMap.clear();
		geneVec.clear();
		geneVec = newGeneVec;
	}
	catch (Exception e) {ErrorReport.print(e, "Build Gene Vec for Group"); }
	}
	/******************************************************
	 * Get All_Anno key/values; uses spAnnoMap; puts HTML <br> between keyword values
	 */
	private boolean buildXannoMap() {
	try {
		TmpRowData rd = new TmpRowData(tdp);
		boolean bTSV = (outType==ex_tsv);
		String xBreak   = (bTSV) ? "| " : "<br>"; // between keywords for single gene
		String xBreakSp = "<br>&nbsp;";          // indent when wraparound
		
		String notFound="";
		for (int nSp=0; nSp<nSpecies; nSp++) {
			if (spKey[nSp].isEmpty()) continue;
			
			HashMap <String, String> nAnnoMap = spAnnoMap.get(nSp).nAnnoMap;
			if (nAnnoMap.size()==0) continue;
			
			HashMap <String, Boolean> keyMap = new HashMap <String, Boolean> ();
			String [] set = spKey[nSp].split(keyC);
			for (int i=0; i<set.length; i++) 
				keyMap.put(set[i].trim().toLowerCase(), false);
			
			String spAbbr = spPanel.getSpAbbr(nSp);
					
			for (String gnum : nAnnoMap.keySet()) { // process each gene from this species
				int row = Integer.parseInt(nAnnoMap.get(gnum));
				String allanno = rd.getAnnoFromKey(spAbbr, row);
				String allVal = "";
				String [] annoTok = allanno.trim().split(semiC); // GFF input e.g. ID=one; Desc=foo bar; 
				for (String kv : annoTok) {
					String [] vals = kv.split("=");
					if (vals.length==2 && keyMap.containsKey(vals[0].toLowerCase())) {
						String v = vals[1].trim();
						
						String wval;
						if (v.isEmpty() || v.equals(Q.empty)) 	wval = "-";
						else if (bTSV) 							wval = v;
						else 									wval = Jhtml.wrapLine(v, descWidth, xBreakSp);
						
						if (allVal!="") allVal += xBreak; 
						allVal += wval;
						
						keyMap.put(vals[0].toLowerCase(), true); 
					}
				}
				if (allVal=="") allVal="---"; 
				nAnnoMap.put(gnum, allVal);
			}
			for (String key : keyMap.keySet()) {
				if (!keyMap.get(key)) notFound +=  "\n" + spAbbr + ": " + key;
			}
		}
		if (!notFound.equals("")) {
			Utilities.showWarningMessage("Keywords not found: " + notFound);
			return false;
		}
		return true;
	} catch (Exception e) {ErrorReport.print(e, "Build Anno Map"); return false;}
	}
	///////////////////////////////////////////////////////////////
	private void clearDS() { // spKey is the only DS reused 
		geneVec.clear();
		for (Anno aObj : spAnnoMap.values()) aObj.nAnnoMap.clear();
		spIdxMap.clear();
		spOrdMap.clear();
	}
	/******************************************************************
	 * Classes
	 */
	private class Gene {
		private Gene(int spOrd, String tag, int grpN) {
			oTagList = new String [nSpecies];
			for (int i=0; i<nSpecies; i++) oTagList[i] = "";
			
			if (bModeCoSet) { // Ref coSet info not necessary because saved with gene pair 
				oCoNList = new String [nSpecies];
				for (int i=0; i<nSpecies; i++) oCoNList[i] = "";
			};
			
			if (tag.endsWith("\\.")) tag = tag.substring(0, tag.length()-1);
			rTag = oTagList[spOrd] = tag;			// chr.gnum.suffix
			
			String [] tok = tag.split("\\.");	// split into chr, gnum, suffix
			rChr = tok[0];
			rGnum = Integer.parseInt(tok[1]);
			rSuffix =  (tok.length==3) ? tok[2] : "";
			
			this.rGrpN = grpN;
		}
		private void addNonRef(int spOrd, String tag, String chr, int coSz, int coN) {
			if (tag.endsWith("\\.")) tag = tag.substring(0, tag.length()-1);
			oTagList[spOrd] += tag + splitC;
			
			if (bModeCoSet) oCoNList[spOrd] += coSz + "." + coN + splitC;
		}
		private int cntSpWithTag() {
			int cnt=0;
			for (String o : oTagList) if (!o.equals("")) cnt++;
			return cnt;
		}
		private boolean passGrpSz() {
			for (int i=0; i<nSpecies; i++) {
				if (i==posRef) continue;
				
				String [] tok = oTagList[i].split(splitC);
				if (tok.length>=grpSz) return true;
			}
			return false;
		}
		private void merge(Gene gn) {
			for (int i=0; i<nSpecies; i++) {
				if (i==posRef) continue;
				
				if (oTagList[i].equals("") && !gn.oTagList[i].equals("")) {
					if (gn.oTagList[i].split(splitC).length>grpSz) {
						oTagList[i] = gn.oTagList[i];
						strGrpN += gn.rGrpN + " ";
						return;
					}
				}
			}
		}
		private void sortTags() { // annotations are found by geneTag, so do not need switching
			for (int sp=0; sp<nSpecies; sp++) {
				if (sp==posRef || oTagList[sp].equals("")) continue;
						
				String [] tags = oTagList[sp].split(splitC);
				if (tags.length==1) continue;
		
				String [] cosets = (bModeCoSet) ? oCoNList[sp].split(splitC) : null;
				boolean swap=false;
				for (int i=0; i<tags.length-1; i++) { // good ole bubble sort on chr.genenum.suffix
					String [] tokI = tags[i].split("\\.");
					boolean bSort=false;
					
					for (int j=i+1; j<tags.length; j++) {
						String [] tokJ = tags[j].split("\\.");
						
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
							String x = tags[i];   tags[i] = tags[j];     tags[j] = x;
							if (bModeCoSet) {
								x = cosets[i]; cosets[i] = cosets[j]; cosets[j] = x;
							}
							tokI = tags[i].split("\\.");	// tags[i] was just changed
						}
					}
				}
				if (swap) {
					oTagList[sp] = "";
					for (int i=0; i<tags.length; i++) oTagList[sp] += tags[i] + splitC;
		
					if (bModeCoSet) {
						oCoNList[sp] = "";
						for (int i=0; i<tags.length; i++) oCoNList[sp] += cosets[i] + splitC;
					}
				}
			}
		}
		public String toString() {
			String tags="";
			for (int i=0; i<nSpecies; i++) {
				if (i==posRef) continue;
				tags += oTagList[i] + " :: ";
			}
			return String.format("%10s Grp#%d  %s", rTag, rGrpN, tags);
		}
		private String rChr, rSuffix, rTag; // ref only; 
		private int rGnum, rGrpN=0;
		private String strGrpN=null;
		
		// List (splitC delimited) of all genes ref aligns to on speciesN; two arrays are 1-1 for collinear
		// pair genes:		  e.g. ref tag      tag          2 tags/cosets 
		private String [] oTagList; // [0]1.62  [1] 1.63.b   [2] 5.78; 8.5522	// Chr.genenum.suffix
		private String [] oCoNList; // [0]      [1] 7.1      [2] 4.1;  3.182	// collinear sz.coset#
		private Union unionObj=null;  // all coSet pair genes with ref; can be different CoSets
		private Vector <String> coSetVec = null; 	// used for buildXunion only, cleared at end
	}

	// pointed to by all genes in Union and contains all coSets in union
	private class Union { 
		private Vector <Gene> geneVec = new Vector <Gene> ();
		private HashMap <String, Integer> coSetMap = new HashMap <String, Integer> ();
		private int unSpCnt=0;
		
		private void updateOne(String key, Gene gn) {
			if (!geneVec.contains(gn)) geneVec.add(gn);
			if (!coSetMap.containsKey(key)) coSetMap.put(key, 1);
			else coSetMap.put(key, coSetMap.get(key)+1);
		}
		public String toString() {
			String msg="";
			for (String cs : coSetMap.keySet()) msg += String.format(" %15s  %d  ", cs, coSetMap.get(cs));
			return msg;
		}
		private void clear() {
			geneVec.clear();
			coSetMap.clear();
		}
	}
	// one per species where nAnnoMap contains gene values of keyword
	private class Anno { 
		private HashMap <String, String> nAnnoMap  = new HashMap <String, String> (); // gene#, value
	}
	/*********************************************************************/
	// Interface 
	private TableDataPanel tdp;
	private SpeciesSelectPanel spPanel;
	private JPanel mainPanel;
	
	private JRadioButton [] radSpecies=null;
	private JTextField [] txtSpKey=null;
	
	private JRadioButton radRowAll, radCoSetAll, radIgnAll;
	private JCheckBox chkShowCoSet=null; // Collinear
	
	private JButton btnOK=null, btnCancel=null, btnInfo=null;	
}
