package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.TreeMap;
import java.util.Vector;
import java.util.HashMap;
import java.util.HashSet;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import symap.manager.Mproject;
import symap.manager.Mpair;
import util.ErrorReport;
import util.Jcomp;
import util.Popup;

/******************************************************
 * For QueryPanel: 
 *  Load DB data and creates SpeciesSelect panel for each species
 *  Do not read DB; isSelf projIdx is not real.
 *  CAS579c removed dead code, rearranged, renamed
 */
public class SpeciesPanel extends JPanel {
	private static final long serialVersionUID = -6558974015998509926L;
	private int dnameWidth = 85; // good fit for display name of 12 characters  
	
	protected static int locOnly=1, locChr=2; // setEnable Loc, setEnable loc&Chr
	private boolean isNoLoc=false;			  // do not enable Loc, even if chr selected
	
	protected SpeciesPanel(QueryFrame qFrame, QueryPanel qPanel) {
		this.qFrame = qFrame;
		this.qPanel = qPanel;
		
		spRows = new Vector<SpeciesRow> (); // Will be sorted by DisplayName
		setBackground(Color.WHITE);
		
		createSpRows(); 
		
		createSpPanel();
	}
	/**************************************************************
	 * Creates the Species Row objects and pairWhere 
	 ********************************************************/
	private void createSpRows() {
		try {
			Vector<Mproject> mProjs = qFrame.getProjects(); // Sorted by DisplayName
			boolean isSelf = qFrame.isSelf();
			
			int [] spidx = new int [mProjs.size()];
			int x=0;
			
			// Create Rows that will be in the spPanel
			for (Mproject proj : mProjs) {
				// Split the grpMap into two strings
				String chrNumStr="", chrIdxStr="";
				TreeMap <Integer, String> idChrMap = proj.getGrpIdxMap(); 
				
				for (int idx : idChrMap.keySet()) {
					if (!chrNumStr.equals("")) {chrNumStr += ","; chrIdxStr += ",";}
					
					chrNumStr += proj.getGrpNameFromIdx(idx);
					chrIdxStr += idx;
				}
				// Create row object and enter in the original order
				SpeciesRow ssp = new SpeciesRow(proj.getDisplayName(),
						proj.getIdx(), proj.getdbCat(), chrNumStr, chrIdxStr, proj.getdbAbbrev(), proj.hasGenes());
				spRows.add(ssp);
				
				spName2spIdx.put(proj.getDisplayName(), proj.getID());
				spIdx2spRow.put(proj.getID(), ssp);
				if (isSelf) selfName[x] = proj.getDisplayName();
				spidx[x++] = proj.getIdx();
				
				String [] chrIdxList = ssp.getChrIdxList();
				for (String idx : chrIdxList) chrIdx2spRow.put(Integer.parseInt(idx), ssp);
			}
			
			// Create pairWhere from all pair indices - used in all queries but Orphan
			// the order can be spidx[i]<spidx[j] or spidx[i]>spidx[j]
			String idList="";
			for (int i=0; i<spidx.length-1; i++) {
				for (int j=i+1; j<spidx.length; j++) {	
					int spidx2 = (isSelf) ? spidx[i] : spidx[j];
					Mpair mp = qFrame.getMpair(spidx[i], spidx2);
					if (mp==null) {
						Popup.showErrorMessage("No pair for IDX=" + spidx[i] + " " + spidx[j]);
						return;
					}
					int idx = mp.getPairIdx();
					if (idx>0) {
						if (idList.equals("")) idList = idx+"";
						else                   idList += "," + idx;
					}
				}
			}
			if (idList.equals("")) Popup.showErrorMessage("No synteny pairs. Attempts to Run Query will fail.");
			else if (!idList.contains(",")) pairWhere = "PH.pair_idx=" + idList + " ";
			else 							pairWhere = "PH.pair_idx IN (" + idList + ") ";
		} 
		catch(Exception e) {ErrorReport.print(e, "Species panel");}
	}
	/************************************************************
	 * Creates the Species panel
	 ******************************************************/
	private void createSpPanel() {
		if(spRows == null || spRows.size() == 0) return;
		
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		add(Box.createVerticalStrut(5));
		/* remove labelPanel, includePanel, excludePanel which do nothing; CAS579c */
	
		// Adjust the chromosome select controls
		Dimension maxSize = new Dimension(0,0);
		for (SpeciesRow sp : spRows) {
			Dimension tempD = sp.getChromSize();
			if (tempD.width > maxSize.width) maxSize = tempD;
		}
		for (SpeciesRow sp : spRows) sp.setChromSize(maxSize);
		
		// Categories
		Vector<String> sortedCat = new Vector<String> ();
		for(int x=0; x<spRows.size(); x++) {
			String cat = spRows.get(x).getCategory();
			if(!sortedCat.contains(cat)) 
				sortedCat.add(cat);
		}
		Collections.sort(sortedCat);
		
		for(int x=0; x<sortedCat.size(); x++) {
			String catName = sortedCat.get(x);
			boolean firstOne = true;
			for (SpeciesRow spObj : spRows) {
				if(catName.equals(spObj.getCategory())) {
					if (firstOne) {
						if (sortedCat.size() > 1) add(new JLabel(catName.toUpperCase()));
						firstOne = false;
					}
					JPanel row = Jcomp.createRowPanel();
					spObj.setChromSize(maxSize);
					row.add(spObj);
					
					add(row);
					add(Box.createVerticalStrut(1)); 
				}
			}
		}
		setAlignmentX(Component.LEFT_ALIGNMENT);
		revalidate();
	}
	
