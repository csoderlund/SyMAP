package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Vector;
import java.util.HashMap;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import database.DBconn2;
import symap.manager.Mproject;
import util.ErrorReport;
import util.Utilities;

/******************************************************
 * For QueryPanel: 
 *  Load DB data and creates SpeciesSelect panel for each species
 * 
 * CAS504 extend it to provide the project and group MYSQL indices; CAS556 add spAbbr
 */
public class SpeciesSelectPanel extends JPanel {
	private static final long serialVersionUID = -6558974015998509926L;
	private static final int REFERENCE_SELECT_WIDTH = 60;
	private static final int SPECIES_NAME_WIDTH = 150;

	public SpeciesSelectPanel(SyMAPQueryFrame parentFrame, QueryPanel qPanel) {
		theParentFrame = parentFrame;
		spPanels = new Vector<SpeciesSelect> (); // Sorted by DisplayName
		setBackground(Color.WHITE);
		
		loadPanelsFromDB(); 
		
		refreshAllPanels();
	}
	protected void clear() {
		bIsGene=false;
		for (SpeciesSelect p : spPanels) p.clear();
	}
	
	protected int getNumSpecies() 				{return spPanels.size();}
	// i is the index into the array, which is sorted by DisplayName
	protected int getSpIdx(int i)			{return spPanels.get(i).getSpIdx();}
	protected String getSpName(int i) 		{return spPanels.get(i).getSpName();}
	protected String getSpAbbr(int i)		{return spPanels.get(i).getSpAbbr();}
	
	protected HashMap <String, Integer>  getSpName2spIdx() {return spName2spIdx;}
	
	protected String getSpNameFromSpIdx(int x) {	
		SpeciesSelect p = spIdx2panel.get(x);
		return p.getSpName();
	}
	protected int getSpIdxFromSpName(String name) {
		if (spName2spIdx.containsKey(name))
			 return spName2spIdx.get(name);
		else return -1;
	}
	protected int getSpIdxFromChrIdx(int x) {	
		SpeciesSelect p = chrIdx2panel.get(x);
		return p.getSpIdx();
	}
	protected String getSpNameFromChrIdx(int x) {	
		SpeciesSelect p = chrIdx2panel.get(x);
		return p.getSpName();
	}
	
	protected String getChrNameFromChrIdx(int x) {
		SpeciesSelect p = chrIdx2panel.get(x);
		
		String [] num = p.getChrNumList();
		String [] idx = p.getChrIdxList();
		String xx = String.valueOf(x);
		for (int i=0; i<num.length; i++) {
			if (idx[i].equals(xx)) return num[i];
		}
		return "0";
	}
	protected int getChrIdxFromChrNumSpIdx(String chrNum, int spIdx) {
		for (SpeciesSelect sp : spPanels) {
			if (spIdx== sp.spIdx) {
				for (int i=0; i<sp.chrNumList.length; i++) {
					if (sp.chrNumList[i].equals(chrNum)) {
						return Integer.parseInt(sp.chrIdxList[i]);
					}
				}
			}
		}
		return 0;
	}
	protected boolean isSpEnabled(int p) 		{return spPanels.get(p).isSpEnabled();} // CAS518 add
	protected String [] getChrIdxList(int p) 	{return spPanels.get(p).getChrIdxList();}
	protected String getChrIdxStr(int p) 		{return spPanels.get(p).getChrIdxStr();}
	protected String [] getChrNumList(int p) 	{return spPanels.get(p).getChrNumList();}
	
	protected int getSelChrIdx(int p)			{return spPanels.get(p).getSelChrIdx();}
	protected String getSelChrNum(int p) 		{return spPanels.get(p).getSelChrNum();}
	protected String getChrStart(int p) 		{return spPanels.get(p).getStartFullNum();}
	protected String getChrStop(int p) 		{return spPanels.get(p).getStopFullNum();}
	
	protected String getPairWhere() 			{ return pairWhere;}
	
	// For summary
	protected String getStartkb(int panel) 	{return spPanels.get(panel).getStartkb();}
	protected String getStopkb(int panel) 	{return spPanels.get(panel).getStopkb();}
	
