package symapQuery;

import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import database.DBconn2;
import symap.Globals;
import symap.Ext;			
import symap.closeup.AlignPool;
import symap.closeup.SeqData;
import symap.drawingpanel.SyMAP2d;
import symap.mapper.HfilterData;
import symap.sequence.Sequence;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.util.HashMap;

import util.ErrorReport;
import util.Jcomp;
import util.Utilities;

/**********************************************************
 * Selected row(s): Contains the MSA align and 2D display
 * Added MSA CAS563; added 2D CAS564 - both moved from TableMainPanel
 */
public class UtilSelect {
	private TableMainPanel tablePanel;
	
	protected UtilSelect(TableMainPanel tdp) {
		this.tablePanel = tdp;
	}
	protected void msaAlign() {
		new MsaAlign();
	}
	protected void synteny2D(int numRows, int selIndex, int pad,  boolean isChkNum, Vector <Integer> grpIdxVec) {
		
		new Synteny2D(numRows, selIndex, pad, isChkNum, grpIdxVec);
	}
	/*******************************************************************************************/
	protected class Synteny2D { 
		private int selIndex;				// REGION, SET, BLOCK, GROUP
		private int pad;					// how much to pad from the selected hit
		private boolean isChkGene;			// display geneNum, else annotation
		private Vector <Integer> grpIdxVec; // the group hits to highlight; returns the values
		
		protected int [] hitNums = {-1, -1};
		