	/****************************************************************
	 *******************************************************/
	protected int getNumSpecies() 		{return spRows.size();}
	
	protected String getPairIdxWhere() 	{return pairWhere;} // made in createRowsFromProj
	
	protected HashMap <String, Integer>  getSpName2spIdx() {return spName2spIdx;} // DBdata make keys
	
	// QueryPanel: clear
	protected void setClear() {
		for (SpeciesRow p : spRows) p.setClear();
		isNoLoc = false; 
	}
	/***************************************************************
	 * XXX QueryPanel: when a check box has changed, this is called to activate/deactivate species
	 * locOnly: Exact: Block, Collinear, or Hit check: no ChkBox, Chr, no Loc
	 * locChr:  Gene, Anno and Single                     ChkBox, Chr, no Loc
	 * see two SpeciesRow ActionListener
	 */
	protected void setChkEnable(boolean isChk, int type, boolean isSelf) { // QueryPanel
		isNoLoc = isChk;			// even is chr changes, do not enable loc
		
		for (SpeciesRow p : spRows) {
			if (type==locOnly) 	p.setLocEnable(isChk);     
			else 				p.setLocChrEnable(isChk);  
		}
		if (isSelf && type==locChr && isChk) {
			SpeciesRow p = spRows.get(1);
			p.chkSpActive.setSelected(false); // do not let it change
			p.chkSpActive.setEnabled(false); 
			p.lblChrom.setEnabled(false);  p.cmbChroms.setEnabled(false);
		}
	}
	/********** Selections ********************/
	// Return all valid chromosomes (if all, then null)
	protected HashSet <Integer> getGrpIdxSet() {// used in DBdata to filter out non-valid chromosomes
		HashSet <Integer> grpSet = new HashSet <Integer> ();
		for (SpeciesRow sp : spRows) {
			HashSet <Integer> rowSet = sp.getGrpIdx();	// checks for SpIgnore
			for (int i : rowSet) grpSet.add(i);
		}
		if (grpSet.size()==0) return null;
		return grpSet; 
	}
	protected String getGrpIdxStr() { // same as above, but return list
		String list=null;
		for (SpeciesRow sp : spRows) {
			HashSet <Integer> rowSet = sp.getGrpIdx();
			for (int i : rowSet) {
				if (list==null) list = i+"";
				else list += ","+i;
			}
		}
		return list;
	}
	// Return all selected chromosomes 
	protected HashSet<Integer> getSelectGrpIdxSet() {// runMultiGene
		HashSet <Integer> idxSet = new HashSet <Integer> ();
		for (SpeciesRow sp : spRows) {
			int index = sp.getSelChrIdx();  //ignore is checked in SpeciesRow
			if(index > 0) idxSet.add(index);
		}
		return idxSet;
	}
	protected boolean hasSpSelectChr() { // has a selected chromosome
		for (SpeciesRow sp : spRows) {
			int index = sp.getSelChrIdx();
			if (index>0) return true;
		}
		return false;
	}
	protected boolean hasSpAllIgnoreRows() { // if fail, all rows are Ignore
		for (SpeciesRow sp : spRows) 
			if (!sp.isSpIgnore()) return true;
		return false;
	}
	protected boolean hasSpIgnoreRow() {// if fail, no row is disabled
		for (SpeciesRow sp : spRows) 
			if (sp.isSpIgnore()) return true;
		return false;
	}
	
