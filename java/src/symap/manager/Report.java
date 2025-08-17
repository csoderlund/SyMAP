package symap.manager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.util.ArrayList;

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

import database.DBconn2;
import symap.Globals;
import util.ErrorReport;
import util.Jcomp;
import util.Utilities;

/**************************************************************
 * Block report from Manager (moved from ChrExpFrame CAS571)
 */

public class Report extends JDialog  {
	private static final long serialVersionUID = 1L;
	private final int GENE=1, ONLY=2, SUM=3;
	private final int IGN=1, NUM=2, FLOAT=3, LFLOAT=4;
	private final String CHR="Chr", CCB = "C1.C2.B";
	
	private boolean isTSV=false, isHTML, isPop=false, isSp1=true, isSp2, useComb=true;
	private int cntHtmlRow=0; // to determine whether to have a Goto top at bottom
	
	private DBconn2 tdbc2 = null;
	private Mpair mp;
	private Mproject [] mProjs;
	private String name1, name2;
	
	protected Report(DBconn2 tdbc2, Mproject [] mProjs, Mpair mp) {
		this.tdbc2 = tdbc2;
		this.mProjs = mProjs;
		this.mp = mp;
		name1 = mProjs[0].getDisplayName();
		name2 = mProjs[1].getDisplayName();
		
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setTitle("Block Report");
		
		mainPanel = Jcomp.createPagePanel();
		createPanel();
		createOutput();
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		add(mainPanel);
		
		pack();
		setModal(true); // nothing else can happen until ok,cancel
		toFront();
		setResizable(false);
		setLocationRelativeTo(null); 
		setVisible(true);
	}
	/*****************************************************************/
	private void createPanel() {
		
		JPanel radPanel = Jcomp.createPagePanel();
		radBlocksPlus  = Jcomp.createRadio("Blocks plus", "Download all blocks with extra block info"); 
		radBlocksGenes = Jcomp.createRadio("Blocks with gene info", "Download all blocks with gene info");
		radBlocksSum   = Jcomp.createRadio("Blocks summary", "Download block summary by chromosome pair");
		
		ButtonGroup ba = new ButtonGroup();
		ba.add(radBlocksGenes);ba.add(radBlocksPlus);ba.add(radBlocksSum);
		radBlocksPlus.setSelected(true);
			
		radPanel.add(radBlocksPlus); 	radPanel.add(Box.createVerticalStrut(5));
		radPanel.add(radBlocksGenes); 	radPanel.add(Box.createVerticalStrut(5));
		radPanel.add(radBlocksSum); 	radPanel.add(Box.createVerticalStrut(5));
		
		/// Species
		JPanel spRow = Jcomp.createRowPanel();
		spRow.add(Jcomp.createLabel("Reference   "));
		radSpecies = new JRadioButton[mProjs.length];
		ButtonGroup bg = new ButtonGroup();
		
		for (int i=0; i<mProjs.length; i++) {
			radSpecies[i] = Jcomp.createRadio(mProjs[i].getDisplayName(), "Make this the reference");
			bg.add(radSpecies[i]);
			spRow.add(radSpecies[i]); spRow.add(Box.createHorizontalStrut(5));
		}
		radSpecies[0].setSelected(true);
				
		
		mainPanel.add(spRow);   mainPanel.add(Box.createVerticalStrut(3));
		mainPanel.add(radPanel);mainPanel.add(Box.createVerticalStrut(3));
	}
	private void createOutput() {
		JPanel row = Jcomp.createRowPanel();
		JLabel lab = Jcomp.createLabel("Output");
		row.add(lab); row.add(Box.createHorizontalStrut(5));
		
		btnPop =  Jcomp.createRadio("Popup", "Popup of block report");
		row.add(btnPop); row.add(Box.createHorizontalStrut(5));
		
		btnHTML =  Jcomp.createRadio("HTML File", "HTML file of blocks");
		row.add(btnHTML); row.add(Box.createHorizontalStrut(5));
		
		btnTSV =  Jcomp.createRadio("TSV File", "File of tab separated values");
		row.add(btnTSV); 
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(btnHTML); grp.add(btnTSV); grp.add(btnPop);
    	btnPop.setSelected(true);

    	/// Combine
		JPanel useRow = Jcomp.createRowPanel();
		useRow.add(Box.createHorizontalStrut(lab.getPreferredSize().width + 5));
		chkComb = Jcomp.createCheckBox("Combine " + CCB, "Make Chr1.Chr2.Block one column; if unchecked, make 3 columns.", true);
		useRow.add(chkComb);
    			
		// Buttons
		btnOK = Jcomp.createButton("Create", "Create report and write to file");
    	btnOK.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				runReport();
			}
		});
		btnCancel = Jcomp.createButton(Jcomp.cancel, "Cancel action");
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
		
		JPanel buttonPanel = Jcomp.createRowPanel();
		buttonPanel.add(Box.createHorizontalStrut(25));
		buttonPanel.add(btnOK);			buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnCancel);		buttonPanel.add(Box.createHorizontalStrut(5));
		
		mainPanel.add(new JSeparator());
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(3));
		mainPanel.add(useRow); mainPanel.add(Box.createVerticalStrut(3));
		
		mainPanel.add(new JSeparator());
		mainPanel.add(Box.createVerticalStrut(8));
		mainPanel.add(buttonPanel);
	}
	/*XXX****************************************************************/
	/*****************************************************************/
	private void runReport() {
		setVisible(false);
		
		isTSV  = btnTSV.isSelected();
		isPop  = btnPop.isSelected();
		isHTML = btnHTML.isSelected() || isPop;
		isSp1  = radSpecies[0].isSelected();
		isSp2  = radSpecies[1].isSelected();
		useComb = chkComb.isSelected();
		
		if (radBlocksGenes.isSelected())     mkBlocksGene();
		else if (radBlocksPlus.isSelected()) mkBlocksPlus();
		else if (radBlocksSum.isSelected())  mkBlocksSum();
	}
	/*****************************************************************/
	private void mkBlocksSum() { 
		try { 
			PrintWriter outFH = null;
			String report = "";
			
			if (isPop) report = htmlHead();
			else {
				outFH = startFile("blocksSummary");
				if (outFH==null) return;
			}
			
			ArrayList <String> row = new ArrayList <String> (6);
			row.add("Species1"); row.add("Species2"); 
			if (useComb) {row.add("C1.C2"); }
			else         {row.add(CHR+"1"); row.add(CHR+"2"); }
			row.add("#Block"); row.add("#Hits"); 
			if (outFH==null) report+=joinHead(row,""); else outFH.println(joinHead(row,""));
			
			String savChr1="", savChr2="";
			int cntBlocks=0, sumHits=0;
			
			ResultSet rs = tdbc2.executeQuery(mkSQL(SUM));
			while (rs.next()){
				String n1="", n2="";
				int nhits;
				if (useComb) {
					n1 = rs.getString(1);
					nhits = rs.getInt(3);
				}
				else {
					n1 = rs.getString(1);
					n2 = rs.getString(2);
					nhits = rs.getInt(4);
				}
				if (!n1.equals(savChr1) || !n2.equals(savChr2)) {
					if (cntBlocks>0) { // create row and print
						int i=0;
						row.set(i++, name1); row.set(i++, name2); 
						if (useComb) {row.set(i++, savChr1); }
						else {row.set(i++, savChr1); row.set(i++, savChr2);}
						row.set(i++, Integer.toString(cntBlocks)); row.set(i++, Integer.toString(sumHits)); 
						
						if (outFH==null) report+=joinRow(row); else outFH.println(joinRow(row));
					}
					savChr1 = n1; savChr2 = n2;
					cntBlocks=sumHits=0;
				}
				cntBlocks++;
				sumHits += nhits;
			}
			if (cntBlocks>0) {
				int i=0;
				row.set(i++, name1); row.set(i++, name2); 
				if (useComb) {row.set(i++, savChr1); }
				else {row.set(i++, savChr1); row.set(i++, savChr2); }
				row.set(i++, Integer.toString(cntBlocks)); row.set(i++, Integer.toString(sumHits)); 
				
				if (outFH==null) report+=joinRow(row); else outFH.println(joinRow(row));
			}
			if (isPop) {
				report += htmlTail();
				util.Jhtml.showHtmlPanel(null, ("Block Summary"), report); 
			}
			else endFile(outFH);	
		} 
		catch(Exception e) {ErrorReport.print(e, "Generate blocks summary");}
	}
	/*****************************************************************/
	private void mkBlocksPlus() { 
		try { 
			PrintWriter outFH = null;
			String report = "";
			
			if (isPop) report = htmlHead();
			else {
				outFH = startFile("blocksPlus");
				if (outFH==null) return;
			}
    	
			ArrayList<String> rowHead = new ArrayList<String>();
			rowHead.add("Species1"); rowHead.add("Species2"); 
			if (useComb) {rowHead.add(CCB);} // combined in sql
			else         {rowHead.add(CHR+"1"); rowHead.add(CHR+"2"); rowHead.add("Block"); }
			rowHead.add("#Hits"); rowHead.add("PearsonR");
			rowHead.add("AvgGap1"); rowHead.add("AvgGap2"); rowHead.add("Len1"); rowHead.add("Len2"); 
			rowHead.add("Start1"); rowHead.add("Start2"); 
			if (isTSV) {
				if (outFH==null) report += joinHead(rowHead, ""); else outFH.println(joinHead(rowHead, ""));
			}
			
			int ncol = rowHead.size()-2;
			String saveChr="", chr="";
			ArrayList<String> row = new ArrayList<String>(rowHead.size());
			for (int i=0; i<rowHead.size(); i++) row.add("");
			
			ResultSet rs = tdbc2.executeQuery(mkSQL(ONLY));
			while (rs.next()){	
				if (!isTSV) {// repeat headings when ref-chr changes 
					if (useComb) {
						chr = rs.getString(1);
						String [] tok = chr.split("\\.");
						chr = (isSp1) ? tok[0] : tok[1];
					}
					else chr = (isSp1) ? rs.getString(1) : rs.getString(2);
					if (!chr.equals(saveChr)) {
						if (outFH==null) report += joinHead(rowHead, chr); else outFH.println(joinHead(rowHead, chr));
					}
					saveChr = chr;
				}
				row.set(0, name1); row.set(1, name2); 
				for(int i = 1; i <= ncol; i++){
					int type = typeArr.get(i-1);
					if (type==IGN)    	   row.set(i+1, rs.getString(i));
					else if (type==NUM)    row.set(i+1, String.format("%,d",rs.getInt(i)));
					else if (type==FLOAT)  row.set(i+1, df(i, rs.getDouble(i)));
					else if (type==LFLOAT) row.set(i+1, String.format("%,d",(int) rs.getDouble(i)));
				}
				if (outFH==null) report += joinRow(row); else outFH.println(joinRow(row));
			}
			if (isPop) {
				report += htmlTail();
				util.Jhtml.showHtmlPanel(null, ("Blocks"), report); 
			}
			else endFile(outFH);	
		} 
		catch(Exception e) {ErrorReport.print(e, "Generate blocks only");}
	}
	/*****************************************************************/
    private void mkBlocksGene() {
    	try { 
    		PrintWriter outFH = null;
			String report = "";
			
			if (isPop) report = htmlHead();
			else {
				outFH = startFile("blocksGenes");
				if (outFH==null) return;
			}
			
			ArrayList<String> rowHead = new ArrayList<String>();
			rowHead.add("Species1"); rowHead.add("Species2"); 
			if (useComb) {rowHead.add(CCB); }// combined in sql
			else         {rowHead.add(CHR+"1"); rowHead.add(CHR+"2"); rowHead.add("Block"); }
			
			rowHead.add("#Hits"); rowHead.add("PearsonR");
			rowHead.add("Start1"); rowHead.add("End1"); rowHead.add("Start2"); rowHead.add("End2"); 
			rowHead.add("#Genes1"); rowHead.add("%Genes1"); rowHead.add("#Genes2"); rowHead.add("%Genes2"); 
			if (isTSV) {
				if (outFH==null) report += joinHead(rowHead, ""); else outFH.println(joinHead(rowHead, ""));
			}
			int ncol = rowHead.size()-2;
			String saveChr="", chr;
			ArrayList<String> row = new ArrayList<String>(rowHead.size());
			for (int i=0; i<rowHead.size(); i++) row.add("");
			
			ResultSet rs = tdbc2.executeQuery(mkSQL(GENE));
			while (rs.next()){
				if (isHTML) { // repeat headings when ref-chr changes
					if (useComb) {
						chr = rs.getString(1);
						String [] tok = chr.split("\\.");
						chr = (isSp1) ? tok[0] : tok[1];
					}
					else chr = (isSp1) ? rs.getString(1) : rs.getString(2);
				
					if (!chr.equals(saveChr)) {
						if (outFH==null) report += joinHead(rowHead, chr); else outFH.println(joinHead(rowHead, chr));
					}
					saveChr= chr;
				}
				
				row.set(0, name1); row.set(1, name2); 
				for(int i = 1; i <= ncol; i++){
					int type = typeArr.get(i-1);
					if (type==IGN)         row.set(i+1, rs.getString(i));
					else if (type==NUM)    row.set(i+1, String.format("%,d",rs.getInt(i)));
					else if (type==FLOAT)  row.set(i+1, df(i, rs.getDouble(i)));
				}
				if (outFH==null) report += joinRow(row); else outFH.println(joinRow(row));
			}
			if (isPop) {
				report += htmlTail();
				util.Jhtml.showHtmlPanel(null, ("Blocks with genes info"), report); 
			}
			else endFile(outFH);	
		} 
    	catch(Exception e) {ErrorReport.print(e, "Generate blocks with genes");}
    }
  
    /*******************************************************************/
    
    //////////////////////////////////////////////////
    private String mkSQL(int which) {
    	String sql;
    	if (useComb) {
    		if (which==SUM) sql = "select concat(g1.name,'.', g2.name), b.blocknum, b.score ";
    		else            sql = "select concat(g1.name,'.', g2.name, '.', b.blocknum),b.score ";
    	}
    	else                sql = "select g1.name, g2.name, b.blocknum ,b.score ";
    	
    	if (which==GENE) {
    		sql += ", b.corr, b.start1, b.end1, b.start2, b.end2 "; // if corr is moved, change df
    		sql += ", b.ngene1,b.genef1,b.ngene2,b.genef2";
    	}
    	if (which==ONLY) {
    		sql += ", b.corr";
    		sql += ", ((b.end1-b.start1+1)/b.score), ((b.end2-b.start2+1)/b.score)";
    		sql += ", (b.end1-b.start1+1), (b.end2-b.start2+1)";
    		sql += ", b.start1, b.start2";
    	}
    	sql +=  " from blocks as b" +
    		    " join xgroups as g1 on g1.idx=b.grp1_idx " + 
    		    " join xgroups as g2 on g2.idx=b.grp2_idx " +
    		    " where b.pair_idx=" + mp.getPairIdx();
    	
    	if (radSpecies[0].isSelected()) sql += " order by g1.name asc, g2.name asc, b.blocknum asc";
    	else                            sql += " order by g2.name asc, g1.name asc, b.blocknum asc";
    	
    	if (which==SUM) return sql;
    	
    	// create type vector
    	if (useComb) typeArr.add(IGN);
    	else {typeArr.add(IGN);typeArr.add(IGN);typeArr.add(IGN);} // name, name, blocknum
    	
    	typeArr.add(NUM); typeArr.add(FLOAT);	// score, corr
    
    	if (which==ONLY) {
    		typeArr.add(LFLOAT); typeArr.add(LFLOAT); // AvgGap
    		typeArr.add(NUM); typeArr.add(NUM);	 typeArr.add(NUM); typeArr.add(NUM); 	  // len, start
    	}
    	else {
    		typeArr.add(NUM); typeArr.add(NUM); typeArr.add(NUM); typeArr.add(NUM); // start,end,start,end
    		typeArr.add(NUM); typeArr.add(FLOAT); typeArr.add(NUM); typeArr.add(FLOAT); // #gene, %gene
    	}
    	
    	return sql;
    }
    ///////////////////////////////////////////////////
    private PrintWriter startFile(String filename) {
    try {
    	filename += (isTSV) ? ".tsv" : ".html";
    	String dirname = Globals.getExport(); 
		JFileChooser chooser = new JFileChooser(dirname);
		chooser.setSelectedFile(new File(filename));
		
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return  null;
		if (chooser.getSelectedFile() == null) return null;
			
		exFileName = chooser.getSelectedFile().getAbsolutePath();
		if (isTSV) {
			if(!exFileName.endsWith(".tsv")) exFileName += ".tsv";
		}
		else {
			if(!exFileName.endsWith(".html")) exFileName += ".html";
		}
		File f = new File(exFileName);
		if (f.exists()) {
			if (!Utilities.showConfirm2("File exists","File '" + exFileName + "' exists.\nOverwrite?")) return null;
			f.delete();
		}
		
		f.createNewFile();
		PrintWriter out = new PrintWriter(new FileWriter(exFileName));
		
		if (isHTML) out.println(htmlHead());
		
		return out;
    }
    catch (Exception e) {ErrorReport.print(e, "Getting file handle for report"); return null;}
    }
    private void endFile(PrintWriter out) {
    	if (btnHTML.isSelected()) out.println(htmlTail());
    
    	out.close();

		System.out.println("Wrote to " + exFileName);
    }
    ////////////////////////////////////////////////////
    private String htmlHead() {
    	String n = (radSpecies[0].isSelected()) ? name1 + "-" + name2 : name2 + "-" + name1;
		String title = n + " Blocks";
		String head = "<!DOCTYPE html><html>\n"
				+ "<head>\n"
				+ "<title>" + title + "</title>\n"
				+ "<style>\n";
		if (isPop) head += "body {font-family: monospaced;  font-size: 10px; }\n";
		else       head += "body {font-family: monospaced;  font-size: 12px; }\n";
		
		title = "SyMAP Synteny Blocks for " + n;
		head += ".ty {border: 1px solid black; border-spacing: 1px; border-collapse: collapse; margin-left: auto; margin-right: auto;}\n"
				+ ".ty td {border: 1px solid black; padding: 5px; border-collapse: collapse; white-space: nowrap; text-align: right;}\n"
				+ "</style>\n"
				+ "</head>\n"
				+ "<body>\n"
				+ "<a id='top'></a>\n"
				+ "<center><b>" + title + "</b>"
				+ "<p><table class='ty'>";
		return head;
    }
    private String htmlTail() {
    	String tail="</table>";
		if (cntHtmlRow>100 && !isPop) tail += "<p><a href='#top'>Go to top</a>\n";
		tail += "</center></body></html>\n";		
		return tail;
    }
    //////////////////////////////////////////
    private String joinRow(ArrayList <String> list)  {
    	String delimiter = (isTSV) ? "\t" : "<td>";
		String buffer = (isTSV) ? "" : "<tr>";
		
		for (String x : list) {
			if (!buffer.equals("")) buffer += delimiter;
			buffer += x;
		}
		cntHtmlRow++;
		
        return buffer;	
	} 
    private String joinHead(ArrayList <String> colList, String chr)  { 
    	String delim  = (isTSV) ? "\t" : "<td style=\"text-align: center\">";
		String buffer = (isTSV) ? ""   : "<tr style=\"background-color: lightgrey;\">";
		
		for (String oCol : colList) {
			String col = oCol;
			if (!buffer.equals("")) buffer += delim;
			
			if (col.startsWith(CHR) || col.startsWith("Species")) {
				if (isHTML && col.startsWith(CHR) && !chr.equals("")) {
					if (isSp1 && col.endsWith("1")) {
						col = chr;
						if (Utilities.getInt(chr) != -1) col = CHR + col;
					}
					else if (isSp2 && col.endsWith("2")) {
						col = chr;
						if (Utilities.getInt(chr) != -1) col = CHR + col;
					}
					else col = CHR + "X";
				}
				if ((isSp1 && oCol.endsWith("1")) || (isSp2 && oCol.endsWith("2"))) {
					if (isTSV) col = col.toUpperCase();
					else       col = "<b>" + col + "</b>";
				}
			}
			else if (col.equals(CCB) && isHTML) {
				if (chr.equals("") ) {
					if (isSp1) col = "<b>C1</b>.C2.B";
					else       col = "C1.<b>C2</b>.B";
				}
				else {
					if (isSp1) col = "<b>" + chr + "</b>.Cx.B";
					else       col = "Cx.<b>" + chr + "</b>.B";
				}
			}
			if (isPop) buffer += "<i>" + col + "</i>";  // popup does not show lightgrey
			else       buffer += col;
		}
		cntHtmlRow++;
        return buffer;	
	} 
    
    private String df(int i, double d) { // Excel removes trailing zeros and leading +
    	String x = String.format("%.3f", d);
    	if (i==5 && !x.startsWith("-")) x = "+" + x;
    	return x;
    }
    /********************************************************************/
    private String exFileName="";
    private ArrayList <Integer> typeArr = new ArrayList <Integer> ();
    private JPanel mainPanel;
    private JCheckBox chkComb = null;
    private JRadioButton [] radSpecies=null;
    private JRadioButton radBlocksGenes, radBlocksPlus, radBlocksSum;
    
    private JRadioButton btnHTML, btnTSV, btnPop;
    private JButton btnOK=null, btnCancel=null;
}