		private Synteny2D(int numRows, int selIndex,  int pad,  boolean isChkNum, Vector <Integer> grpIdxVec) {
			this.selIndex = selIndex;
			this.pad = pad;
			this.isChkGene = isChkNum;
			this.grpIdxVec = grpIdxVec;
			
			if (numRows==2) showSyntenyfor3();
			else 			showSynteny();
		}
		/***************************************/
		private void showSynteny() {
		try{
			TmpRowData rd = new TmpRowData(tablePanel);
			if (!rd.loadRow(tablePanel.theTable.getSelectedRows()[0])) return;
						
			int track1Start=0, track2Start=0, track2End=0, track1End=0;
			int [] coords;
			HfilterData hd = new HfilterData (); 
					
			if (selIndex==TableMainPanel.showSET) {
				if (rd.collinearN==0) {
					Utilities.showWarning("The selected row does not belong to a collinear set.");
					return;
				}
				coords = loadCollinearCoords(rd.collinearN, rd.chrIdx[0], rd.chrIdx[1]);
				hd.setForQuery(0, true, false);  // block, set, region; CAS571 block was T
			}
			else if (selIndex==TableMainPanel.showBLOCK) {
				if (rd.blockN==0) {
					Utilities.showWarning("The selected row does not belong to a synteny block.");
					return;
				}
				coords = loadBlockCoords(rd.blockN, rd.chrIdx[0], rd.chrIdx[1]);
				hd.setForQuery(rd.blockN, false, false);  // block, set, region; CAS573 show block only
			}
			else if (selIndex==TableMainPanel.showGRP) { 
				if (rd.groupN==0) {
					Utilities.showWarning("The selected row does not belong to a group.");
					return;
				}
				coords = rd.loadGroup(grpIdxVec); 	 // assigns hits to grpIdxVec
				hd.setForQuery(0, false, true);  // block, set, region
			}
			else {
				coords = new int [4];
				coords[0] = rd.start[0]; coords[1] = rd.end[0];
				coords[2] = rd.start[1]; coords[3] = rd.end[1];
				
				hd.setForQuery(0, false, true);  // block, set, region
			}
			track1Start = coords[0] - pad; if (track1Start<0) track1Start=0;
			track1End   = coords[1] + pad; 
			track2Start = coords[2] - pad; if (track2Start<0) track2Start=0;
			track2End   = coords[3] + pad;
			
			tablePanel.hitNum1 = rd.hitnum; 			
			
			int p1Idx = rd.spIdx[0];
			int p2Idx = rd.spIdx[1];

			int grp1Idx = rd.chrIdx[0];
			int grp2Idx = rd.chrIdx[1];
			
			// create new drawing panel; 				
			SyMAP2d symap = new SyMAP2d(tablePanel.queryFrame.getDBC(), tablePanel);
			symap.getDrawingPanel().setTracks(2); 		
			symap.getDrawingPanel().setHitFilter(1,hd); 
			
			Sequence s1 = symap.getDrawingPanel().setSequenceTrack(1, p2Idx, grp2Idx, Color.CYAN);
			Sequence s2 = symap.getDrawingPanel().setSequenceTrack(2, p1Idx, grp1Idx, Color.GREEN);
			if (isChkGene){s1.setGeneNum(); s2.setGeneNum();} 			
			else          {s1.setAnnotation(); s2.setAnnotation();} 
			
			symap.getDrawingPanel().setTrackEnds(1, track2Start, track2End);
			symap.getDrawingPanel().setTrackEnds(2, track1Start, track1End);
			symap.getFrame().showX();
		} 
		catch(Exception e) {ErrorReport.print(e, "Create 2D Synteny");}
	    }
		/***********************************************************/
	    private void showSyntenyfor3() {// this is partially redundant with above, but easier...
	 		try{
	 			int [] col = tablePanel.getSharedRef(false); 
	 			if (col == null) return;
	 			
	 			TmpRowData [] rd = new TmpRowData [2];
	 			
	 			rd[0] = new TmpRowData(tablePanel);
	 			if (!rd[0].loadRow(tablePanel.theTable.getSelectedRows()[0])) {Globals.tprt("No load 0");return;}
	 			rd[1] = new TmpRowData(tablePanel);
	 			if (!rd[1].loadRow(tablePanel.theTable.getSelectedRows()[1])) {Globals.tprt("No load 1");return;}
	 				
	 			int r0t1 = col[0], r0t2 = col[1],  
	 				               r1t2 = col[2], r1t3 = col[3];
	 			
	 			int sL=0, eL=1, sR=2, eR=3;
	 			int [] coordsRow0 = new int [4]; 
	 			int [] coordsRow1 = new int [4];
	 			HfilterData hd0 = new HfilterData (); 
	 			HfilterData hd1 = new HfilterData (); 
	 			
	 			if (selIndex==TableMainPanel.showSET) {
	 				if (rd[0].collinearN==0 || rd[1].collinearN==0) {
	 					Utilities.showWarning("The selected rows do not belong to a collinear set.");
	 					return;
	 				}
	 				int [] coords0 = loadCollinearCoords(rd[0].collinearN, rd[0].chrIdx[0], rd[0].chrIdx[1]);// ret sL,eL,sR,eR
	 				int [] coords1 = loadCollinearCoords(rd[1].collinearN, rd[1].chrIdx[0], rd[1].chrIdx[1]);
	 				hd0.setForQuery(0, true, false);  // block, set, region
	 				hd1.setForQuery(0, true, false);  // block, set, region
	 				
	 				if (r0t1==0 && r0t2==1) {
	 					coordsRow0[sL] = coords0[0]; coordsRow0[eL] = coords0[1]; // row0
	 					coordsRow0[sR] = coords0[2]; coordsRow0[eR] = coords0[3];
	 				}
	 				else {
	 					coordsRow0[sL] = coords0[2]; coordsRow0[eL] = coords0[3]; 
	 					coordsRow0[sR] = coords0[0]; coordsRow0[eR] = coords0[1];
	 				}
	 				if (r1t2==0 && r1t3==1) {
	 					coordsRow1[sL] = coords1[0]; coordsRow1[eL] = coords1[1]; // row1
	 					coordsRow1[sR] = coords1[2]; coordsRow1[eR] = coords1[3];
	 				}
	 				else {
	 					coordsRow1[sL] = coords1[2]; coordsRow1[eL] = coords1[3]; 
	 					coordsRow1[sR] = coords1[0]; coordsRow1[eR] = coords1[1];
	 				}
	 			}
	 			else if (selIndex==TableMainPanel.showREGION) {
	 				coordsRow0[sL] = rd[0].start[r0t1]; coordsRow0[eL] = rd[0].end[r0t1]; // row0
	 				coordsRow0[sR] = rd[0].start[r0t2]; coordsRow0[eR] = rd[0].end[r0t2];
	 				coordsRow1[sL] = rd[1].start[r1t2]; coordsRow1[eL] = rd[1].end[r1t2]; 
	 				coordsRow1[sR] = rd[1].start[r1t3]; coordsRow1[eR] = rd[1].end[r1t3];

	 				hd0.setForQuery(0, false, true);  // block, set, region
	 				hd1.setForQuery(0, false, true);  
	 			}
	 			else {
	 				JOptionPane.showMessageDialog(null, "3-track display only works with Region or Collinear Set.", 
	 						"Warning", JOptionPane.WARNING_MESSAGE);
					return;
	 			}
	 			int [] tStart = new int [3]; int [] tEnd   = new int [3]; 
	 			int [] spIdx  = new int [3]; int [] chrIdx = new int [3];
	 		
				spIdx[0] = rd[0].spIdx[r0t1]; chrIdx[0] = rd[0].chrIdx[r0t1];
				spIdx[1] = rd[0].spIdx[r0t2]; chrIdx[1] = rd[0].chrIdx[r0t2];	// ref
				spIdx[2] = rd[1].spIdx[r1t3]; chrIdx[2] = rd[1].chrIdx[r1t3];
				
				tStart[0] = coordsRow0[sL] - pad; if (tStart[0]<0) tStart[0]=0;
	 			tEnd[0]   = coordsRow0[eL] + pad; 
	 			
	 			int startRef = (coordsRow0[sR]<coordsRow1[sL]) ? coordsRow0[sR] : coordsRow1[sL];
				int endRef   = (coordsRow0[eR]>coordsRow1[eL]) ? coordsRow0[eR] : coordsRow1[eL];
				tStart[1] = startRef - pad; if (tStart[1]<0) tStart[1]=0;	
	 			tEnd[1]   = endRef + pad; 
				
				tStart[2] = coordsRow1[sR] - pad; if (tStart[2]<0) tStart[2]=0;
	 			tEnd[2]   = coordsRow1[eR] + pad; 
	 			
	 			tablePanel.hitNum1 = rd[0].hitnum; tablePanel.hitNum2 = rd[1].hitnum;
				
	 			// create new drawing panel; 
	 			SyMAP2d symap = new SyMAP2d(tablePanel.queryFrame.getDBC(), tablePanel);
	 			symap.getDrawingPanel().setTracks(3); 
	 			symap.getDrawingPanel().setHitFilter(1,hd0); // template for Mapper HfilterData, which is already created
	 			symap.getDrawingPanel().setHitFilter(2,hd1);
	 			
	 			Sequence s1 = symap.getDrawingPanel().setSequenceTrack(1, spIdx[0], chrIdx[0], Color.CYAN);
	 			Sequence s2 = symap.getDrawingPanel().setSequenceTrack(2, spIdx[1], chrIdx[1], Color.GREEN); // ref
	 			Sequence s3 = symap.getDrawingPanel().setSequenceTrack(3, spIdx[2], chrIdx[2], Color.GREEN);
	 			if (isChkGene){s1.setGeneNum(); s2.setGeneNum();s3.setGeneNum();} 			
				else          {s1.setAnnotation(); s2.setAnnotation(); s3.setAnnotation();} 
				
	 			symap.getDrawingPanel().setTrackEnds(1, tStart[0], tEnd[0]);
	 			symap.getDrawingPanel().setTrackEnds(2, tStart[1], tEnd[1]); // ref
	 			symap.getDrawingPanel().setTrackEnds(3, tStart[2], tEnd[2]);
	 			symap.getFrame().showX();
	 		} 
	 		catch(Exception e) {ErrorReport.print(e, "Create 2D Synteny");}
	    }
	    /***********************************************************/
	    private int [] loadCollinearCoords(int set, int idx1, int idx2) {
			int [] coords = null;		
			try {
				DBconn2 dbc2 = tablePanel.queryFrame.getDBC();
			
				ResultSet rs = dbc2.executeQuery("select start1, end1, start2, end2 from pseudo_hits "
						+ "where runnum=" + set + " and grp1_idx=" + idx1 + " and grp2_idx=" + idx2);
				int start1=Integer.MAX_VALUE, end1=-1, start2=Integer.MAX_VALUE, end2=-1, d;
				while (rs.next()) {
					d = rs.getInt(1);	if (d<start1) start1 = d;
					d = rs.getInt(2);	if (d>end1)   end1 = d;
					
					d = rs.getInt(3); 	if (d<start2) start2 = d;
					d = rs.getInt(4);	if (d>end2)   end2 = d;
				}
				if (start1==Integer.MAX_VALUE) { // try flipping idx1 and idx2
					rs = dbc2.executeQuery("select start1, end1, start2, end2 from pseudo_hits "
							+ "where runnum=" + set + " and grp1_idx=" + idx2 + " and grp2_idx=" + idx1);
					while (rs.next()) {
						d = rs.getInt(1);	if (d<start2) start2 = d;
						d = rs.getInt(2);	if (d>end2)   end2 = d;
						
						d = rs.getInt(3); 	if (d<start1) start1 = d;
						d = rs.getInt(4);	if (d>end1)   end1 = d;
					}
				}
				if (start1!=Integer.MAX_VALUE) {
					int pad = 8;				
					coords = new int [4];
					coords[0]=start1-pad; if (coords[0]<0) coords[0]=0;
					coords[1]=end1+pad; 
					coords[2]=start2-pad; if (coords[3]<0) coords[0]=0;
					coords[3]=end2+pad;
				}
				rs.close();
				return coords;
			}
			catch(Exception e) {ErrorReport.print(e, "get collinear coords"); return null;}
	    }
	    /***********************************************************/
	    private int [] loadBlockCoords(int block, int idx1, int idx2) {
			int [] coords = null;
			try {
				DBconn2 dbc2 = tablePanel.queryFrame.getDBC();
				
				ResultSet rs = dbc2.executeQuery("select start1, end1, start2, end2 from blocks "
						+ " where blocknum="+block+ " and grp1_idx=" + idx1 + " and grp2_idx=" + idx2);
				if (rs.next()) {
					coords = new int [4];
					for (int i=0; i<4; i++) coords[i] = rs.getInt(i+1);
				}

				if (coords==null) { // try opposite way
					rs = dbc2.executeQuery("select start2, end2, start1, end1 from blocks "
							+ " where blocknum="+block+ " and grp1_idx=" + idx2 + " and grp2_idx=" + idx1);
					if (rs.next()) {
						coords = new int [4];
						for (int i=0; i<4; i++) coords[i] = rs.getInt(i+1);
					}
				}
				rs.close();
				return coords;
			}
			catch(Exception e) {ErrorReport.print(e, "set project annotations"); return null;}
		}
	}
	/***************************************************************************/
	private class MsaAlign  extends JDialog {
		private static final long serialVersionUID = 1L;
		
