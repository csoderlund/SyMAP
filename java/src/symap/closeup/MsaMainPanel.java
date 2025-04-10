package symap.closeup;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;

import symap.Globals;
import symapQuery.QueryFrame;
import symapQuery.TableMainPanel;
import util.ErrorReport;
import util.Jcomp;
import util.Utilities;
/**************************************
 * For Symap Query: align selected set using muscle or mafft (CAS563 add MAFFT)
 * Called from SyMAPQueryFrame; calls methods to build alignments and displays
 * MsaPanel is the actual align panel and MsaRun runs the alignment
 * 
 * CAS521: pass in shortName instead of computing here. pass in lines for the legend (lower table)
 * CAS563: rearrange, cleanup, was AlignmentViewPanel; add split pane
 */
public class MsaMainPanel extends JPanel {
	private static final long serialVersionUID = -2090028995232770402L;
	private String [] tableLines;
	private String sumLines=""; 			// created in AlignRun
	private String tabName, tabAdd, resultSum;
	
	public MsaMainPanel(TableMainPanel tablePanel, QueryFrame parentFrame, String [] names, String [] seqs, String [] tabLines,
			String progress, String msaSum, String tabName, String tabAdd, String resultSum,
			boolean bTrim, boolean bAuto, int cpu){
		
		this.tablePanel = tablePanel;
		this.queryFrame = parentFrame;
		this.tableLines = tabLines;
		this.tabName = tabName;
		this.tabAdd = tabAdd;
		this.resultSum = resultSum;
		
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBackground(Color.WHITE);
		
		buildAlignThread(names, seqs, progress, msaSum, bTrim, bAuto, cpu);
	}
	
	private void buildAlignThread(String [] names, String [] seqs, String progress, String msaSum, 
			boolean bTrim,  boolean bAuto, int cpu) {
		
		if (theThread == null){
			theThread = new Thread(new Runnable() {
				public void run() {
					try {
						createProgress();
						
						bDone=false;
					
						boolean rc = runAlign(names, seqs, progress, msaSum, bTrim, bAuto, cpu);
						if (!rc) return;	// Tab will still occur, but with progress error shown
						if (bStopped) return;
						
						bDone=true;
						
						createButtonPanel();
						createSplitPane();
						
						add(buttonPanel);
						add(splitPane);
						
						showProgress(false);
						updateExportButton();
						buildFinish();
						
						if (isVisible()) { 		//Makes the table appear
							setVisible(false);
							setVisible(true);
						}
					} 
					catch (Exception e) {ErrorReport.print(e, "Build Alignment");}
				}
			});
			theThread.setPriority(Thread.MIN_PRIORITY);
			theThread.start();
		}		
	}
	/* replace tab: add to result table;  Has to be done from thread, but not in thread */
	private void buildFinish() { 
		if (bStopped) return;					// if it does not on result table after stop, cannot remove
		
		String oldTab = tabName+":"; 			// same as QueryFrame.makeTabMSA
		String newTab = tabName + " " + tabAdd;
		
		String [] resultVal = new String[2];
 		resultVal[0] = newTab; 						// left of result table panel
 		resultVal[1] = resultSum;					// right of result table panel with query filters
 		
		queryFrame.updateTabText(oldTab,newTab, resultVal);
		
		tablePanel.setMsaButton(true);
	}
	/*****************************************************/
	private boolean runAlign(String [] names, String [] sequences, 
			String status, String msaSum, boolean bTrim, boolean bAuto, int cpu) {
		
		String fileName = "temp";
		msaData = new MsaRun(this, fileName, progressField);
		for(int x=0; x<names.length; x++)
			msaData.addSequence(names[x], sequences[x]);
		
		boolean rc=true;
		updateProgress(status);
		if (msaSum.startsWith("MUS")) rc = msaData.alignMuscle(status, bTrim);
		else        				  rc = msaData.alignMafft(status, bTrim, bAuto, cpu);
		
		if (bStopped) return false; // message in stopThread
		else if (!rc) {
			if (msaSum.startsWith("MUS")) 	Globals.prt("Try running MAFFT");
			else 							Globals.prt("Try running MAFFT with 'auto' unchecked; or try running MUSCLE");
			
			updateProgress("Error running MSA - see terminal");
			return false;
		}
		sumLines = "   " + msaSum + ": " + msaData.finalStats;
		return true;
	}
	private void refreshAlign() {
		try {
			int scale = cmbRatio.getSelectedIndex()+1;
			msaPanel.setBasesPerPixel(scale);
			
			boolean showText = btnShowType.getText().equals("Show Sequences");
			if (showText) msaPanel.setDrawMode(MsaPanel.GRAPHICMODE);
			else		  msaPanel.setDrawMode(MsaPanel.TEXTMODE);
			
			msaScroll.setViewportView(msaPanel); // refreshes scrollbar
		}
		catch (Exception e) {ErrorReport.print(e, "split pane");}
	}
	