	// For Single (only one project/chr/loc) and GeneNum (only one chr)
	protected void setIsGene(boolean isGene) {// CAS541 For GeneNum
		if (isGene) clear(); // if a chr is set, will allow a 2nd to be selected, which is wrong
		bIsGene=isGene;
		setAllEnabled(true); // loc disabled in sp.setEnabled
	}
	protected void setIsSingle( String spName, boolean isSingle) {// spName goes with Single Project, may be "All" or Null
		clear();
		setAllEnabled(!isSingle);
		if (!isSingle) return;
		
		if (!spName.equals("All")) {
			setAllEnabled(false);
			setSpEnabled(spName);
		}
	}
	private void setAllEnabled(boolean b) {
		for (SpeciesSelect p : spPanels) p.setEnabled(b);
	}
	private void setSpEnabled(String spName) { // CAS518 select chr for single
		for (SpeciesSelect p : spPanels) 
			if (spName.contentEquals(p.getSpName())) {
				p.setEnabled(true);
				return;
			}
		System.err.println("Could not find " + spName);
	}
	private void refreshAllPanels() {
		if(spPanels == null || spPanels.size() == 0) return;
		removeAll();
		
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		add(Box.createVerticalStrut(5));
		JPanel labelPanel = new JPanel();
		labelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.LINE_AXIS));
		labelPanel.setBackground(Color.WHITE);
		
		JPanel includePanel = new JPanel();
		includePanel.setLayout(new BoxLayout(includePanel, BoxLayout.LINE_AXIS));
		includePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		includePanel.setBackground(Color.WHITE);
		
		JPanel excludePanel = new JPanel();
		excludePanel.setLayout(new BoxLayout(excludePanel, BoxLayout.LINE_AXIS));
		excludePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		excludePanel.setBackground(Color.WHITE);
			
		Dimension dim = includePanel.getPreferredSize();
		dim.width = REFERENCE_SELECT_WIDTH;
		includePanel.setMinimumSize(dim);
		includePanel.setMaximumSize(dim);
		
		dim = excludePanel.getPreferredSize();
		dim.width = REFERENCE_SELECT_WIDTH;
		excludePanel.setMinimumSize(dim);
		excludePanel.setMaximumSize(dim);
		
		labelPanel.add(includePanel);	labelPanel.add(Box.createHorizontalStrut(5));
		labelPanel.add(excludePanel);
		labelPanel.setMaximumSize(labelPanel.getPreferredSize());
		
		add(labelPanel);
		
		//Adjust the chromosome select controls
		Iterator<SpeciesSelect> iter = spPanels.iterator();
		Dimension maxSize = new Dimension(0,0);
		while(iter.hasNext()) {
			Dimension tempD = iter.next().getChromSize();
			if(tempD.width > maxSize.width)
				maxSize = tempD;
		}

		for(int x=0; x<spPanels.size(); x++) {
			SpeciesSelect temp = spPanels.get(x);
			temp.setChromSize(maxSize);
			spPanels.set(x, temp);
		}

		Vector<String> sortedCat = new Vector<String> ();
		for(int x=0; x<spPanels.size(); x++) {
			String cat = spPanels.get(x).getCategory();
			if(!sortedCat.contains(cat)) 
				sortedCat.add(cat);
		}
		Collections.sort(sortedCat);
		
		for(int x=0; x<sortedCat.size(); x++) {
			String catName = sortedCat.get(x);
			boolean firstOne = true;
			iter = spPanels.iterator();
			while(iter.hasNext()) {
				SpeciesSelect temp = iter.next();
	
				if(catName.equals(temp.getCategory())) {
					if(firstOne) {
						if(sortedCat.size() > 1) add(new JLabel(catName.toUpperCase()));
						firstOne = false;
					}
					JPanel row = new JPanel();
					row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
					row.setAlignmentX(Component.LEFT_ALIGNMENT);
					row.setBackground(Color.WHITE);
					
					temp.setChromSize(maxSize);
					row.add(temp);
					
					add(row);
					add(Box.createVerticalStrut(3)); // CAS514  
				}
			}
		}
		setAlignmentX(Component.LEFT_ALIGNMENT);
		revalidate();
	}
	/**************************************************************/
	private void loadPanelsFromDB() {
		try {
			Vector<Mproject> theProjects = theParentFrame.getProjects(); // Sorted by DisplayName
			int [] spidx = new int [theProjects.size()];
			int x=0;
			
			DBconn2 dbc2 = theParentFrame.getDBC();
			
			for (Mproject proj : theProjects) {
				String chrNumStr="", chrIdxStr="";
				
				String strQ = "SELECT xgroups.name, xgroups.idx FROM xgroups " +
							"JOIN projects ON xgroups.proj_idx = projects.idx " +
							"WHERE projects.name = '" + proj.getDBName() + "' " +
							"ORDER BY xgroups.sort_order ASC";
				ResultSet rs = dbc2.executeQuery(strQ);
				while (rs.next()) {
					if (!chrNumStr.equals("")) {
						chrNumStr += ",";
						chrIdxStr += ",";
					}
					chrNumStr += rs.getString(1);
					chrIdxStr += rs.getString(2);
				}
				rs.close();
				
				SpeciesSelect ssp = new SpeciesSelect(this, proj.getDisplayName(),
						proj.getID(), proj.getdbCat(), chrNumStr, chrIdxStr, proj.getdbAbbrev());
				
				spPanels.add(ssp);
				spName2spIdx.put(proj.getDisplayName(), proj.getID());
				spIdx2panel.put(proj.getID(), ssp);
				spidx[x++] = proj.getID();
				
				String [] chrIdxList = ssp.getChrIdxList();
				for (String idx : chrIdxList) chrIdx2panel.put(Integer.parseInt(idx), ssp);
			}
			
			// Get pair indices - this isn't used in the panels, but is used to all queries but Orphan
			// the order can be spidx[i]<spidx[j] or spidx[i]>spidx[j]
			String idList="";
			ResultSet rs=null;
			for (int i=0; i<spidx.length-1; i++) {
				for (int j=i+1; j<spidx.length; j++) {	
					rs = dbc2.executeQuery("select idx from pairs " +
								"where proj1_idx=" + spidx[i] + " and proj2_idx=" + spidx[j]);
					
					if (rs.next()) {
						if (idList.equals("")) idList = rs.getInt(1)+"";
						else idList += "," + rs.getInt(1);
					}
					else {
						rs = dbc2.executeQuery("select idx from pairs " +
								"where proj1_idx=" + spidx[j] + " and proj2_idx=" + spidx[i]);
						if (rs.next()) {
							if (idList.equals("")) idList = rs.getInt(1)+"";
							else idList += "," + rs.getInt(1);
						}
					}
				}
			}
			if (rs!=null) rs.close();
			
			if (idList.equals("")) 		{
				pairWhere="";
				Utilities.showErrorMessage("No synteny pairs. Attempts to Run Query will fail.");
			}
			else if (!idList.contains(",")) pairWhere = "PH.pair_idx=" + idList + " ";
			else 							pairWhere = "PH.pair_idx IN (" + idList + ") ";
		} 
		catch(Exception e) {ErrorReport.print(e, "Species panel");}
	}
		
	/**************************************************************
	 * XXX Row panel per species
	 */
	private class SpeciesSelect extends JPanel {
		private static final long serialVersionUID = 2963964322257904265L;

		protected SpeciesSelect(SpeciesSelectPanel parent, 
				String spName, int spIdx, String strCategory, String chrNumStr, String chrIdxStr,
				String spAbbr) {
			this.theParent = parent;
			this.spIdx = spIdx;	// projects.idx
			this.strCategory = strCategory;
			this.chrIdxStr = chrIdxStr;	
			this.spAbbr = spAbbr; // CAS556 add for TableReport
			
			chrIdxList = chrIdxStr.split(",");	// xgroups.idx for 
			chrNumList = chrNumStr.split(",");
			
			controlPanel = new JPanel();
						
			lblSpecies = new JLabel(spName);	// projects.name
			lblSpecies.setBackground(Color.WHITE);
			lblChrom = new JLabel("Chr: ");
			cmbChroms = new  JComboBox <String> ();
			
			cmbChroms.addItem("All");
			for(int x=0; x<chrNumList.length; x++)
				cmbChroms.addItem(chrNumList[x]);
			
			cmbChroms.setBackground(Color.WHITE);
			cmbChroms.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					boolean bSelAll = cmbChroms.getSelectedItem().equals("All");
					boolean bRange = !bSelAll && !bIsGene;
					txtStart.setEnabled(bRange); txtStop.setEnabled(bRange);
					cmbScale.setEnabled(bRange);
					lblStart.setEnabled(bRange); lblStop.setEnabled(bRange);
					
					if (bIsGene && !bSelAll) { // CAS541 
						setAllEnabled(false);
						setSpEnabled(spName);
					}
					else setAllEnabled(true);
				}
			});
			
			lblStart = new JLabel("From");lblStart.setBackground(Color.WHITE);lblStart.setEnabled(false);
			txtStart = new JTextField(10);txtStart.setBackground(Color.WHITE);txtStart.setEnabled(false);
			
			lblStop =  new JLabel("To");  lblStop.setBackground(Color.WHITE);lblStop.setEnabled(false);
			txtStop =  new JTextField(10);txtStop.setBackground(Color.WHITE);txtStop.setEnabled(false);
			
			cmbScale = new JComboBox <String> ();
			cmbScale.setBackground(Color.WHITE);
			cmbScale.addItem("bp"); cmbScale.addItem("kb"); cmbScale.addItem("mb");
			cmbScale.setSelectedIndex(1);
			cmbScale.setEnabled(false);
			
			refreshPanel();
		}
		
		private void refreshPanel() {
			removeAll();
			controlPanel.removeAll();
			
			controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.LINE_AXIS));
			controlPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
			controlPanel.setBackground(Color.WHITE);
			
			setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
			setAlignmentX(Component.LEFT_ALIGNMENT);
			setBackground(Color.WHITE);
			
			controlPanel.add(lblSpecies);
			
			Dimension d = lblSpecies.getPreferredSize();
			d.width = Math.max(d.width, SPECIES_NAME_WIDTH);
			lblSpecies.setPreferredSize(d); lblSpecies.setMinimumSize(d);
			
			controlPanel.add(lblChrom); controlPanel.add(cmbChroms);
			controlPanel.add(Box.createHorizontalStrut(5));
			
			controlPanel.add(lblStart); controlPanel.add(txtStart);
			controlPanel.add(lblStop); controlPanel.add(txtStop);controlPanel.add(cmbScale);
			
			add(controlPanel);
			add(Box.createVerticalStrut(5));

			theParent.refreshAllPanels();
		}
		protected void clear() {
			txtStart.setText("");
			txtStop.setText("");
			cmbChroms.setSelectedIndex(0);
			setEnabled(true);
		}
		// CAS517 add enable for locations; CAS541 add isGene
		public void setEnabled(boolean b) {
			cmbChroms.setEnabled(b);
			
			boolean x =  (cmbChroms.getSelectedItem().equals("All") || bIsGene || !b) ? false : true;
			
			txtStart.setEnabled(x); txtStop.setEnabled(x);
			cmbScale.setEnabled(x);
			lblStart.setEnabled(x); lblStop.setEnabled(x);
		}
		private String getStartFullNum() { // CAS513 add error message and return null
			String text = txtStart.getText().trim();
			if (text.contains(",")) text = text.replace(",",""); // CAS558 allow commas in input
			if (text.contentEquals("")) return "";
			
			try {
				int temp = Integer.parseInt(text); // CAS551 was long
				if(temp < 0) {
					Utilities.showWarningMessage("Invalid From (start) coordinate '" + text + "'");
					return null;
				}
				if (temp == 0) return "0";
				return temp + getScaleDigits();
			} catch(NumberFormatException e) {
				Utilities.showWarningMessage("Invalid From (start) coordinate '" + text + "'");
				return null;
			}
		}
		
		private String getStopFullNum() {
			String etext = txtStop.getText().trim();
			if (etext.contains(",")) etext = etext.replace(",",""); // CAS558 allow commas in input
			if (etext.contentEquals("")) return "";
			if (etext.contentEquals("0") || etext.contentEquals("-")) {// CAS549 add
				txtStop.setText("");
				return "";
			}
				
			try {
				int end = Integer.parseInt(etext);
				if (end <= 0) {
					Utilities.showWarningMessage("Invalid To (end) coordinate '" + etext + "'");
					return null;
				}
				
				String stext = txtStart.getText().trim(); // CAS549 add check (start is checked first, so this is fine)
				if (!stext.contentEquals("")) {
					int start = Integer.parseInt(stext);
					if (start>=end) {
						Utilities.showWarningMessage("Invalid From (start) '" + stext + "' > To (end) '" + etext + "'");
						return null;
					}
				}
				
				return end + getScaleDigits();
			} 
			catch(NumberFormatException e) {
				Utilities.showWarningMessage("Invalid To (end) coordinate '" + etext + "'");
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
			String num = txtStart.getText().trim();
			if (num.equals("")) return num;
			if(cmbScale.getSelectedIndex() == 1) return num + "kb";
			if(cmbScale.getSelectedIndex() == 2) return num + "mb";
			return num + "bp";
		}
		private String getStopkb() {
			String num = txtStop.getText().trim();
			if (num.equals("")) return num;
			if(cmbScale.getSelectedIndex() == 1) return num + "kb";
			if(cmbScale.getSelectedIndex() == 2) return num + "mb";
			return num + "bp";
		}
		private String getSpAbbr() { return spAbbr;}
		private String getSpName() {return lblSpecies.getText();}
		private String getSelChrNum() {
			return (String)cmbChroms.getSelectedItem();
		}
		private boolean isSpEnabled() {return cmbChroms.isEnabled();} // CAS518 
		private int getSelChrIdx() {
			if (!cmbChroms.isEnabled()) return -1; // CAS518 Single make sure enabled
			
			String chr = (String)cmbChroms.getSelectedItem();
			if (chr.equals("All")) return -1;
			
			for (int i=0; i<chrNumList.length; i++) 
				if (chrNumList[i].equals(chr)) return Integer.parseInt(chrIdxList[i]);
			
			System.out.println("Error: no " + chr);
			return -1;
		}
		private String getCategory() { return strCategory; }
		private String [] getChrIdxList() {return chrIdxList;}
		private String [] getChrNumList() {return chrNumList;}
		private String getChrIdxStr()    { return chrIdxStr;}
		private int getSpIdx() 			{return spIdx;}
		
		private void setChromSize(Dimension d) {
			cmbChroms.setPreferredSize(d);
			cmbChroms.setMaximumSize(d);
			cmbChroms.setMinimumSize(d);
		}
		private Dimension getChromSize() { return cmbChroms.getPreferredSize(); }
		
		private JLabel lblSpecies = null;
		private JComboBox <String> cmbChroms = null; // CAS513 add type
		private JLabel lblStart = null, lblStop = null, lblChrom = null;
		private JTextField txtStart = null, txtStop = null;
		private JComboBox <String >cmbScale = null; // CAS513 add type
		private JPanel controlPanel = null;
		
		private int spIdx=0;
		private String strCategory = "";
		private String [] chrIdxList;
		private String [] chrNumList;
		private String chrIdxStr;
		private String spAbbr = ""; // CAS556 add for TableReport
		
		private SpeciesSelectPanel theParent = null;
	} // End species row panel
	
	private SyMAPQueryFrame theParentFrame = null;
	
	private Vector<SpeciesSelect> spPanels = null;
	
	// CAS504 - changed 5 arrays to HashMaps with Idxs
	private HashMap <Integer, SpeciesSelect> chrIdx2panel = new HashMap <Integer, SpeciesSelect> ();
	private HashMap <Integer, SpeciesSelect> spIdx2panel = new HashMap <Integer, SpeciesSelect> ();
	private HashMap <String, Integer> spName2spIdx = new HashMap <String, Integer> ();
	private String pairWhere="";
	private boolean bIsGene=false;
} 