		// saves between usage, but cancel does not restore old except for nCPU
		private static int nCPU=1; // performed in MsaRun for mafft
		private static boolean 
				bMerge=true,    // performed in buildHitVec
		        bGapLess=false, // performed in loadSeq
		        bTrim=true,		// performed after align in alignRun
		        bAuto=true,		// performed in MsaRun for mafft
				bMAFFT=true;	// run mafft in MsaRun, else muscle
		
		private Vector <HitEnd> hitVec = new Vector <HitEnd> ();
		private int numRows=0;
		private long alignBase=0;
		private boolean bOkay=false;
		private boolean bSuccess=true;
		private AlignPool aPool = null;
		
		private MsaAlign() {
		try {						
			DBconn2 dbc = tablePanel.queryPanel.getDBC();
			aPool = new AlignPool(dbc);
			
			tablePanel.setPanelEnabled(false);
			createMenu();
			if (bOkay) {
				tablePanel.setMsaButton(false);
				
				buildHitVec();	if (!bSuccess) return;
				loadSeq();		if (!bSuccess) return;
				buildTableAndAlign();
			}
			tablePanel.setPanelEnabled(true);
		}
		catch(Exception e) {ErrorReport.print(e, "Show alignment");}			
		}
		private void popupHelp() {
			String msg = "";
			msg += "Merge overlap hits: this greatly reduces bases aligned by removing redundant sequence";
			msg += "\nUse gapless hits: this is good for genes with long introns (e.g. homo sapiens), "
				+  "\n                  where the subhits are separatedly by long intronic regions. ";
			msg += "\n\nSee ? for details.\n";
			util.Utilities.displayInfoMonoSpace(this, "Quick Help", msg, false);
		}
		
