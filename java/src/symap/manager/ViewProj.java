package symap.manager;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Point;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import backend.Utils;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.TreeMap;

import database.DBconn2;
import symap.Globals;
import symap.closeup.AlignPool;
import util.ErrorReport;
import util.Jcomp;
import util.Utilities;

/***********************************************
 * Project View; displays information and has an export; created CAS589
 * CAS591 add Export unspliced transcripts
 */
public class ViewProj extends JDialog implements ActionListener {
	private static final long serialVersionUID = 1L;

	private JButton closeButton, exportButton;
	
	private DBconn2 dbc;
	private Mproject mProj;
	private ManagerFrame mFrame;
	private boolean isCDS=false;
	
	protected ViewProj(DBconn2 dbc, Mproject mProj, ManagerFrame parent) {
		super();
		setModal(false);
		setTitle("View "); 
		setResizable(true);
		
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
		addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {}
		});
	
		this.dbc = dbc;
		this.mProj = mProj;
		this.mFrame = parent;
		isCDS = mProj.isLoadCDS();
		
		String theInfo = mProj.loadProjectForView();
		
		JTextArea messageArea = new JTextArea(theInfo);
		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		exportButton = Jcomp.createButton("Export", "Export file of sequences"); 
		exportButton.addActionListener(this);
		
		closeButton = Jcomp.createButton(Jcomp.close, "Close window"); 
		closeButton.addActionListener(this);
		
		JPanel buttonPanel = new JPanel(); buttonPanel.setBackground(Color.white);
		buttonPanel.add(exportButton); buttonPanel.add(Box.createVerticalStrut(10));
		buttonPanel.add(closeButton);
	
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(sPane,BorderLayout.CENTER);
		getContentPane().add(buttonPanel,BorderLayout.SOUTH);
		pack();
		
		Dimension d = new Dimension (900, 400); // w,h
		if (getWidth() >= d.width || getHeight() >= d.height) setSize(d);
		toFront(); //setAlwaysOnTop(true); 										// doesn't work on Ubuntu
		setLocationRelativeTo(null);	
		setVisible(true);
	}
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == closeButton) {		
			setVisible(false); 
		}
		if (e.getSource() == exportButton) {		
			new Export(this);
		}
	}
	private class Export extends JDialog  {
		private static final long serialVersionUID = 3194236729803023525L;

		private boolean bOkay=false;
		private JRadioButton radExon, radProtein, radTrans;
		private FileWriter logFW=null;
		
		private Export(ViewProj parent) {
			createMenu(parent);
			
			if (bOkay) export();
		}
		private void createMenu(ViewProj parent) {
			setModal(true);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			setTitle("Export options");
			
			JPanel page = Jcomp.createPagePanel();
			radProtein = Jcomp.createRadio("Proteins", "Create amino acid sequence from CDSs");
			
			
			String label = (isCDS) ? "Spliced CDSs" : "Spliced Exons";
			radExon =    Jcomp.createRadio(label, "Create spliced nucleotide sequences from exons or CDSs");
			
			radTrans = Jcomp.createRadio("Unspliced transcripts", "Create primary unspliced transcripts");
			
			ButtonGroup rg = new ButtonGroup();
			rg.add(radExon); rg.add(radProtein); rg.add(radTrans);
			
			if (!mProj.isLoadCDS()) {
				radProtein.setEnabled(false);
				radExon.setSelected(true);
			}
			else radProtein.setSelected(true);
			
			page.add(Box.createVerticalStrut(5)); 	
			page.add(radProtein); 	page.add(Box.createVerticalStrut(3));
			page.add(radExon); 		page.add(Box.createVerticalStrut(3));
			page.add(radTrans);
				
			page.add(new JSeparator());
			
			// Buttons
			JPanel row = Jcomp.createRowPanel();
			row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(5));
			JButton btnOK = Jcomp.createButton("Export", "Export to file"); 	
	    	btnOK.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bOkay=true;
					setVisible(false);
				}
			});
	    	row.add(Box.createHorizontalStrut(5));
	    	row.add(btnOK); 	row.add(Box.createHorizontalStrut(5));
	    	
	    	JButton btnCancel = Jcomp.createButton(Jcomp.cancel, "Cancel export"); 
	    	btnCancel.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bOkay=false;
					setVisible(false);
				}
			});
	    	row.add(btnCancel); row.add(Box.createHorizontalStrut(5));
			
	    	page.add(row);
	    	page.add(Box.createVerticalStrut(5));
	    	
	    	page.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
	    	add(page);
	    	
			pack();
			setModal(true); // nothing else can happen until ok,cancel
			setAlwaysOnTop(true); //toFront();
			this.setResizable(false);
			
			// Everything was copied from TextShowInfo, but this would go behind parent here.
			Point parentLocation = parent.getLocationOnScreen();
			int parentWidth = parent.getWidth();
			int x = parentLocation.x + parentWidth;
			int y = parentLocation.y;
			setLocation(x, y);
			setVisible(true);
		}
		/*************************************************************/
		private void export() {
		try {
			DBconn2 tdbc = new DBconn2("Export" + DBconn2.getNumConn(), dbc);
			
			boolean isExonCDS = radExon.isSelected(), 
					isProt  = radProtein.isSelected(); // else radTrans.isSelected()
			
			String file;
			if (isExonCDS) 		file =  mProj.getAnnoType() + ".fna"; // CDS or Exon
			else if (isProt) 	file = "Prot.faa";
			else 				file = "Unspliced.fna";
		
			BufferedWriter outFH = getFileHandle(mProj.strDBName+ file);
			if (outFH==null) return;
			
			Thread inThread = new Thread(new Runnable() {
		    public void run() {
			try {
				long time = Utilities.getNanoTime();
				AlignPool aPool = new AlignPool(tdbc);
				
				TreeMap <Integer, String> grpIdxMap = mProj.getGrpIdxFullMap();
				int cntOut=0, cntNoID=0, cntGrp=1, numGrp = grpIdxMap.size(), cntGrpEnd=0, cntSeq=0;
				
				for (int grpIdx : grpIdxMap.keySet()) {
					String chr = grpIdxMap.get(grpIdx);
					Globals.rprt("Exporting " + chr);
					ArrayList <String> tagList = new ArrayList <String>  ();
					
					ResultSet rs = dbc.executeQuery("select idx, strand, tag, name from pseudo_annot "
						+ " where type='gene' and grp_idx=" + grpIdx + " order by start");
					
					while(rs.next()) {
						int id = rs.getInt(1);
						String strand = rs.getString(2);
						String tag =rs.getString(3);
						String anno=rs.getString(4);
						
						tag = chr + "." + tag.substring(0, tag.indexOf("("));
						String name="", rnaID="", protID="";
						
						if (!anno.isBlank() && anno.contains(Globals.idKey)) {
							String [] tok = anno.split(";");
							for (String tuple : tok) {
								if (name.isBlank() && tuple.startsWith(Globals.idKey)) {
									name = tuple.split("=")[1];
								}
								else if (rnaID.isBlank() && tuple.startsWith(Globals.rnaID)) {
									rnaID = tuple.split("=")[1];
									if (rnaID.contains("(")) rnaID = rnaID.substring(0, rnaID.indexOf("("));
								}
								else if (protID.isBlank() && tuple.startsWith(Globals.protID)) {
									protID = tuple.split("=")[1];
								}
							}
						} else {name="NoID"; cntNoID++;}
						
						tagList.add(id+":" + strand +":" + name + "   " + rnaID + "   " + protID + "   " + tag);
					}
					rs.close();
					
					Globals.rprt("Export " + grpIdxMap.get(grpIdx) + " genes " + tagList.size());
					
					int cnt=0;
					for (String line : tagList) {
						String [] tok = line.split(":");
						int annoIdx = Integer.parseInt(tok[0]);
						String strand = tok[1];
						String name = tok[2];
						
						String seq;
						if (isProt) 		seq = aPool.loadProtein(annoIdx, grpIdx, strand); 
						else if (isExonCDS) seq = aPool.loadSpliced(annoIdx, grpIdx, strand);
						else                seq = aPool.loadUnsplicedTrans(annoIdx, grpIdx);
						
						if (isProt) {
							int cntend=0;
							for (int i=0; i<seq.length(); i++) 
								if (seq.charAt(i)=='*') cntend++;
							if (cntend>1) cntGrpEnd++;
						}
						
						cntSeq += seq.length();
						outFH.write(">" + name + "\n" + seq + "\n"); 
						cntOut++;
						
						if (cnt++ %100 == 0) {
							Globals.rprt("Export " + cntGrp + "/" + numGrp + "  "+ grpIdxMap.get(grpIdx) + " " + cnt + "/" + tagList.size());
							outFH.flush(); 
						}
					}
					Globals.rclear();
					String msg;
					if (isProt) 		msg = String.format("   %-10s  %,5d Proteins   %,10d AAs    %,4d multi-stops",
												grpIdxMap.get(grpIdx), tagList.size(), cntSeq, cntGrpEnd);
					else if (isExonCDS) msg = String.format("   %-10s  %,5d Spliced %s  %,10dbp", 
												grpIdxMap.get(grpIdx), tagList.size(), mProj.getAnnoType(), cntSeq);
					else 				msg = String.format("   %-10s  %,5d Unspliced   %,10dbp", 
												grpIdxMap.get(grpIdx), tagList.size(), cntSeq);
					
					Utils.prtToFile(logFW,msg); 
					cntSeq=cntGrpEnd=0;
					outFH.flush();
					cntGrp++;
				}			
				outFH.close();
			
				Utils.prtToFileNano(logFW, String.format("Wrote %,d genes to file", cntOut), time);
				if (cntNoID>0) Globals.prt(String.format("   %,d genes had no 'ID=' keyword", cntNoID));
				if (logFW!=null) {logFW.close(); logFW=null;}
			} 
			catch(Exception e) {ErrorReport.print(e, "Write fasta");}
			}});
			inThread.start();
			tdbc.close();
		}
		catch (Exception e) {ErrorReport.print(e, "export");}
		}
		
		/*************************************************************/
		private BufferedWriter getFileHandle(String fname) {
	    	String saveDir = Globals.getExport(); 
			
			JFileChooser chooser = new JFileChooser(saveDir);
			chooser.setSelectedFile(new File(fname));
			if(chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
			if(chooser.getSelectedFile() == null) return null;
			
			int bufsz = 1024 * 128;
			String saveFileName = chooser.getSelectedFile().getAbsolutePath();
			
			logFW = mFrame.getLoadLog(); // log/db_load.log
			
			if (radExon.isSelected()) { // Exons or CDSs
				if (!saveFileName.contains(".")) saveFileName += ".fna";
				String what = (isCDS) ? "   Spliced CDSs to" : "   Spliced Exons to";
				Utils.prtToFileHeader(logFW,"Exporting " + mProj.getDBName());
				Utils.prtToFile(logFW, what + saveFileName);
			}
			else if (radTrans.isSelected()) {
				if (!saveFileName.contains(".")) saveFileName += ".fna";
	    		Utils.prtToFileHeader(logFW,"Exporting " + mProj.getDBName());
	    		Utils.prtToFile(logFW, "   Unspliced transcripts to " + saveFileName);
			}
			else {
	    		if (!saveFileName.contains(".")) saveFileName += ".faa";
	    		Utils.prtToFileHeader(logFW,"Exporting " + mProj.getDBName());
	    		Utils.prtToFile(logFW, "   Proteins to " + saveFileName);
			}
			if (new File(saveFileName).exists()) {
				String [] options = {"Cancel", "Confirm"};
				String msg = "File '" + saveFileName + "' exists.\nOverwrite?";
				int ret = JOptionPane.showOptionDialog(this, 
						msg, "File Exists", JOptionPane.YES_NO_OPTION, 
						JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
				if (ret == 0) return null;
			}
			BufferedWriter out=null;
			try {
				out = new BufferedWriter(new FileWriter(saveFileName, false), bufsz);
			}
			catch (Exception e) {ErrorReport.print(e, "Cannot open file - " + saveFileName);}
			return out;
	    }
	}
}
