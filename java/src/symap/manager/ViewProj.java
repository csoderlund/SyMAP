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
import util.Popup;
import util.Utilities;

/***********************************************
 * Project View; displays information and has an export; created CAS589
 */
public class ViewProj extends JDialog implements ActionListener {
	private static final long serialVersionUID = 1L;

	private JButton closeButton, exportButton;
	
	private DBconn2 dbc;
	private Mproject mProj;
	private ManagerFrame mFrame;
	
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
		
		private JRadioButton radGene, radID, radTrans, radCDS;
		
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
			
			radID =  Jcomp.createRadio("GFF ID", "Use GFF ID from the GFF");
			radGene =  Jcomp.createRadio("Gene#", "Use SyMAP Gene#");
			ButtonGroup grp = new ButtonGroup();
			grp.add(radGene); grp.add(radID); 
	    	radID.setSelected(true);
	    	
			JPanel row = Jcomp.createRowPanel();
			row.add(Jcomp.createLabel("Use", "Select type of identifier"));row.add(Box.createHorizontalStrut(5));
			row.add(radID); row.add(Box.createHorizontalStrut(5));
			row.add(radGene);
			// ignore for now; page.add(row); page.add(Box.createVerticalStrut(10));
			
			radTrans = Jcomp.createRadio("Export RNAs", "Create nucleotide sequence from spliced exons or CDSs");
			radCDS = Jcomp.createRadio("Export proteins", "Create amino acid sequence from spliced CDS");
			if (!mProj.isLoadCDS()) radCDS.setEnabled(false);
			
			ButtonGroup rg = new ButtonGroup();
			rg.add(radTrans); rg.add(radCDS); 
			radTrans.setSelected(true);
			
			page.add(Box.createVerticalStrut(5)); 	
			page.add(radTrans); 		page.add(Box.createVerticalStrut(3));
			page.add(radCDS); 		
				
			page.add(new JSeparator());
			
			// Buttons
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
	    	
	    	JButton btnInfo = Jcomp.createBorderIconButton("/images/info.png", "Quick Help Popup");
	    	btnInfo.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					popupHelp();
				}
			});
			row.add(btnInfo); 	row.add(Box.createHorizontalStrut(5));
			
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
			
			boolean isGene = radGene.isSelected(), isCDS = radCDS.isSelected();
			String file = (isCDS) ? "Pr.faa" : "Tr"+mProj.getAnnoType() + ".fna"; // Pr.faa, TrExon.fna or TrCDS.fna
			
			BufferedWriter outFH = getFileHandle(mProj.strDBName+ file);
			if (outFH==null) return;
			
			Thread inThread = new Thread(new Runnable() {
		    public void run() {
			try {
				long time = Utilities.getNanoTime();
				AlignPool aPool = new AlignPool(tdbc);
				
				TreeMap <Integer, String> grpIdxMap = mProj.getGrpIdxFullMap();
				int cntOut=0, cntNoID=0, cntGrp=1, numGrp = grpIdxMap.size();
				
				for (int grpIdx : grpIdxMap.keySet()) {
					Globals.rprt("Exporting " + grpIdxMap.get(grpIdx));
					
					ArrayList <String> tagList = new ArrayList <String>  ();
					
					ResultSet rs = dbc.executeQuery("select idx, strand, tag, name from pseudo_annot "
						+ " where type='gene' and grp_idx=" + grpIdx + " order by start");
					
					while(rs.next()) {
						String tag =rs.getString(3);
						String anno=rs.getString(4);
						String name="";
						
						if (isGene) name = tag.substring(0, tag.indexOf("("));
						else if (!anno.isBlank() && anno.contains(Globals.idKey)) {
							String [] tok = anno.split(";");
							for (String tuple : tok) {
								if (tuple.startsWith(Globals.idKey)) {
									name = tuple.split("=")[1];
									break;
								}
							}
						} else {name="NoID"; cntNoID++;}
						
						tagList.add(rs.getInt(1)+":" + rs.getString(2)+":" + name);
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
						if (isCDS) seq = aPool.loadProtein(annoIdx, grpIdx, strand); 
						else       seq = aPool.loadSpliced(annoIdx, grpIdx, strand);
						
						outFH.write(">" + name + "\n" + seq + "\n");
						cntOut++;
						
						if (cnt++ %10 == 0) 
							Globals.rprt("Export " + cntGrp + "/" + numGrp + "  "+ grpIdxMap.get(grpIdx) + " " + cnt + "/" + tagList.size());
					}
					outFH.flush();
					cntGrp++;
				}			
				outFH.close();
				Globals.rclear();
				
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
			
			if (radTrans.isSelected()) {
				if (!saveFileName.contains(".")) saveFileName += ".fna";
				
				Utils.prtToFileHeader(logFW,"Exporting " + mProj.getDBName());
				Utils.prtToFile(logFW, "   Spliced transcripts to " + saveFileName);
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
		private void popupHelp() {
			String msg = "Export a file of spliced sequences for each gene in project.";
			msg += "\nTranscripts: These are the spliced nucleotide sequences";
			msg += "\nProteins: These are the translated spliced amino acid sequences."
				 + "\n          The project must have CDSs loaded.\n";
			Popup.displayInfoMonoSpace(this, "Quick Help", msg, false);
		}
	}
}
