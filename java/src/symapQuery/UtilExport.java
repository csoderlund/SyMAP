package symapQuery;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;

import symap.Globals;
import symap.manager.Mproject;
import util.ErrorReport;
import util.Jcomp;
import util.Jhtml;
import util.Popup;

/***************************************************
 * Export file methods for Table, called by TableDataPanel.exportPopup
 * Recently put export FASTA of pairs and unique spliced transcripts; the 2nd is now in View, and the 1st seems useless; CAS591
 */
public class UtilExport extends JDialog {
	private static final long serialVersionUID = 1L;
	private static String filePrefix = "queries"; // CAS589 was tableExport
	protected final int ex_csv = 0, ex_html = 1, ex_fa_pairs= 2,  ex_fa_genes = 3, ex_cancel= 4; // Referenced in TableDataPanel
   
	private TableMainPanel tPanel;
	private String title, projs=""; 
	private Vector<Mproject> projVec;
	
	private JRadioButton btnYes; 
	private int nMode = -1;  
	    
	protected UtilExport(TableMainPanel tdp, String title) {
		this.tPanel = tdp;
		this.title = title;
		
		projVec = tdp.qFrame.getProjects();
		for (Mproject mp : projVec) projs += mp.getDisplayName() + " (" + mp.getdbAbbrev() + "); ";
		projs = projs.substring(0, projs.length()-2);
		
		setModal(true);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setTitle("Export Table Rows");
		
		createDialog();
	
		pack();
		this.setResizable(false);
		setLocationRelativeTo(null); 
    }
	private void createDialog() {
		// Files
		JRadioButton btnCSV = Jcomp.createRadio("CSV", "Export all visible columns and rows in CVS format"); 
		btnCSV.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				nMode = ex_csv;
			}
		});
		JRadioButton btnHTML = Jcomp.createRadio("HTML", "Export all visible columns and rows in HTML format"); 
		btnHTML.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				nMode = ex_html;
			}
		});
		
		JPanel rowPanel = Jcomp.createRowPanel();
        rowPanel.add(Box.createHorizontalStrut(15));
        rowPanel.add(new JLabel("Include Row column:")); rowPanel.add(Box.createHorizontalStrut(5));
        
        btnYes = Jcomp.createRadio("Yes", "Include row column"); 
        JRadioButton btnNo = Jcomp.createRadio("No", "Do not include row column"); 
        ButtonGroup inc = new ButtonGroup();
		inc.add(btnYes);
		inc.add(btnNo);
		btnNo.setSelected(true); 
		rowPanel.add(btnYes); rowPanel.add(Box.createHorizontalStrut(5));
		rowPanel.add(btnNo);
		
		ButtonGroup grp = new ButtonGroup();
		grp.add(btnCSV);  grp.add(btnHTML);
    	btnCSV.setSelected(true);
    	nMode = ex_csv;
			
		JPanel selectPanel = Jcomp.createPagePanel();
		selectPanel.add(new JLabel("Table rows and columns")); selectPanel.add(Box.createVerticalStrut(5));
		selectPanel.add(btnCSV); 			selectPanel.add(Box.createVerticalStrut(5));
		selectPanel.add(btnHTML);			selectPanel.add(Box.createVerticalStrut(5));
        selectPanel.add(rowPanel);
        
    // buttons
    	JButton btnOK = Jcomp.createButton("Export", "Perform export");
    	btnOK.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
		JButton btnCancel = Jcomp.createButton(Jcomp.cancel, "Cancel export");
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				nMode = ex_cancel;
				setVisible(false);
			}
		});
		boolean isl = Jhtml.isLinux();
		int sp = (isl) ? 50 : 20;
		
		JPanel buttonPanel = Jcomp.createRowPanel(); 
		buttonPanel.add(Box.createHorizontalStrut(sp));
		buttonPanel.add(btnOK);			buttonPanel.add(Box.createHorizontalStrut(sp));
		buttonPanel.add(btnCancel);		

      // Finish 
		JPanel mainPanel = Jcomp.createPagePanel();
		mainPanel.add(selectPanel); 	mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
		mainPanel.add(buttonPanel, BorderLayout.CENTER);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		add(mainPanel);	
	}
    protected int getSelection() { return nMode; }
    
    /*********************************************************
     * Write CSV - 
     */
    protected void writeColsCSV() {
    try {
    	boolean reset = false;
    	int nSelRows = tPanel.theTable.getSelectedRowCount();
    	if (nSelRows>0) {
    		if (!Popup.showConfirm2("Selected rows","Export " + nSelRows + " selected rows?" )) return ;
    	}
		BufferedWriter outFH = getFileHandle(ex_csv, filePrefix + ".csv", true);
		if (outFH==null) return;
		
		if(nSelRows == 0) {
			tPanel.theTable.selectAll();
			reset = true;
		}
		int [] selRows = tPanel.theTable.getSelectedRows();
		
		outFH.write("### " + title + "\n");
		outFH.write("### " + projs + "\n");
		outFH.write("### Filter: " + tPanel.theSummary + "\n"); 
		boolean incRow = btnYes.isSelected();
		tPanel.setPanelEnabled(false);
		
		// Columns names
		for(int x=0; x<tPanel.theTable.getColumnCount()-1; x++) {
			if (!incRow && x==0) continue; // for row check
			
			outFH.write(reloadCleanString(tPanel.theTable.getColumnName(x)) + "," );
		}
		outFH.write(reloadCleanString(tPanel.theTable.getColumnName(tPanel.theTable.getColumnCount()-1)));
		outFH.newLine();
		
		// rows
		for(int x=0; x<selRows.length; x++) {
			for(int y=0; y<tPanel.theTable.getColumnCount()-1; y++) {
				if (!incRow && y==0) continue; 
				
				outFH.write(reloadCleanString(tPanel.theTable.getValueAt(selRows[x], y)) + ",");
			}
			outFH.write(reloadCleanString(tPanel.theTable.getValueAt(selRows[x], tPanel.theTable.getColumnCount()-1)));
			outFH.newLine();
			outFH.flush();
			if (x>0 && x%1000 == 0) Globals.rprt("Wrote " + x + " of " + selRows.length);
		}
		outFH.close();
		Globals.prt("Wrote " + selRows.length + " rows                        ");
		
		if(reset)
			tPanel.theTable.getSelectionModel().clearSelection();
		tPanel.setPanelEnabled(true);
	} catch(Exception e) {ErrorReport.print(e, "Write delim file");}
    }
     /* HTML */
    protected void writeColsHTML() { 
    try {
    	int nSelRows = tPanel.theTable.getSelectedRowCount();
    	if (nSelRows>0) { 
    		if (!Popup.showConfirm2("Selected rows","Export " + nSelRows + " selected rows?" )) return ;
    	}
    	
    	BufferedWriter outFH = getFileHandle(ex_html, filePrefix + ".html", false);
		if (outFH==null) return;
	
		boolean incRow = btnYes.isSelected();
		
		tPanel.setPanelEnabled(false);
		boolean reset = false;
		if(nSelRows == 0) {
			tPanel.theTable.selectAll();
			reset = true;
		}
		int [] selRows = tPanel.theTable.getSelectedRows();
		
		// html header; CAS586 make it HTML5 compliant
		outFH.write("<!DOCTYPE html><html lang=\"en\">\n<head>\n"
				+ "<title>SyMAP Query Table Results</title>\n"
				+ "<style>\n"
				+  "body {font-family: Verdana, Arial, Helvetica, sans-serif;  font-size: 14px; }\n"
				+ ".ty    {border: 1px  solid black; border-spacing: 1px; border-collapse: collapse;margin-left: auto; margin-right: auto;}\n"
				+ ".ty td {border: 1px  solid black; padding: 5px; width: 80px; word-wrap: break-word; }" 
				+ "</style>\n</head>\n<body>\n"
				+ "<a id='top'></a>\n");
	 
		outFH.write("<p style=\"text-align: center; font-size: 16px;\"><b>" + title + "</b></p>\n"); 
		outFH.write("<p style=\"text-align: center;\">" + projs + "</p>\n"); 
		outFH.write("<p style=\"text-align: center;\">Filter: " + tPanel.theSummary + "</p>\n");
		outFH.write("<p><table class='ty'>\n<tr>");
		
		// Columns names
		for(int x=0; x<tPanel.theTable.getColumnCount(); x++) {
			if (!incRow && x==0) continue; 
			String col = reloadCleanString(tPanel.theTable.getColumnName(x));
			col = col.replace(" ","<br>");
			outFH.write("<th>" + col + "</th>");
		}
		
		// rows
		for(int x=0; x<selRows.length; x++) {
			outFH.write("\n<tr>");
			for(int y=0; y<tPanel.theTable.getColumnCount(); y++) {
				if (!incRow && y==0) continue; 
				
				outFH.write("<td>" + reloadCleanString(tPanel.theTable.getValueAt(selRows[x], y)));
			}
			outFH.flush();
			if (x>0 && x%1000 == 0) Globals.rprt("Wrote " + x + " of " + selRows.length);
		}
		outFH.write("\n</table>\n");
		if (selRows.length>100) outFH.write("<p><a href='#top'>Go to top</a>\n");
		outFH.write("</body></html>\n");
		outFH.close();
		
		Globals.prt("Wrote " + selRows.length + " rows                         ");
		if(reset)
			tPanel.theTable.getSelectionModel().clearSelection();
		tPanel.setPanelEnabled(true);
	} catch(Exception e) {ErrorReport.print(e, "Write delim file");}
    }
     
    ////////////////////////////////////////////////////////////////////////
    // All Exports use this
    // CAS589 change from PrintWriter for speed, which made no difference 
    private BufferedWriter getFileHandle(int type, String fname, boolean bAppend) {
    	String saveDir = Globals.getExport(); 
		
		JFileChooser chooser = new JFileChooser(saveDir);
		chooser.setSelectedFile(new File(fname));
		if(chooser.showSaveDialog(tPanel.qFrame) != JFileChooser.APPROVE_OPTION) return null;
		if(chooser.getSelectedFile() == null) return null;
		
		int bufsz = 1024;
		String saveFileName = chooser.getSelectedFile().getAbsolutePath();
		if (type==ex_csv) {
			if(!saveFileName.endsWith(".csv")) saveFileName += ".csv";
			symap.Globals.prt("Exporting CSV to " + saveFileName);
		}
		else if (type==ex_html) {
			if(!saveFileName.endsWith(".html")) saveFileName += ".html";
			symap.Globals.prt("Exporting HTML to " + saveFileName);
		}
		else {
    		if(!saveFileName.endsWith(".fasta") && !saveFileName.endsWith(".fa")) saveFileName += ".fa";
    		symap.Globals.prt("Exporting Fasta to " + saveFileName);
    		bufsz = 128 * 1024;
		}
		boolean append=true;
		if (new File(saveFileName).exists()) {
			if (bAppend) {
				int rc = Popup.showConfirmFile(saveFileName);
				if (rc==0) return null;
				if (rc==1) append=false;
			}
			else {
				if (!Popup.showConfirm2("File exists","File '" + saveFileName + "' exists.\nOverwrite?")) return null;
				append=false;
			}
		}
		BufferedWriter out=null;
		try {
			out = new BufferedWriter(new FileWriter(saveFileName, append), bufsz);
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot open file - " + saveFileName);}
		return out;
    }
    private String reloadCleanString(Object obj) {
    	if(obj != null) {
    		String val = obj.toString().trim();
    		if (val.equals("")) return "''";
    		
    		val = val.replaceAll("[\'\"]", "");
    		val = val.replaceAll("\\n", " ");
    		val = val.replaceAll(",", ";"); 
    		return val;
    	}
    	else return "''";
	}
}