	private void createSplitPane() {
		createLowerPanel();
		createMsaPanel();
		
		splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, msaScroll, lowerScroll);
		splitPane.setOneTouchExpandable(true);
		splitPane.setDividerLocation(350);
		splitPane.setResizeWeight(0.5);
		
		//Provide minimum sizes for the two components in the split pane
		msaScroll.setMinimumSize(new Dimension(100, 100));
		lowerScroll.setMinimumSize(new Dimension(100, 50));
	}
	
	/****************************************************/
	private void createMsaPanel() {
		msaPanel = new MsaPanel(msaData);
		
		msaScroll = new JScrollPane();	// CAS563 add scroll
		msaScroll.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				handleClickAlign(e);
			}
		});		
	    msaScroll.setPreferredSize(new Dimension(0,0));  
		msaScroll.setViewportView(msaPanel);
	}
	/****************************************************/
	private void createLowerPanel() {
		lowerPanel = new TablePanel(); 
		
		lowerScroll = new JScrollPane();	// CAS563 add scroll
		lowerScroll.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				handleClickAlign(e);
			}
		});		
		lowerScroll.setPreferredSize(new Dimension(0,0));  
		lowerScroll.setViewportView(lowerPanel);
	}
	/****************************************************/
	private void createButtonPanel() {
		buttonPanel = Jcomp.createPagePanel();
		add(Box.createVerticalStrut(10));
		
		JPanel theRow = Jcomp.createRowPanel();
		cmbRatio = new JComboBox <String> (); // CAS563 this was a class, but not necessary since index+1=ratio
		cmbRatio.addItem("Zoom 1:1");
		cmbRatio.addItem("Zoom 1:2");
		cmbRatio.addItem("Zoom 1:3");
		cmbRatio.addItem("Zoom 1:4");
		cmbRatio.addItem("Zoom 1:5");
		cmbRatio.addItem("Zoom 1:6");
		cmbRatio.addItem("Zoom 1:7");
		cmbRatio.addItem("Zoom 1:8");
		cmbRatio.addItem("Zoom 1:9");
		cmbRatio.addItem("Zoom 1:10");
		cmbRatio.setBackground(Color.WHITE);
		cmbRatio.setSelectedIndex(0);
		cmbRatio.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				refreshAlign();
			}
		});	
		theRow.add(cmbRatio); theRow.add(Box.createHorizontalStrut(10));
		
		btnShowType = Jcomp.createButton("Show Sequences", "Change graphics from graphic to sequence");
		btnShowType.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if(btnShowType.getText().equals("Show Graphic")) {
					btnShowType.setText("Show Sequences");
					cmbRatio.setEnabled(true);
				}
				else {
					btnShowType.setText("Show Graphic");
					cmbRatio.setEnabled(false);
				}
				refreshAlign();
			}
		});
		theRow.add(btnShowType); theRow.add(Box.createHorizontalStrut(10));
		
		btnExport = Jcomp.createButton("Export", "Export consensus and aligned sequences");
		btnExport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) { // CAS563 change to file chooser; was just input popup
				if (msaPanel == null) return;
				
				String fname = "consensus.fa";
				String saveDir = Globals.alignDir; 
				
				JFileChooser chooser = new JFileChooser(saveDir);
				chooser.setSelectedFile(new File(fname));
				if (chooser.showSaveDialog(queryFrame) != JFileChooser.APPROVE_OPTION) return;
				if (chooser.getSelectedFile() == null) return;
		
				fname = chooser.getSelectedFile().getName();		
				if(fname != null && fname.length() > 0) {
					if(!fname.endsWith(".fa")) fname += ".fa";
					msaData.exportConsensus(fname);
				}
			}
		});
		theRow.add(btnExport);			
		theRow.setMaximumSize(theRow.getPreferredSize());
		
		buttonPanel.add(theRow);
	}
	
	private void handleClickAlign(MouseEvent e) { 
		if(msaPanel == null) return;
		
		// Convert to view relative coordinates
		int viewX = (int) (e.getX() + msaScroll.getViewport().getViewPosition().getX());
		int viewY = (int) (e.getY() + msaScroll.getViewport().getViewPosition().getY());
		
		// Convert to panel relative coordinates
		int nPanelX = viewX - msaPanel.getX();
		int nPanelY = viewY - msaPanel.getY();

		if (msaPanel.contains(nPanelX, nPanelY)) {// Click is in current panel, let the object handle it
			msaPanel.handleClick(e, new Point(nPanelX, nPanelY));
		}
		else if (!e.isShiftDown() && !e.isControlDown()) {
			msaPanel.selectNoRows();
			msaPanel.selectNoColumns();
		}
	}
	/*************************************************/
	private void updateExportButton() {
		if(msaData != null && msaData.hasFilename())
			btnExport.setEnabled(true);
		else
			btnExport.setEnabled(false);
	}
	/*************************************************
	 * Status at top of page while alignment is running
	 */
	private void createProgress() {
		progressField = new JTextField(130); // CAS563 was 100
		progressField.setEditable(false);
		progressField.setMaximumSize(progressField.getPreferredSize());
		progressField.setBackground(Color.WHITE);
		progressField.setBorder(BorderFactory.createEmptyBorder());
		
		btnCancel = new JButton("Stop");
		btnCancel.setBackground(Color.WHITE);
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				stopThread();
			}
		});
		
		add(progressField);
		add(Box.createVerticalStrut(10));
		add(btnCancel);
	}
	private void showProgress(boolean show) {
		btnCancel.setVisible(show);
		progressField.setVisible(show);
	}
	private void updateProgress(String status) {
		progressField.setText(status);
		repaint();
	}
	private void stopThread() {
		if (theThread==null) return;
		
		if (bDone) {
			if (!Utilities.showConfirm2("Stop MSA", "MSA complete. Stop displaying results?")) return;
		}
		
		tablePanel.setMsaButton(true); // unfreeze button
		
		queryFrame.removeResult(this); // remove from left side
		
		Utilities.showInfoMessage("Stop MSA", // writes to terminal
				"You must stop MAFFT (distbfast) or MUSCLE (muscle) manually; "
				+ "\n   see the online documentation for help. ");
		
		bStopped=true;
		try {
			theThread.interrupt(); 	
		}
		catch (Exception e) {};
	}
	
	/***********************************************
	 * Draws the table of hit information beneath alignment; CAS moved from its own LegendPanel
	 */
	private class TablePanel extends JPanel {
		private static final long serialVersionUID = -5292053168212278988L;
		private static Font font = new Font("Courier", Font.PLAIN, 12); 
		private String [] tabPlus;
		
		private TablePanel () {
			// Add sumLines to keyLines
			int ex=2, nlines = tableLines.length;
			tabPlus = new String[nlines+ex];
			for (int i=0; i<nlines+ex; i++) tabPlus[i] = "";
			for (int i=0; i<nlines; i++) tabPlus[i] = tableLines[i];
			tabPlus[nlines+(ex-1)] = sumLines;
			
			setLayout(null);
	        setBackground(Color.WHITE);
	        setAlignmentX(Component.LEFT_ALIGNMENT);
	        
	        int width = getFontMetrics(font).stringWidth("A")*3; // the font width is 7
	        int w = 0, h = (tabPlus.length * width) + 15;
	        for (String n : tabPlus) if (w<n.length()) w=n.length();
	        w *= 8;
	        	
	        setMinimumSize(new Dimension(w, h));
	        setPreferredSize(getMinimumSize());
	        setMaximumSize(getMinimumSize());
		}
		
		public void paintComponent(Graphics g) {
			super.paintComponent( g );
			Graphics2D g2 = (Graphics2D)g;
			
			g2.setFont(font);
			int y = 5;
			for (String n : tabPlus) drawString(g2,  n,  10, y+=15);
		}
		private static void drawString(Graphics2D g2,String s, int x, int y) {
		    g2.setFont(font);
			g2.setColor(Color.BLACK);
			g2.drawString(s, x+10, y+9);
		}
	}
	/********************************************************/
	private QueryFrame queryFrame = null;
	private TableMainPanel tablePanel = null;
	
	private JPanel buttonPanel = null;
	private JSplitPane splitPane = null;
	
	private JComboBox <String> cmbRatio = null;
	private JButton btnShowType = null, btnExport = null;
	
	private JTextField progressField = null;
	private JButton btnCancel = null;
	
	private Thread theThread = null; //Thread used for building the sequence data
	private boolean bDone=false;	 // If they Stop, but it just finished, this will be true
	protected boolean bStopped=false;
	
	private MsaRun msaData = null;
	private MsaPanel msaPanel = null;
	private JScrollPane msaScroll = null, lowerScroll = null;
	private TablePanel lowerPanel =  null;
}