	/***** Specific for a species row *****/
	protected int getSpIdxFromSpName(String name) {
		if (spName2spIdx.containsKey(name))
			 return spName2spIdx.get(name);
		else return -1;
	}
	protected String getSpNameFromSpIdx(int x) {	
		SpeciesRow p = spIdx2spRow.get(x);
		return p.getSpName();
	}
	protected int getSpIdxFromChrIdx(int x) {	
		SpeciesRow p = chrIdx2spRow.get(x);
		return p.getSpIdx();
	}
	protected String getSpNameFromChrIdx(int x) {	
		SpeciesRow p = chrIdx2spRow.get(x);
		return p.getSpName();
	}
	protected String getChrNumFromChrIdx(int x) {
		SpeciesRow p = chrIdx2spRow.get(x);
		
		String [] num = p.getChrNumList();
		String [] idx = p.getChrIdxList();
		String xx = String.valueOf(x);
		
		for (int i=0; i<num.length; i++) {
			if (idx[i].equals(xx)) return num[i];
		}
		return "0";
	}
	protected int getChrIdxFromChrNumSpIdx(String chrNum, int spIdx) {
		for (SpeciesRow sp : spRows) {
			if (spIdx == sp.spIdx) {
				for (int i=0; i<sp.chrNumList.length; i++) {
					if (sp.chrNumList[i].equals(chrNum)) {
						return Integer.parseInt(sp.chrIdxList[i]);
					}
				}
			}
		}
		return 0;
	}
	
	// p is the index into the array, which is sorted by DisplayName
	protected String getSelfName(int p)		{return selfName[p];}
	
	protected boolean isSpIgnore(int p) 	{return spRows.get(p).isSpIgnore();} 
	protected int    getSpIdx(int p)		{return spRows.get(p).getSpIdx();}
	protected String getSpName(int p) 		{return spRows.get(p).getSpName();}
	protected String getSpAbbr(int p)		{return spRows.get(p).getSpAbbr();}
		
	protected String [] getChrIdxList(int p){return spRows.get(p).getChrIdxList();}
	protected int getSelChrIdx(int p)		{return spRows.get(p).getSelChrIdx();}
	protected String getSelChrNum(int p) 	{return spRows.get(p).getSelChrNum();}
	
	protected String getChrStart(int p) 	{return spRows.get(p).getStartFullNum();}
	protected String getChrStop(int p) 		{return spRows.get(p).getStopFullNum();}
	protected boolean bHasGenes(int p)		{return spRows.get(p).hasGenes;}
	
	// For summary
	protected String getStartkb(int panel) 	{return spRows.get(panel).getStartkb();}
	protected String getStopkb(int panel) 	{return spRows.get(panel).getStopkb();}
	
	/**************************************************************
	 * XXX Row panel per species
	 */
	private class SpeciesRow extends JPanel {
		private static final long serialVersionUID = 2963964322257904265L;

