package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

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

import database.DBconn2;
import symap.Globals;
import symap.closeup.AlignPool;
import util.ErrorReport;
import util.Jcomp;
import util.Utilities;

/***************************************************
 * Export file methods for Table, called by TableDataPanel.exportPopup
 * CAS556 was in TableDataPanel file. CAS563 was TableExport
 */
public class UtilExport extends JDialog {
	private static final long serialVersionUID = 1L;
	private static String filePrefix = "tableExport";
	protected final int ex_csv = 0, ex_html = 1, ex_fasta= 2, ex_cancel= 3; // Referenced in TableDataPanel
   
	private TableMainPanel tdp;
	private String title; 			// CAS560 add for 1st line of export
	private JRadioButton btnYes;
	private int nMode = -1;    
	    
	public UtilExport(TableMainPanel tdp, String title) {
		this.tdp = tdp;
		this.title = title;
		
		setModal(true);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setTitle("Export Table Rows");
		
		createDialog();
		
		pack();
		this.setResizable(false);
		setLocationRelativeTo(null); 
    }
	private void createDialog() {
		// Files
		JRadioButton btnCSV = new JRadioButton("CSV"); btnCSV.setBackground(Color.white);
		btnCSV.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				nMode = ex_csv;
			}
		});
		JRadioButton btnHTML =  new JRadioButton("HTML");btnHTML.setBackground(Color.white); //CAS547 white for linux
		btnHTML.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				nMode = ex_html;
			}
		});
		
		JPanel rowPanel = Jcomp.createRowPanel();
        rowPanel.add(Box.createHorizontalStrut(15));
        rowPanel.add(new JLabel("Include Row column:")); rowPanel.add(Box.createHorizontalStrut(5));
        
        btnYes = new JRadioButton("Yes"); btnYes.setBackground(Color.white);
        JRadioButton btnNo = new JRadioButton("No");btnNo.setBackground(Color.white);
        ButtonGroup inc = new ButtonGroup();
		inc.add(btnYes);
		inc.add(btnNo);
		btnNo.setSelected(true); // CAS560 changed to no
		rowPanel.add(btnYes); rowPanel.add(Box.createHorizontalStrut(5));
		rowPanel.add(btnNo);
		
		JRadioButton btnFASTA = new JRadioButton("FASTA");btnFASTA.setBackground(Color.white);
		btnFASTA.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				nMode = ex_fasta;
			}
		});
		if (tdp.isSingle) btnFASTA.setEnabled(false);
   	  
		ButtonGroup grp = new ButtonGroup();
		grp.add(btnCSV);
		grp.add(btnHTML);
        grp.add(btnFASTA);  
    	btnCSV.setSelected(true);
    	nMode = ex_csv;
			
		JPanel selectPanel = Jcomp.createPagePanel();
		selectPanel.add(new JLabel("Table rows and columns")); selectPanel.add(Box.createVerticalStrut(5));
		selectPanel.add(btnCSV); 			selectPanel.add(Box.createVerticalStrut(5));
		selectPanel.add(btnHTML);			selectPanel.add(Box.createVerticalStrut(5));
        selectPanel.add(rowPanel);
        
        selectPanel.add(new JSeparator());
        selectPanel.add(new JLabel("Table sequence")); selectPanel.add(Box.createVerticalStrut(5));
        selectPanel.add(btnFASTA); 
        
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
		
		JPanel buttonPanel = Jcomp.createRowCenterPanel();
		buttonPanel.add(btnOK);			buttonPanel.add(Box.createHorizontalStrut(5));
		buttonPanel.add(btnCancel);		
		buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

      // Finish 
		JPanel mainPanel = Jcomp.createPagePanel();
		mainPanel.add(selectPanel); 	mainPanel.add(Box.createVerticalStrut(5));
		mainPanel.add(new JSeparator());
		mainPanel.add(buttonPanel);
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		add(mainPanel);
	}
    protected int getSelection() { return nMode; }
    
   
    /*********************************************************
     * Write CSV - works with 504
     */
    protected void writeCSV() {
    try {
    	boolean reset = false;
    	int nSelRows = tdp.theTable.getSelectedRowCount();
    	if (nSelRows>0) {
    		if (!Utilities.showConfirm2("Selected rows","Export " + nSelRows + " selected rows?" )) return ;
    	}
		PrintWriter outFH = getFileHandle(ex_csv, filePrefix + ".csv", true);
		if (outFH==null) return;
		
		if(nSelRows == 0) {
			tdp.theTable.selectAll();
			reset = true;
		}
		int [] selRows = tdp.theTable.getSelectedRows();
		
		outFH.println("### " +title);
		boolean incRow = btnYes.isSelected();
		tdp.setPanelEnabled(false);
		
		// Columns names
		for(int x=0; x<tdp.theTable.getColumnCount()-1; x++) {
			if (!incRow && x==0) continue; // CAS540 for row check
			
			outFH.print(reloadCleanString(tdp.theTable.getColumnName(x)) + ",");
		}
		outFH.println(reloadCleanString(tdp.theTable.getColumnName(tdp.theTable.getColumnCount()-1)));
		
		// rows
		for(int x=0; x<selRows.length; x++) {
			for(int y=0; y<tdp.theTable.getColumnCount()-1; y++) {
				if (!incRow && y==0) continue; // CAS540 for row check
				
				outFH.print(reloadCleanString(tdp.theTable.getValueAt(selRows[x], y)) + ",");
			}
			outFH.println(reloadCleanString(tdp.theTable.getValueAt(selRows[x], tdp.theTable.getColumnCount()-1)));
			outFH.flush();
		}
		System.out.println("Wrote " + selRows.length + " rows                  ");
		outFH.close();
		
		if(reset)
			tdp.theTable.getSelectionModel().clearSelection();
		tdp.setPanelEnabled(true);
	} catch(Exception e) {ErrorReport.print(e, "Write delim file");}
    }
   
    protected void writeHTML() { // CAS542 add
    try {
    	int nSelRows = tdp.theTable.getSelectedRowCount();
    	if (nSelRows>0) { // CAS560 add
    		if (!Utilities.showConfirm2("Selected rows","Export " + nSelRows + " selected rows?" )) return ;
    	}
    	
    	PrintWriter outFH = getFileHandle(ex_html, filePrefix + ".html", false);
		if (outFH==null) return;
	
		boolean incRow = btnYes.isSelected();
		
		tdp.setPanelEnabled(false);
		boolean reset = false;
		if(nSelRows == 0) {
			tdp.theTable.selectAll();
			reset = true;
		}
		int [] selRows = tdp.theTable.getSelectedRows();
		
		// html header
		outFH.print("<!DOCTYPE html><html>\n<head>\n"
				+ "<title>SyMAP Query Table Results</title>\n"
				+ "<style>\n"
				+  "body {font-family: Verdana, Arial, Helvetica, sans-serif;  font-size: 14px; }\n"
				+ ".ty    {border: 1px  solid black; border-spacing: 1px; border-collapse: collapse;margin-left: auto; margin-right: auto;}\n"
				+ ".ty td {border: 1px  solid black; padding: 5px; width: 80px; word-wrap: break-word; }" 
				+ "</style>\n</head>\n<body>\n"
				+ "<a id='top'></a>\n");
	 
		outFH.println("<p><center><b><big>" + title + "</big></b></center>\n"); // CAS560 'title' is SyMAP version - dbname
		outFH.println("<br><center>Filter: " + tdp.theSummary + "</center>\n");
		outFH.print("<p><table class='ty'>\n<tr>");
		
		// Columns names
		for(int x=0; x<tdp.theTable.getColumnCount(); x++) {
			if (!incRow && x==0) continue; 
			String col = reloadCleanString(tdp.theTable.getColumnName(x));
			col = col.replace(" ","<br>");
			outFH.print("<td><b><center>" + col + "</center></b></th>");
		}
		
		// rows
		for(int x=0; x<selRows.length; x++) {
			outFH.print("\n<tr>");
			for(int y=0; y<tdp.theTable.getColumnCount(); y++) {
				if (!incRow && y==0) continue; 
				
				outFH.print("<td>" + reloadCleanString(tdp.theTable.getValueAt(selRows[x], y)));
			}
			outFH.flush();
		}
		outFH.print("\n</table>\n");
		if (selRows.length>100) outFH.print("<p><a href='#top'>Go to top</a>\n");
		outFH.print("</body></html>\n");
		symap.Globals.prt("Wrote " + selRows.length + " rows                  ");
		outFH.close();
		
		if(reset)
			tdp.theTable.getSelectionModel().clearSelection();
		tdp.setPanelEnabled(true);
	} catch(Exception e) {ErrorReport.print(e, "Write delim file");}
    }
    
    /******************************************************************
     * Write fasta 
     */
    protected void writeFASTA() {
    try {
    	int nSelRows = tdp.theTable.getSelectedRowCount();
    	if (nSelRows>0) {
    		if (!Utilities.showConfirm2("Selected rows","Export " + nSelRows + " selected rows?" )) return ;
    	}
    	PrintWriter outFH = getFileHandle(ex_fasta, filePrefix + ".fa", true);
		if (outFH==null) return;
	
    	Thread inThread = new Thread(new Runnable() {
    	public void run() {
			try {
				TmpRowData rd = new TmpRowData(tdp);
				tdp.setPanelEnabled(false);
				
				DBconn2 dbc = tdp.queryPanel.getDBC(); // CAS563 moved from SyMAPQueryFrame; call here
				AlignPool aPool = new AlignPool(dbc);
				
				boolean reset = false;
				if(tdp.theTable.getSelectedRowCount() == 0) {
					tdp.theTable.selectAll();
					reset = true;
				}
				long startTime = Utilities.getNanoTime();
				int [] selRows = tdp.theTable.getSelectedRows();
				int selNum = selRows.length;
				if (selNum>500) {
					if (!Utilities.showContinue("Export FASTA", 
						"Selected " + selNum + " row to export. This is a slow function. \n" +
						"It may take over a minute to export each 500 rows of sequences.")) 
					{
						if(reset)tdp.theTable.getSelectionModel().clearSelection();
						tdp.setPanelEnabled(true);
						return;
					}
				}
				int seqNum = 1, pairNum=1;
					
				outFH.println("### " + title);
				for(int x=0; x<selNum; x++) {
					if (!rd.loadRow(x)) {
						outFH.close();
						if(reset) tdp.theTable.getSelectionModel().clearSelection();
						tdp.setPanelEnabled(true);
						return;
					}	
					for (int i=0; i<2; i++) {
						String seq = aPool.loadSeq(rd.start[i] + ":" + rd.end[i], rd.chrIdx[i]);
						String outString = ">SEQ" + String.format("%06d  ", seqNum)  
									+ rd.spName[i] + " Chr " + rd.chrNum[i] 
									+ " Start " + rd.start[i] + " End " + rd.end[i];
						outString += " Pair#" + pairNum;
						outFH.println(outString);
						outFH.println(seq);
						outFH.flush();
						seqNum++;
					}
					pairNum++;						
					Globals.rprt("Wrote: " + ((int)((((float)x)/selRows.length) * 100)) + "%");// CAS561 use rprt
				}
				outFH.close();
				
				Utilities.printElapsedNanoTime("Wrote " + (seqNum-1) + " sequences", startTime);
				
				if(reset)
					tdp.theTable.getSelectionModel().clearSelection();
				tdp.setPanelEnabled(true);
		} catch(Exception e) {ErrorReport.print(e, "Write fasta");}
		}});
		inThread.start();
	}
	catch(Exception e) {ErrorReport.print(e, "Save as fasta");}
    }
    ////////////////////////////////////////////////////////////////////////
    // CAS548 add append; CAS555 these two methods repeated in UtilReport
    private PrintWriter getFileHandle(int type, String fname, boolean bAppend) {
    	String saveDir = Globals.getExport(); // CAS547 change to call globals
		
		JFileChooser chooser = new JFileChooser(saveDir);
		chooser.setSelectedFile(new File(fname));
		if(chooser.showSaveDialog(tdp.queryFrame) != JFileChooser.APPROVE_OPTION) return null;
		if(chooser.getSelectedFile() == null) return null;
		
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
    private String reloadCleanString(Object obj) {
    	if(obj != null) {
    		String val = obj.toString().trim();
    		if (val.equals("")) return "''";
    		
    		val = val.replaceAll("[\'\"]", "");
    		val = val.replaceAll("\\n", " ");
    		val = val.replaceAll(",", ";"); // CAS540 quit returning with ''; just replace comma
    		return val;
    	}
    	else return "''";
	}
}
