package symapQuery;

import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import backend.Constants;
import database.DBconn2;
import symap.Globals;
import symap.closeup.AlignPool;
import symap.closeup.SeqData;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;

import util.ErrorReport;
import util.Jcomp;
import util.Utilities;

/**********************************************************
 * Contains the MSA align; Created CAS563
 */
public class UtilSelect {
	private TableMainPanel tdp;
	// saves between usage, but cancel does not restore old except for nCPU
	private static int nCPU=1; // performed in MsaRun for mafft
	private static boolean 
			bMerge=true,    // performed in buildHitVec
	        bGapLess=false, // performed in loadSeq
	        bTrim=true,		// performed after align in alignRun
	        bAuto=true,		// performed in MsaRun for mafft
			bMAFFT=true;	// run mafft in MsaRun, else muscle
	
	protected UtilSelect(TableMainPanel tdp) {
		this.tdp = tdp;
	}
	protected void msaAlign() {
		new MsaAlign();
	}
	
	/***************************************************************************/
	private class MsaAlign  extends JDialog {
		private static final long serialVersionUID = 1L;
		
		private Vector <HitEnd> hitVec = new Vector <HitEnd> ();
		private int numRows=0;
		private long alignBase=0;
		private boolean bOkay=false;
		private boolean bSuccess=true;
		private AlignPool aPool = null;
		
		private MsaAlign() {
		try {						// CAS563 removed the thread, has two more in the alignment and display
			DBconn2 dbc = tdp.queryPanel.getDBC();
			aPool = new AlignPool(dbc);
			
			tdp.setPanelEnabled(false);
			createMenu();
			if (bOkay) {
				buildHitVec();	if (!bSuccess) return;
				loadSeq();		if (!bSuccess) return;
				buildTableAndAlign();
			}
			tdp.setPanelEnabled(true);
		}
		catch(Exception e) {ErrorReport.print(e, "Show alignment");}			
		}
		private void popupInfo() {
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
			setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
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
			row2.add(muscle); bg.add(muscle);
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
					popupInfo();
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
				if (!Constants.checkFor("mafft")) {bSuccess=false; return;}
			}
			else {
				if (!Constants.checkFor("muscle")) {bSuccess=false; return;}
			}
			alignBase=0;
			int [] selRows = tdp.theTable.getSelectedRows();
			numRows = selRows.length;
			HashMap<String, HitEnd> tagMap = new HashMap<String, HitEnd> (); 
			
			TmpRowData rd = new TmpRowData(tdp);
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
				if (ht.strand.equals("-")) ht.theSeq = SeqData.revComplement(ht.theSeq); // CAS563 was not doing this
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
			
			String tab = "#" + hitVec.get(0).hitNum + " (" + r + ")";	// left hand tab
			String pgm = (bMAFFT) ? "MAFFT" : "MUSCLE";	// reads for MUS!! 
			String ab = Utilities.kMText(alignBase);
			String lb = Utilities.kMText(maxLen);
			String progress = String.format(" Running %s in directory /%s.  Aligning %s (max hit %s bases)....\n", 
					pgm, Globals.alignDir, ab, lb);				// If progress line is too long, it blanks panel
				
			String resultSum = pgm; 							// for result table
			if (bMAFFT && bAuto) resultSum += " Auto";
			if (bMerge) 	resultSum += " Merge";
			if (bGapLess) 	resultSum += " GapLess";
			String msaSum = resultSum;							// for MSA panel (trim is added in AlignRun)
			if (bTrim) 		resultSum += " Trim";
			resultSum += "; Hit#" + hitVec.get(0).hitNum + " plus " + (r-1) + " more";
			
			/*-------------- ALIGN -------------------*/
			// msaSum must start with 'MUS' for muscle, else it will run mafft
			String [] names = 	theNames.toArray(new String[0]);
			String [] seqs = 	theSeqs.toArray(new String[0]);
			String [] tabLines = theTable.toArray(new String[0]);
			
			tdp.queryFrame.addAlignTab(tdp, names, seqs, tabLines, 
					progress, tab, resultSum, msaSum, 
					bTrim, bAuto, nCPU);
			
			theNames.clear(); theTable.clear(); theSeqs.clear();
			
			tdp.setPanelEnabled(true);
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