		protected SpeciesRow(String spDispName, int spIdx, String strCategory, 
				String chrNumStr, String chrIdxStr, String spAbbr, boolean hasGenes) {
			this.spIdx = spIdx;					// projects.idx
			this.strCategory = strCategory;
			this.spAbbr = spAbbr; 				// for UtilReport
			this.hasGenes = hasGenes;				// for UtilReport; CAS579c
			
			chrIdxList = chrIdxStr.split(",");	// xgroups.idx 
			chrNumList = chrNumStr.split(",");	// xgroups.name
			
			chkSpActive = Jcomp.createCheckBox("", "For Annotation, Single, Gene#: only search checked species", true);
			chkSpActive.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					boolean b = chkSpActive.isSelected();
					lblChrom.setEnabled(b);  
					cmbChroms.setEnabled(b);
				}
			});
			
			lblDisplayName = Jcomp.createLabel(spDispName);	// projects.displayName
			dnameWidth = Math.max(lblDisplayName.getWidth(), dnameWidth); 
			
			lblChrom =   Jcomp.createLabel("Chr: ");
			cmbChroms = new  JComboBox <String> ();
			cmbChroms.addItem("All");
			for(int x=0; x<chrNumList.length; x++)
				cmbChroms.addItem(chrNumList[x]);
			
			cmbChroms.setBackground(Color.WHITE);
			cmbChroms.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					if (!isNoLoc) { 
						boolean bRange = !cmbChroms.getSelectedItem().equals("All");
						txtStart.setEnabled(bRange); txtStop.setEnabled(bRange);
						cmbScale.setEnabled(bRange);
						lblStart.setEnabled(bRange); lblStop.setEnabled(bRange);
					}
				}
			});
			
			lblStart = Jcomp.createLabel("From", "Start coordinate", false);
			txtStart = Jcomp.createTextField("", "Start coordinate", 10, false);
			
			lblStop = Jcomp.createLabel("To", "End coordinate", false);
			txtStop = Jcomp.createTextField("", "End coordinate", 10, false);
			
			cmbScale = new JComboBox <String> ();	cmbScale.setBackground(Color.WHITE);
			cmbScale.addItem("bp"); cmbScale.addItem("kb"); cmbScale.addItem("mb");
			cmbScale.setSelectedIndex(1);
			cmbScale.setEnabled(false);
		
			spRowPanel = Jcomp.createRowPanel(); 
			spRowPanel.add(chkSpActive);
			spRowPanel.add(lblDisplayName);
			
			Dimension d = lblDisplayName.getPreferredSize();
			d.width = Math.max(d.width, dnameWidth);
			lblDisplayName.setPreferredSize(d); lblDisplayName.setMinimumSize(d);
			
			spRowPanel.add(Box.createHorizontalStrut(1)); // make sure name does not run into chr
			spRowPanel.add(lblChrom); spRowPanel.add(cmbChroms);
			spRowPanel.add(Box.createHorizontalStrut(5));
			
			spRowPanel.add(lblStart); spRowPanel.add(txtStart);
			spRowPanel.add(lblStop);  spRowPanel.add(txtStop); spRowPanel.add(cmbScale);
			
			setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setBackground(Color.WHITE);
			
			add(spRowPanel);
			add(Box.createVerticalStrut(5));
		}
		
		protected void setClear() {
			cmbChroms.setSelectedIndex(0); txtStart.setText(""); txtStop.setText(""); 
			
			lblChrom.setEnabled(true);  cmbChroms.setEnabled(true); 
			lblStart.setEnabled(false); lblStop.setEnabled(false);
			txtStart.setEnabled(false); txtStop.setEnabled(false);  cmbScale.setEnabled(false);
			
			chkSpActive.setSelected(true); chkSpActive.setEnabled(false);
		}
		// Called when another filter using this is checked or unchecked
		private boolean isSpIgnore()  {
			return chkSpActive.isEnabled() && !chkSpActive.isSelected();
		}
				
		// Called when block, hit, collinear checked or unchecked
		private void setLocEnable(boolean isTurnOff) {
			lblChrom.setEnabled(true);  cmbChroms.setEnabled(true);
			if (isTurnOff) {
				lblStart.setEnabled(false); lblStop.setEnabled(false);
				txtStart.setEnabled(false); txtStop.setEnabled(false); cmbScale.setEnabled(false);
			}
			else {
				boolean isChr = !cmbChroms.getSelectedItem().equals("All");
				
				lblStart.setEnabled(isChr); lblStop.setEnabled(isChr);
				txtStart.setEnabled(isChr); txtStop.setEnabled(isChr); cmbScale.setEnabled(isChr);	
			}
		}
		private void setLocChrEnable(boolean isChk) {// CAS579c this keep rechecking box with chkSpActive.setSelected(true);
			setLocEnable(isChk);
			if (isChk) {
				chkSpActive.setEnabled(true); 
				if (!chkSpActive.isSelected()) { // must be checked for these to be enabled
					lblChrom.setEnabled(false);  cmbChroms.setEnabled(false);
				}
			}
			else {
				chkSpActive.setEnabled(false); 
			}
		}	
		private String getStartFullNum() { // return null (error), 0 (none), number
			if (!txtStart.isEnabled()) return "0"; // CAS579c
			String text = txtStart.getText().trim();
			if (text.contains(",")) text = text.replace(",",""); // allow commas in input
			if (text.equals("") || text.equals("0")) return "0";
			
			try {
				int temp = Integer.parseInt(text); 
				if(temp < 0) {
					qPanel.showWarnMsg("Invalid From (start) coordinate '" + text + "'");
					return null;
				}
				if (temp == 0) return "0";
				return temp + getScaleDigits();
			} 
			catch(NumberFormatException e) {
				qPanel.showWarnMsg("Invalid From (start) coordinate '" + text + "'");
				return null;
			}
		}
		private String getStopFullNum() {// return null (error), 0 (none), number
			if (!txtStop.isEnabled()) return "0"; // CAS579c
			String etext = txtStop.getText().trim();
			if (etext.contains(",")) etext = etext.replace(",",""); // allow commas in input
			if (etext.equals("") || etext.equals("0")) return "0";
			
			try {
				int end = Integer.parseInt(etext);
				if (end <= 0) {
					qPanel.showWarnMsg("Invalid To (end) coordinate '" + etext + "'");
					return null;
				}
				if (end == 0) return "0";
			
				String stext = getStartFullNum(); // check not >= end
				if (!stext.equals("0")) {
					int start = Integer.parseInt(stext);
					if (start>=end) {
						qPanel.showWarnMsg("Invalid From (start) '" + stext + "' > To (end) '" + etext + "'");
						return null;
					}
				}
				return end + getScaleDigits();
			} 
			catch(NumberFormatException e) {
				Popup.showWarningMessage("Invalid To (end) coordinate '" + etext + "'");
				return null;
			}
		}
		private String getScaleDigits() {
			if(cmbScale.getSelectedIndex() == 1) return "000";
			if(cmbScale.getSelectedIndex() == 2) return "000000";
			return "";
		}
		
		// for filter summary string
		private String getStartkb() {
			if (!txtStart.isEnabled()) return ""; // CAS579c
			String num = txtStart.getText().trim();
			if (num.equals("") || num.equals("0")) return "";
			if(cmbScale.getSelectedIndex() == 1) return num + "kb";
			if(cmbScale.getSelectedIndex() == 2) return num + "mb";
			return num + "bp";
		}
		private String getStopkb() {
			if (!txtStop.isEnabled()) return ""; // CAS579c
			String num = txtStop.getText().trim();
			if (num.equals("") || num.equals("0")) return "";
			if(cmbScale.getSelectedIndex() == 1) return num + "kb";
			if(cmbScale.getSelectedIndex() == 2) return num + "mb";
			return num + "bp";
		}
		private String getSpAbbr() 	  {return spAbbr;}
		private String getSpName() 	  {return lblDisplayName.getText();}
		private String getSelChrNum() {return (String)cmbChroms.getSelectedItem();}
		
		private int getSelChrIdx() { // Selected chromosome idx
			if (isSpIgnore()) return -1; 
			
			String chr = (String)cmbChroms.getSelectedItem();
			if (chr.equals("All")) return -1;
			
			for (int i=0; i<chrNumList.length; i++) 
				if (chrNumList[i].equals(chr)) return Integer.parseInt(chrIdxList[i]);
			
			symap.Globals.eprt("no " + chr);
			return -1;
		}
		private HashSet <Integer> getGrpIdx() { // All chromosomes that will be queried
			HashSet <Integer> grpIdx = new HashSet <Integer> ();
			
			if (!cmbChroms.isEnabled()) return grpIdx; // none can be selected
			if (isSpIgnore()) return grpIdx;
			
			int idx = getSelChrIdx();
			if (idx!=-1) {							   // only one can be selected
				grpIdx.add(idx);
				return grpIdx;				
			}
			for (int i=0; i<chrNumList.length; i++) { // get all
				grpIdx.add(Integer.parseInt(chrIdxList[i]));
			}
			return grpIdx;
		}
		private String getCategory() 		{return strCategory;}
		private String [] getChrIdxList() 	{return chrIdxList;}
		private String [] getChrNumList() 	{return chrNumList;}
		private int getSpIdx() 				{return spIdx;}
		
		private void setChromSize(Dimension d) {
			cmbChroms.setPreferredSize(d);
			cmbChroms.setMaximumSize(d);
			cmbChroms.setMinimumSize(d);
		}
		private Dimension getChromSize() { return cmbChroms.getPreferredSize(); }
		
		/***************************************************/
		private JPanel spRowPanel = null;
		
		private JCheckBox chkSpActive = null; // if enabled, then selectable
		private JLabel lblDisplayName = null;
		private JComboBox <String> cmbChroms = null; 
		private JLabel lblStart = null, lblStop = null, lblChrom = null;
		private JTextField txtStart = null, txtStop = null;
		private JComboBox <String> cmbScale = null; 
		
		private int spIdx=0;
		private String strCategory = "";
		private String [] chrIdxList;
		private String [] chrNumList;
		private String spAbbr = ""; 	
		private boolean hasGenes=true;
	} // End species row panel
	/***************************************************/
	private QueryFrame qFrame = null;
	private QueryPanel qPanel = null;
	
	private String [] selfName = {"",""}; // isSelf shared same idx; this is for Summary
	private Vector <SpeciesRow> spRows = null;
	private HashMap <Integer, SpeciesRow> chrIdx2spRow = new HashMap <Integer, SpeciesRow> ();
	private HashMap <Integer, SpeciesRow> spIdx2spRow = new HashMap <Integer, SpeciesRow> ();
	private HashMap <String, Integer> spName2spIdx = new HashMap <String, Integer> ();
	private String pairWhere="";
} 
