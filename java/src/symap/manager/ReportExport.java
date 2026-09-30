package symap.manager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;

import database.DBconn2;
import symap.Globals;
import util.ErrorReport;
import util.Jcomp;
import util.Jhtml;
import util.Popup;

/********************************************************************************
 * Called from ManagerFrame Report pull-down; add CAS589
 */
public class ReportExport  extends JDialog {
	private static final long serialVersionUID = 1L;
	
	private DBconn2 dbc2;
	private Mpair mp;
	private Mproject mProj1, mProj2;
	
	protected ReportExport(DBconn2 dbc, Mpair mp) {
		this.dbc2 = dbc;
		this.mp = mp;
		mProj1 = mp.getProj1();
		mProj2 = mp.getProj2();
		
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setTitle("Export");
		
		mainPanel = Jcomp.createPagePanel();
		createPanel();
		mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		add(mainPanel);
		
		pack();
		setModal(true); // nothing else can happen until ok,cancel
		toFront();
		setResizable(false);
		setLocationRelativeTo(null); 
		setVisible(true);
	}
	/*******************************************/
	private void createPanel() {
		
		/*   Create Panel  */
		JPanel row = Jcomp.createRowPanel();
		row.add(Jcomp.createLabel("Export blocks with gene pairs"));
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(10));
		
		row = Jcomp.createRowPanel();
		radID =  Jcomp.createRadio("GFF ID", "Use GFF ID (if exists)");
		radGene =  Jcomp.createRadio("Gene#", "Use SyMAP Gene#");
		ButtonGroup grp = new ButtonGroup();
		grp.add(radGene); grp.add(radID); 
    	radID.setSelected(true);
    
		row.add(radID); row.add(Box.createHorizontalStrut(10));
		row.add(radGene);
		mainPanel.add(row); mainPanel.add(Box.createVerticalStrut(10));
    	
    	// Buttons
		btnOK = Jcomp.createButton("Create", "Write to file");
    	btnOK.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				new Blocks();
				setVisible(false);
			}
		});
		btnCancel = Jcomp.createButton(Jcomp.cancel, "Cancel action");
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
		boolean isl = Jhtml.isLinux();
		int sp = (isl) ? 60 : 30;
		
		JPanel buttonPanel = Jcomp.createRowPanel();
		buttonPanel.add(Box.createHorizontalStrut(sp));
		
		buttonPanel.add(btnOK);			buttonPanel.add(Box.createHorizontalStrut(sp));
		buttonPanel.add(btnCancel);		
		
		mainPanel.add(new JSeparator());
		mainPanel.add(Box.createVerticalStrut(8));
		mainPanel.add(buttonPanel);
	}
	/********************************************************/
	private BufferedWriter getFileHandle(String fname) {
    	String saveDir = Globals.getExport(); 
		
		JFileChooser chooser = new JFileChooser(saveDir);
		chooser.setSelectedFile(new File(fname));
		if(chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
		if(chooser.getSelectedFile() == null) return null;
		
		int bufsz = 1024;
		String saveFileName = chooser.getSelectedFile().getAbsolutePath();
		if(!saveFileName.endsWith(".txt")) saveFileName += ".txt";
		symap.Globals.prt("Exporting blocks to " + saveFileName);
		
		if (new File(saveFileName).exists()) {
			if (!Popup.showConfirm2("File exists","File '" + saveFileName + "' exists.\nOverwrite?")) return null;
		}
		BufferedWriter out=null;
		try {
			out = new BufferedWriter(new FileWriter(saveFileName, false), bufsz);
		}
		catch (Exception e) {ErrorReport.print(e, "Cannot open file - " + saveFileName);}
		return out;
    }
	/***********************************************************/
	private class Blocks {
		private TreeMap <String, Block> blockMap = new TreeMap <String, Block> ();
		
		private Blocks() {
		try {
			createBlocks();
			
			BufferedWriter fhOut = getFileHandle("blocks.txt");
			for (String key : blockMap.keySet()) {
				Block b = blockMap.get(key);
				fhOut.write("Block: " + b.chr1 + " " + b.chr2 + " #" + b.blknum + "\n");
				for (int hitnum : b.geneMap.keySet()) {
					String line = b.geneMap.get(hitnum);
					fhOut.write(String.format("#%-5d   ", hitnum)+ line + "\n");
				}
			}
			fhOut.close();
			Globals.prt("Wrote " + blockMap.size() + " blocks");
		}
		catch (Exception e) {ErrorReport.print(e, "Export Blocks");}
		}
		/****************************************************************/
		private void createBlocks() {
		try {
			DBconn2 tdbc = new DBconn2("Export" + DBconn2.getNumConn(), dbc2);
			 String sql = "SELECT PA.grp_idx, PA.name, PA.tag,  PH.hitnum, PH.grp1_idx, PH.grp2_idx, B.blocknum"
		    + " FROM pseudo_hits AS PH"
		    + " LEFT JOIN pseudo_hits_annot AS PHA ON PH.idx = PHA.hit_idx"
			+ " LEFT JOIN pseudo_annot AS PA ON PHA.annot_idx = PA.idx"
			+ " LEFT JOIN pseudo_block_hits AS PBH ON PBH.hit_idx = PH.idx"
			+ " LEFT JOIN blocks AS B ON B.idx = PBH.block_idx"
			+ " where PH.pair_idx=" + mp.getPairIdx() 
			+ " AND PBH.block_idx is not null AND PH.gene_overlap=2 ORDER BY PH.hitnum asc";
			 
			boolean isID = radID.isSelected();
			Block curBlk;
			
			ResultSet rs = tdbc.executeQuery(sql);
			while (rs.next()) {
				int i=1;
				int gidx 	= rs.getInt(i++);
				String anno = rs.getString(i++);
				String tag 	= rs.getString(i++);
				int hitnum 	= rs.getInt(i++);
				int gidx1 	= rs.getInt(i++);
				int gidx2 	= rs.getInt(i++);
				int blknum 	= rs.getInt(i++);
				
				String key = gidx1 + ":" + gidx2 + ":" + blknum;
				if (blockMap.containsKey(key)) curBlk = blockMap.get(key);
				else {
					curBlk = new Block();
					curBlk.chr1 = mProj1.getGrpFullNameFromIdx(gidx1);
					curBlk.chr2 = mProj2.getGrpFullNameFromIdx(gidx2);
					curBlk.blknum = blknum;
					blockMap.put(key, curBlk);
				}
				String line = (curBlk.geneMap.containsKey(hitnum)) ? curBlk.geneMap.get(hitnum) : "";
				String name="";
				if (isID) {
					if (!anno.isBlank() && anno.contains(Globals.idKey)) {
						String [] tok = anno.split(";");
						for (String tuple : tok) {
							if (tuple.startsWith(Globals.idKey)) {
								name = tuple.split("=")[1];
								break;
							}
						}
					} else name="NoID";
				}
				else {
					name = tag.substring(0, tag.indexOf("(")).trim();
				}
				name = String.format("%-12s", name);
				
				if (gidx==gidx1) line = name + " " + line;
				else             line = line + " " + name;
				curBlk.geneMap.put(hitnum, line);
			}
			rs.close();
			tdbc.close();	
		} catch (Exception e) {ErrorReport.print(e, "Export Blocks");}
		}
		private class Block {
			int blknum;
			String chr1, chr2;
			TreeMap <Integer, String> geneMap = new TreeMap <Integer, String> ();
		}
	}
	
	/**************************************************/
	private JPanel mainPanel;
	private JRadioButton radGene, radID;
    private JButton btnOK=null, btnCancel=null;
}