		/********************************************************/
		private void createMenu() {
			setModal(true);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			setTitle("MSA options");
			
			JPanel selectPanel = Jcomp.createPagePanel();
			
			ButtonGroup bg = new ButtonGroup();
			JPanel row1 = Jcomp.createRowPanel();
			JRadioButton mafft = Jcomp.createRadio("MAFFT", "Align with MAFFT (Katoh 2013 MBA:30");
			mafft.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bMAFFT = mafft.isSelected();
				}
			});
			row1.add(mafft); row1.add(Box.createHorizontalStrut(3));
			bg.add(mafft);  
			
			JCheckBox chkAuto = Jcomp.createCheckBox("Auto", "Use the --auto option, which finds best method", bAuto);
			chkAuto.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bAuto  = chkAuto.isSelected();
				}
			});
			row1.add(chkAuto); row1.add(Box.createHorizontalStrut(3));
			
			txtCPUs = new JTextField(2);
			txtCPUs.setMaximumSize(txtCPUs.getPreferredSize());
			txtCPUs.setMinimumSize(txtCPUs.getPreferredSize());
			txtCPUs.setText(nCPU+"");
			row1.add(Jcomp.createLabel("CPUs: ")); row1.add(txtCPUs);
			selectPanel.add(row1);selectPanel.add(Box.createVerticalStrut(5));
			
			JPanel row2 = Jcomp.createRowPanel();
			JRadioButton muscle = Jcomp.createRadio("MUSCLE", "Align with MUSCLE (Edgar 2004 NAR:32)");
			muscle.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bMAFFT = !muscle.isSelected();
				}
			});
			if (!Ext.isMacM4()) {// no MUSCLE for M4 (executable had hard-link and could not compile easily)
				row2.add(muscle); bg.add(muscle);
			}
			selectPanel.add(row2);	selectPanel.add(Box.createVerticalStrut(5));	
			if (bMAFFT) mafft.setSelected(true); else muscle.setSelected(true);
			
			selectPanel.add(new JSeparator());
			
			JRadioButton radMerge = Jcomp.createRadio("Merge overlapping seqs", "Merge overlapping sequences");
			radMerge.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bGapLess = false;
					bMerge = radMerge.isSelected();
				}
			});
			JRadioButton radGapLess = Jcomp.createRadio("Use gapless hits", "For cluster hits, remove gaps");
			radGapLess.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bMerge = false;
					bGapLess = radGapLess.isSelected();
				}
			});
			JRadioButton radNone = Jcomp.createRadio("None of the above", "Align all hits ends as is");
			radNone.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bMerge = bGapLess = false;
				}
			});
			ButtonGroup rg = new ButtonGroup();
			rg.add(radMerge); rg.add(radGapLess); rg.add(radNone); 
			
			if (bMerge) radMerge.setSelected(true);
			else if (bGapLess) radGapLess.setSelected(true);
			else radNone.setSelected(true);
			
			JCheckBox chkTrim = Jcomp.createCheckBox("Trim", "Trim ends that has no match", bTrim);
			chkTrim.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bTrim  = chkTrim.isSelected();
				}
			});
			selectPanel.add(Box.createVerticalStrut(5)); 	
			selectPanel.add(radMerge); 		selectPanel.add(Box.createVerticalStrut(3));	
			selectPanel.add(radGapLess); 	selectPanel.add(Box.createVerticalStrut(3));				
			selectPanel.add(radNone); 		selectPanel.add(Box.createVerticalStrut(5));
			selectPanel.add(new JSeparator());
			selectPanel.add(chkTrim); 		selectPanel.add(Box.createVerticalStrut(5));	
			selectPanel.add(new JSeparator());
			
			JPanel row = Jcomp.createRowPanel(); row.add(Box.createHorizontalStrut(5));
			JButton btnOK = Jcomp.createButton("Align", "Run MSA align"); 	
	    	btnOK.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bOkay=true;
					setVisible(false);
				}
			});
	    	row.add(Box.createHorizontalStrut(5));
	    	row.add(btnOK); 				row.add(Box.createHorizontalStrut(5));
	    	
	    	JButton btnCancel = Jcomp.createButton(Jcomp.cancel, "Cancel align"); 
	    	btnCancel.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					bOkay=false;
					setVisible(false);
				}
			});
	    	row.add(btnCancel); 			row.add(Box.createHorizontalStrut(5));
	    	
	    	JButton btnInfo = Jcomp.createIconButton("/images/info.png", "Quick Help Popup");
			btnInfo.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					popupHelp();
				}
			});
			row.add(btnInfo); 			row.add(Box.createHorizontalStrut(5));
			
	    	selectPanel.add(row);
	    	selectPanel.add(Box.createVerticalStrut(5));
	    	
	    	selectPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
	    	add(selectPanel);
	    	
			pack();
			this.setResizable(false);
			setLocationRelativeTo(null); 
			setVisible(true);
		}
		/***************************************************************
		 *  Create hitVec for alignment; perform bMerge  
		 ***************************************************************/
		private void buildHitVec() {
		try {
			if (bMAFFT) {
				if (Ext.getMafftCmd()==null) {bSuccess=false; return;}
			}
			else {
				if (Ext.getMafftCmd()==null) {bSuccess=false; return;}
			}
			alignBase=0;
			int [] selRows = tablePanel.theTable.getSelectedRows();
			numRows = selRows.length;
			HashMap<String, HitEnd> tagMap = new HashMap<String, HitEnd> (); 
			
			TmpRowData rd = new TmpRowData(tablePanel);
			if (!bMerge) {
				for(int x=0; x<numRows; x++) {
					if (!rd.loadRow(selRows[x])) {bSuccess=false; return;} // Load row data
					String [] hitSt = rd.hitSt.split("/");
					String gapLess =  (!bGapLess) ? "" : rd.hitnum + ";" + rd.chrIdx[0] + ";" + rd.spIdx[0]+ ";" + rd.chrIdx[1]+ ";" + rd.spIdx[1];
					for (int i=0; i<2; i++) {
						alignBase += (rd.end[i]-rd.start[i])+1;
						HitEnd h = new HitEnd(i, rd.hitnum, rd.start[i], rd.end[i], rd.chrIdx[i], 
								rd.spAbbr[i], rd.chrNum[i], rd.geneTag[i],hitSt[i], gapLess);
						hitVec.add(h);
					}
				}
				return;
			}
			/************* Merge *********************/
			for(int row=0; row<numRows; row++) {
				if (!rd.loadRow(selRows[row])) {bSuccess=false; return;} // Load row data
				String [] hitSt = rd.hitSt.split("/");
				
				for (int i=0; i<2; i++) {
					boolean merged=false;
					String key=null;
				
					if (!rd.geneTag[i].endsWith(Q.dash)) {// this will be N.N.[b] or N.- 
						key = rd.spIdx[i] + "." + rd.geneTag[i] + hitSt[i];
						merged = tagMap.containsKey(key);
						
						if (merged) { // update hit ends
							HitEnd ht = tagMap.get(key);
							ht.updateCoords(rd.start[i], rd.end[i], rd.hitnum);
						}
					}
					else {								// merge on coordinates
						for (HitEnd ht : hitVec) {
							if (ht.bIsGene) continue;
							
							if (ht.spAbbr.equals(rd.spAbbr[i]) && ht.chrIdx==rd.chrIdx[i] && ht.strand.equals(hitSt[i])) {
								int olap = Math.min(rd.end[i],ht.end) - Math.max(rd.start[i],ht.start) + 1;
								if (olap>20) {
									ht.updateCoords(rd.start[i], rd.end[i], rd.hitnum);
									merged=true;
									break;
								}
							}
						}
					}
					if (!merged) {
						String gapLess = (!bGapLess) ? "" : rd.hitnum + ";" + rd.chrIdx[0] + ";" + rd.spIdx[0]+ ";" + rd.chrIdx[1]+ ";" + rd.spIdx[1];
						HitEnd h = new HitEnd(i, rd.hitnum, rd.start[i], rd.end[i], rd.chrIdx[i], rd.spAbbr[i], 
								rd.chrNum[i], rd.geneTag[i], hitSt[i], gapLess);
						hitVec.add(h);
						if (key!=null) tagMap.put(key, h);
					}	
				}
			} // end merge
			for (HitEnd ht : hitVec) alignBase += (ht.end-ht.start)+1; // need to wait until after merges
		}

		catch (Exception e) {ErrorReport.print(e, "Building gene vec for muscle"); bSuccess=false;}
		}
		/*****************************************************
		 * Load full sequence or gapless
		 */
		private void loadSeq() {
		try {
			for (HitEnd ht : hitVec) {
				ht.theSeq = aPool.loadSeq(ht.pos, ht.start + ":" + ht.end, ht.chrIdx, ht.gapLess); 	// needed for gapLess
				if (ht.strand.equals("-")) ht.theSeq = SeqData.revComplement(ht.theSeq); 
			}
		}
		catch (Exception e) {ErrorReport.print(e, "Load sequences"); bSuccess=false;}
		}
		/********************************************************
		 * Create the vectors of names and sequence, lower table, and calls tdp.queryFrame.addAlignmentTab
		 ******************************************************/
		private void buildTableAndAlign() {
			Vector<String> theTable = new Vector<String> (); // Table at bottom giving more info
			Vector<String> theNames = new Vector<String> (); // Names go beside sequence alignment
			Vector<String> theSeqs = new Vector<String> ();  // Actual sequences
			
			String [] fields = { "" , "Gene#", "Start", "End", "St", "Length", "Hit#"};
			int [] justify =   {   1,    1,       0,          0,   0, 0,  1}; // 1 left justify
			int nRow = hitVec.size(); 
		    int nCol=  fields.length;
		    String [][] rows = new String[nRow][nCol];
		    int r=0, maxLen=0;
		    
			for (HitEnd ht : hitVec) {
				String name =  String.format("%2d. %s.%s", (r+1), ht.spAbbr, ht.chrNum);
				theNames.add(name);
				theSeqs.add(ht.theSeq);
				
				int len = ht.theSeq.length();
				maxLen = Math.max(maxLen, len);
				
				int c=0;
				rows[r][c++]=String.format("%2d. %s.%s", r+1, ht.spAbbr, ht.chrNum);
				rows[r][c++]=ht.geneTag.substring(ht.geneTag.indexOf(".")+1);
				rows[r][c++]=String.format("%,d",ht.start);
				rows[r][c++]=String.format("%,d",ht.end);
				rows[r][c++]=String.format("%s",ht.strand);
				rows[r][c++]=String.format("%,d",len);
				rows[r][c++]=ht.hitNumStr;
				r++;
			}
			
			Utilities.makeTable(theTable, nCol, nRow, fields, justify, rows);
			
			nCPU = Utilities.getInt(txtCPUs.getText());
			if (nCPU<=0) nCPU=1;
			
			String tabAdd = "#" + hitVec.get(0).hitNum + " (" + r + ")";	// left hand tab; append after MSA N:
			String pgm = (bMAFFT) ? "MAFFT" : "MUSCLE";	// MsaMainPanel reads for MUS!! 
			String progress = String.format(" Run %s in directory /%s.  Align %s (max hit %s bases)....\n", 
					pgm, Globals.alignDir, Utilities.kMText(alignBase), Utilities.kMText(maxLen));	// If progress line is too long, it blanks panel
				
			String params = pgm;
			if (bMAFFT && bAuto) params += " Auto";
			if (bMerge) 	params += " Merge";
			if (bGapLess) 	params += " GapLess";
			String msaSum = params;							// for MSA panel (trim # is added in AlignRun)
			
			String resultSum = pgm; 						// for result table
			if (bTrim) 	resultSum += " Trim";
			resultSum += "; Hit#" + hitVec.get(0).hitNum + " plus " + (r-1) + " more";
			
			/*-------------- ALIGN -------------------*/
			// msaSum must start with 'MUS' for muscle, else it will run mafft
			String [] names = 	theNames.toArray(new String[0]);
			String [] seqs = 	theSeqs.toArray(new String[0]);
			String [] tabLines = theTable.toArray(new String[0]);
			
			// Add tab and align - QueryFrame calls MsaMainPanel, which is treaded
			tablePanel.queryFrame.makeTabMSA(tablePanel, names, seqs, tabLines, 
					progress, tabAdd, resultSum, msaSum, bTrim, bAuto, nCPU);
			
			theNames.clear(); theTable.clear(); theSeqs.clear();
		}	
		/*--- HitEnd class --*/
		private class HitEnd {
			private int pos, start, end, chrIdx,  hitNum;
			private String strand;
			private String spAbbr, chrNum, geneTag;
			private boolean bIsGene=false;
			private String theSeq="";
			private String hitNumStr="";
			private String gapLess="";
			
			private HitEnd(int pos, int hitNum, int start, int end, int chrIdx, String spAbbr, String chrNum, String geneTag, String strand, String gapLess) {
				this.pos=pos; this.start=start; this.end=end; this.chrIdx=chrIdx; 
				this.spAbbr=spAbbr; this.chrNum=chrNum; 
				this.geneTag=geneTag;
				this.strand=strand;
				bIsGene = !geneTag.endsWith(Q.dash);	
				this.hitNum=hitNum;  hitNumStr = hitNum+"";
				this.gapLess = gapLess;
			}
			private void updateCoords(int start, int end, int hitNum) {
				if (this.start>start) this.start=start;
				if (this.end<end)     this.end=end;
				this.hitNumStr += "," + hitNum;
			}
			public String toString() {
				return String.format("HT: %10s %5s %5s  %,10d %,10d %,6d %s", 
						geneTag, spAbbr, chrNum, start, end, (end-start+1), hitNum);
			}
		}
		/*--- Interface graphics variables that need accessing ---*/
		private JTextField txtCPUs;
	} // End Align class
}
