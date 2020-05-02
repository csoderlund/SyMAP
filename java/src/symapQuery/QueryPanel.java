package symapQuery;

/***************************************************************
 * The Filter panel. 
 * CAS504 massive changes to queries
 */
import java.awt.Color;
import java.awt.Component;
import java.awt.ItemSelectable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collections;
import java.util.Iterator;
import java.util.Vector;
import java.util.HashMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import symap.projectmanager.common.Project;
import util.Utilities;

public class QueryPanel extends JPanel {
	private static final long serialVersionUID = 2804096298674364175L;
	private boolean OR=false, AND=true;

	public QueryPanel(SyMAPQueryFrame parentFrame) {
		theParentFrame = parentFrame;
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(Color.WHITE);
		
		pnlStepOne = new CollapsiblePanel("1. Filter hits", "");
		pnlStepTwo = new CollapsiblePanel("2. Filter putative gene families (PgeneFs)", "");

		speciesPanel = new SpeciesSelectPanel(theParentFrame);
		nSpecies = speciesPanel.getNumSpecies();
		
		JPanel searchPanel = createFilter1TextPanel();
		JPanel buttonPanel = createButtonPanel(searchPanel.getPreferredSize().width); 
		add(buttonPanel);							add(Box.createVerticalStrut(25));
		
		pnlStepOne.add(Box.createVerticalStrut(10));
		pnlStepOne.add(searchPanel);				 	pnlStepOne.add(Box.createVerticalStrut(25));
		pnlStepOne.add(createFilter2BasicPanel()); 	pnlStepOne.add(Box.createVerticalStrut(5));
		pnlStepOne.add(speciesPanel);				pnlStepOne.add(Box.createVerticalStrut(25));
		pnlStepOne.add(createFilter3OrphanPanel());
		pnlStepOne.collapse();
		pnlStepOne.expand();
		
		pnlStepTwo.add(createFilter4PgeneFPanel());
		pnlStepTwo.collapse();
		pnlStepTwo.expand();
		
		add(pnlStepOne);
		add(Box.createVerticalStrut(5));
		add(pnlStepTwo);
		add(Box.createVerticalStrut(5));
	}
	private void setPgenefEnable(boolean b) {
		chkLinkageInc.setEnabled(b);
		chkUnannotInc.setEnabled(b);
		chkOnlyInc.setEnabled(b);
		for(int x=0; x<exSpecies.length; x++)  exSpecies[x].setEnabled(b);
		for(int x=0; x<incSpecies.length; x++) incSpecies[x].setEnabled(b);
	}
	private void setBasicEnabled(boolean b) {
		blockRow.setEnabled(b);
		annoRow.setEnabled(b);
		txtCollinearN.setEnabled(b);
		lblCollinearN.setEnabled(b);
		chkOrphan.setEnabled(b); cmbOrphanSpecies.setEnabled(b);
		chkBlock.setEnabled(b); txtBlock.setEnabled(b);
		chkHitIdx.setEnabled(b); txtHitIdx.setEnabled(b);
	}
	public void addExecuteListener(ActionListener l) { btnExecute.addActionListener(l);}
	
	private boolean isBlock() 	{ return blockRow.isEnabled() && blockRow.isOne(); }
	private boolean isNotBlock() { return blockRow.isEnabled() && blockRow.isTwo(); }
	
	public  boolean isOneAnno() 	{ return annoRow.isEnabled() && annoRow.isOne(); }
	public boolean isTwoAnno() 	{ return annoRow.isEnabled() && annoRow.isTwo(); }
	private boolean isNoAnno() 	{ return annoRow.isEnabled() && annoRow.isThree(); }
	private boolean isAnnoTxt()	{ return txtAnno.isEnabled() && txtAnno.getText().trim().length()>0;}
	private boolean isBlockNum()	{ return txtBlock.isEnabled() && txtBlock.getText().trim().length()>0;}
	private boolean isHitIdx()	{ return txtHitIdx.isEnabled() && txtHitIdx.getText().trim().length()>0;}
	
	private int isCollinear() 	{ 
		if (!txtCollinearN.isEnabled()) return 0;
		if (txtCollinearN.getText().length()==0) return 0;
		try {
			int n = Integer.parseInt(txtCollinearN.getText());
			if (n<=0) return 0;
			return n;
		} catch (Exception e) {};
		return 0;
	}
	// these are only good for the last query, so should only be used for building the ables
	public boolean isOrphan() 		{ return chkOrphan.isEnabled() && chkOrphan.isSelected(); }
	public boolean isPgeneF()    	{ return chkPgeneF.isEnabled() && chkPgeneF.isSelected(); }
	public boolean isLinkageInc() 	{ return chkLinkageInc.isEnabled() && chkLinkageInc.isSelected(); }
	public boolean isUnannotInc() 	{ return chkUnannotInc.isEnabled() && chkUnannotInc.isSelected(); }
	public boolean isOnlyInc()    	{ return chkOnlyInc.isEnabled() && chkOnlyInc.isSelected(); }
	public boolean isInclude(int sp) {return incSpecies[sp].isEnabled() && incSpecies[sp].isSelected(); }
	public boolean isExclude(int sp) {return exSpecies[sp].isEnabled() && exSpecies[sp].isSelected();}
	public SpeciesSelectPanel getSpeciesPanel() { return speciesPanel;}
	public  HashMap <Integer, String> getGrpCoords() { return grpCoords;}
	
	/****************************************************
	 *  Orphan
	 *  "SELECT " + getDBFieldList(isOrphan) + " FROM pseudo_annot AS " + Q.PA + " " + whereClause;
	 */
	public String makeOrphanWhere() {
		String whereClause = " where not exists(select * from pseudo_hits_annot AS PHA where PA.idx=PHA.annot_idx) and PA.type='gene'	";
		
	// Annotation
		String text = txtAnno.getText();
		String anno = (text.length() == 0) ? "" : "PA.name LIKE '%" + text + "%'";
		whereClause = joinBool(whereClause, anno, true);
		
	// Species, chromosome, location
		String selSp = (String) cmbOrphanSpecies.getSelectedItem();
		
		String chrWhere="", locWhere="";
		for(int x=0; x<nSpecies; x++) {
			String species =  speciesPanel.getSpName(x);
			if (!selSp.equals("All") && !selSp.equals(species)) continue;
			
			int chrIdx = speciesPanel.getSelChrIdx(x);
			if (chrIdx<0) {
				chrWhere = joinStr(chrWhere, speciesPanel.getChrIdxStr(x), ",");
			}
			else {
				String start = speciesPanel.getChrStart(x);
				String stop =  speciesPanel.getChrStop(x);
				if (start.equals("") && stop.equals("")) chrWhere = joinStr(chrWhere, String.valueOf(chrIdx), ",");
				else {
					String clause = "(PA.grp_idx=" + chrIdx + " AND PA.start>=" + start + " AND PA.end<=" + stop + ")";
					locWhere = joinBool(locWhere, clause, OR);
				}
			}
		}	
		if (!chrWhere.equals("")) chrWhere = "PA.grp_idx in (" + chrWhere + ")";
		if (!locWhere.equals("")) locWhere = "(" + locWhere + ")";
		String chrStr = "(" + joinBool(chrWhere, locWhere, OR) + ")";
		whereClause = joinBool(whereClause, chrStr, AND);
		
		return whereClause;
	}
	/*********************************************************
	 * Hit
	 * SELECT " + getDBFieldList() + " FROM pseudo_hits AS " + Q.PH + whereClause
	 */
	public String makeHitWhere() {
		String whereClause= " where " + speciesPanel.getPairWhere(); // PH.pair_idx in (...)
		
		if (isHitIdx()) { // if hit, no other filters
			whereClause = joinBool(whereClause, "PH.idx=" + txtHitIdx.getText(), AND);
			return whereClause;
		}
		
		whereClause = joinBool(whereClause, makeHitAnno(), AND);
		
		if (isBlockNum()) { // if block, only anno filter
			whereClause = joinBool(whereClause, makeHitBlock(), AND);
			return whereClause;
		}
		
		// Chr, loc
		whereClause = joinBool(whereClause, makeHitSpChr(), AND);
		
		// isSynteny, isAnno, isColinear
		if (isBlock())         	whereClause = joinBool(whereClause, "PBH.block_idx is not null", AND);
		else if (isNotBlock()) 	whereClause = joinBool(whereClause, "PBH.block_idx is null", AND);
		
		/** 
		 * isOneAnno requires postprocessing
	     * isTwoAnno needs postprocessing only because there are a few cases where (PH.annot1_idx>0 and PH.annot2_idx>0)
		 *    is not true but it really  is true. It needs to be fixed in AnchorMain, which will take some work....
		 * if (isTwoAnno())  		whereClause = joinBool(whereClause, 
		 * 	"(PH.annot1_idx>0 and PH.annot2_idx>0)  AND PA.idx is not null", AND);
		 **/
		if (isNoAnno())	  	whereClause = joinBool(whereClause, 
				"(PH.annot1_idx=0 and PH.annot2_idx=0) AND PA.idx is null", AND); // 'is null' is necessary. 
		
		int n = isCollinear();
		if (n>0) whereClause = joinBool(whereClause, "PH.runsize>" + n, AND);
		
		return whereClause;
	}	
	/****************************************
	 * If this extra select is no done, then ONLY rows with PA.name with zinc are returned
	 * and there is no annotation for the otherside of the het
	 */
	private String makeHitAnno() {
		if (!isAnnoTxt()) return "";
		
		String clause = "PA.name LIKE '%" + txtAnno.getText() + "%'";
		return  "PH.idx in (SELECT  PH.idx " +
				"FROM pseudo_hits AS PH " +
				"LEFT JOIN pseudo_hits_annot AS PHA ON PH.idx = PHA.hit_idx " +
				"LEFT JOIN pseudo_annot AS PA ON PHA.annot_idx = PA.idx " +
				"WHERE " + clause + ")";
	}
	private String makeHitBlock() {
		String where="";
		String block = txtBlock.getText().trim();
		if (block.equals("")) return "";
		
		String list1=null, list2=null;
		String [] vals = block.split("\\."); 
		if (vals.length!=3) {
			System.err.println("Invalid block number " + block);
			return "";
		}
		for (int p=0; p<nSpecies; p++) {
			String [] grpIdx = speciesPanel.getChrIdxList(p);
			String [] grpNum = speciesPanel.getChrNumList(p);
			for (int i=0; i<grpNum.length; i++) {
				if (grpNum[i].equals(vals[0])) {
					list1 = (list1==null) ? grpIdx[i] : (list1 + "," + grpIdx[i]);
				}
				else if (grpNum[i].equals(vals[1])) {
					list2 = (list2==null) ? grpIdx[i] : (list2 + "," + grpIdx[i]);
				}
			}
		}
		if (list1==null || list2==null) {
			System.err.println("No block number " + block);
			txtBlock.setText("");
			return "";
		}
		where = "B.blocknum=" + vals[2] + 
				" AND (B.grp1_idx in (" + list1 + ") AND B.grp2_idx in (" + list2 + "))";
		return where;
	}
	/************************************
	 * The chromosomes are queried in MySQL and the locations in DBdata
	 */
	private String makeHitSpChr() {
		String grpList="", where="";
		grpCoords.clear();
		
		// First check to see if any chromosomes are selected
		boolean isSet=false;
		for (int p=0; p<nSpecies; p++) {
			int index = speciesPanel.getSelChrIdx(p); 
			if(index >0) isSet=true;
		}
		if (!isSet) return "";
		
		for(int p=0; p<nSpecies; p++) {
			int index = speciesPanel.getSelChrIdx(p); // returns a PH.grp1_idx 
			if(index <= 0) continue;
			
			if (grpList.equals("")) grpList = index+"";
			else grpList += "," + index;
			
			String start = speciesPanel.getChrStart(p);
			String stop = speciesPanel.getChrStop(p);
			if (start.equals("")) start = "0";
			if (stop.equals(""))  stop = "0";
			
			grpCoords.put(index, start + Q.delim2 + stop); // need all selected for DBdata.passFilter
		}
		if (grpList.equals("")) return "";
		else if (grpList.contains(",")) {
			grpList = "(" + grpList + ")";
			where  = "( (PH.grp1_idx IN " + grpList + ") ";
			where += " AND ";
			where += "  (PH.grp2_idx IN " + grpList + ") )";
		}
		else {
			where  = "( (PH.grp1_idx = " + grpList + ") ";
			where += " OR ";
			where += "  (PH.grp2_idx = " + grpList + ") )";
		}
		return where; 
	}

	private static String joinBool(String left, String right, boolean isAND) { // mysql
		if(left.length() == 0) return right;
		if(right.length() == 0) return left;
		if(isAND) return left + " AND " + right;
		return left + " OR " + right;
	}	
	/*******************************************************
	 * Summary
	 * CAS503 made this a complete list of filters
	 */
	public String makeSummary() { 
		int numSpecies = speciesPanel.getNumSpecies();
		if(numSpecies ==0) return "No species"; // not possible?
		
		if (isHitIdx()) {
			return "HitIdx="+txtHitIdx.getText().trim();
		}
		String retVal = txtAnno.getText();
		retVal = joinStr(retVal, makeSummaryBasic(), ";  ");
		
		if (isBlockNum()) {
			retVal = joinStr(retVal, "Block="+txtBlock.getText().trim(), ";  ");
			return retVal;
		}
		
		if(isOrphan()) {
			retVal = joinStr(retVal, "Only orphans", ";  ");
			String selSp = (String) cmbOrphanSpecies.getSelectedItem();
			retVal = joinStr(retVal, selSp, ";  ");
			return retVal;
		}
		
		if (isBlock())		retVal = joinStr(retVal, "Block hits", ";  ");
		else if (isNotBlock()) retVal = joinStr(retVal, "No Block hits", ";  ");
		
		if (isOneAnno())		retVal = joinStr(retVal, 	"Either Gene hit", ";  ");
		else if (isTwoAnno())	retVal = joinStr(retVal, "Both Gene hits", ";  ");
		else if (isNoAnno()) retVal = joinStr(retVal, 	"No Gene hits", ";  ");
		
		int n=isCollinear();
		if (n>0)	retVal = joinStr(retVal, "Collinear >= " + n, ";  ");
		
		if (isPgeneF()) {
			retVal = joinStr(retVal, "Run PgeneF", ";  ");
			String inc="", exc="";
			for (int i=0; i<speciesName.length; i++) {
				if (isInclude(i)) inc = joinStr(inc, speciesName[i],  ",");
				if (isExclude(i)) exc = joinStr(exc, speciesName[i],  ",");
			}
			if (!inc.equals("")) inc = "Inc (" + inc + ")";
			if (!exc.equals("")) exc = "Exc (" + exc + ")";
			retVal = joinStr(retVal, joinStr(inc, exc, ", "), ";  ");
			
			if(isUnannotInc())	retVal = joinStr(retVal, "Not annotated", ";  ");
			if(isLinkageInc())	retVal = joinStr(retVal, "Linkage", ";  ");
			if(isOnlyInc())		retVal = joinStr(retVal, "Only included", ";  ");
		}
		if (retVal.equals("")) retVal="None";
	
		return retVal; 
	}
	// CAS504 add Include 
	private String makeSummaryBasic() {
		int numSpecies = speciesPanel.getNumSpecies();

		String retVal = ""; // CAS503 added info and remove some wordiness, i.e. do not need to list species unless location

		for(int x=0; x<numSpecies; x++) {
			String species = speciesPanel.getSpName(x);
			String chroms =  speciesPanel.getSelChrNum(x);
			String start = speciesPanel.getStartAbbr(x);
			String end = speciesPanel.getStopAbbr(x);
			if(!chroms.equals("All")) {
				String loc = " Chr " + chroms;
				if (!start.equals("") && !end.equals("")) loc += " Range " + start + "-" + end; 
				else if (!start.equals("")) loc += " Start " + start; 
				else if (!end.equals(""))   loc += " Range 1-" + end;
				retVal = joinStr(retVal, species + loc , ", ");
			}
		}		
		return retVal;
	}
	
	private String joinStr (String s1, String s2, String delim) { // summary
		if (!s1.equals("") && !s2.equals("")) return s1 + delim + s2;
		else if (!s1.equals("")) return s1;
		else if (!s2.equals("")) return s2;
		else return "";
	}
	/********************************************************************
	 * Filter panels
	 */
	private JPanel createFilter2BasicPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBackground(Color.WHITE);
		
		blockRow = new OptionsRow("In Block (Synteny hit)", "Yes", "No", "", "All", 4, 150);
		blockRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(blockRow); panel.add(Box.createVerticalStrut(5));
		
		annoRow = new OptionsRow("Annotated (Gene hit)", "Either", "Both", "None", "All", 4, 150);
		annoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(annoRow); panel.add(Box.createVerticalStrut(5));
		
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBackground(Color.WHITE);
		
		lblCollinearN = new JLabel("Collinear genes N >=");
		row.add(lblCollinearN); row.add(Box.createHorizontalStrut(3));
		txtCollinearN = new JTextField(4); txtCollinearN.setBackground(Color.WHITE);
		txtCollinearN.setText("0"); txtCollinearN.setMaximumSize(txtCollinearN.getPreferredSize());
		row.add(txtCollinearN);
		panel.add(row); panel.add(Box.createVerticalStrut(20));
		
		return panel;
	}
	private JPanel createFilter3OrphanPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBackground(Color.WHITE);
		
		// orphan row
		JPanel	row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setBackground(Color.WHITE);
		
		chkOrphan = new JCheckBox("Only orphan genes ");	
		chkOrphan.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkOrphan.setBackground(Color.WHITE);
		chkOrphan.addActionListener(new ActionListener() { // CAS503 disabled others if orphans checked
			public void actionPerformed(ActionEvent e) {
				boolean orpNotSel = !chkOrphan.isSelected();
				setBasicEnabled(orpNotSel);
				chkPgeneF.setEnabled(orpNotSel);
				if (chkPgeneF.isSelected() && orpNotSel) setPgenefEnable(true);
				else 	setPgenefEnable(false);	
				chkOrphan.setEnabled(true);
				cmbOrphanSpecies.setEnabled(true);
			}
		});
		row.add(chkOrphan); row.add(Box.createHorizontalStrut(5));
		
		cmbOrphanSpecies = new JComboBox();
		cmbOrphanSpecies.setBackground(Color.WHITE);
		cmbOrphanSpecies.addItem("All");
		int num = speciesPanel.getNumSpecies();
		for(int x=0; x<num; x++)
			cmbOrphanSpecies.addItem(speciesPanel.getSpName(x));
		cmbOrphanSpecies.setMaximumSize(cmbOrphanSpecies.getPreferredSize());
		
		row.add(new JLabel("Project: ")); row.add(Box.createHorizontalStrut(1));
		row.add(cmbOrphanSpecies); 
		row.setMaximumSize(row.getPreferredSize());
		row.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(row); panel.add(Box.createVerticalStrut(10));
		
		row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
		row.setBackground(Color.WHITE);
		
		chkBlock = new JCheckBox("Block");
		chkBlock.setBackground(Color.WHITE);
		chkBlock.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				boolean blkNotSel = !chkBlock.isSelected();
				setBasicEnabled(blkNotSel);
				
				chkPgeneF.setEnabled(blkNotSel);
				if (chkPgeneF.isSelected() && blkNotSel) setPgenefEnable(true);
				else 	setPgenefEnable(false);	
				
				chkBlock.setEnabled(true);
				txtBlock.setEnabled(chkBlock.isSelected());
			}
		});
		txtBlock = new JTextField(8); txtBlock.setEnabled(false);
		row.add(chkBlock); row.add(Box.createHorizontalStrut(10));
		row.add(txtBlock); row.add(Box.createHorizontalStrut(30));
		
		chkHitIdx = new JCheckBox("HitIdx");
		chkHitIdx.setBackground(Color.WHITE);
		chkHitIdx.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				boolean hitNotSel = !chkHitIdx.isSelected();
				setBasicEnabled(hitNotSel);
				
				chkPgeneF.setEnabled(hitNotSel);
				if (chkPgeneF.isSelected() && hitNotSel) setPgenefEnable(true);
				else 	setPgenefEnable(false);	
				
				txtAnno.setEnabled(hitNotSel);
				speciesPanel.setEnabled(hitNotSel); // only one that sets this
				
				chkHitIdx.setEnabled(true);
				txtHitIdx.setEnabled(chkHitIdx.isSelected());
			}
		});
		txtHitIdx = new JTextField(8); txtHitIdx.setEnabled(false);
		row.add(chkHitIdx); row.add(Box.createHorizontalStrut(10));
		row.add(txtHitIdx); 	
		
		row.setMaximumSize(row.getPreferredSize());
		row.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(row);
		return panel;
	}
	private JPanel createFilter4PgeneFPanel() {		
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.PAGE_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBackground(Color.WHITE);

		Vector<Project> theProjects = theParentFrame.getProjects();
		
		chkPgeneF = new JCheckBox("Compute PgeneF");	
		chkPgeneF.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkPgeneF.setBackground(Color.WHITE);
		chkPgeneF.addActionListener(new ActionListener() { 
			public void actionPerformed(ActionEvent e) {
				setPgenefEnable(chkPgeneF.isSelected());
				
			}
		});
		chkPgeneF.setSelected(false);
		panel.add(chkPgeneF);
		
		speciesName = new String [theProjects.size()];
		incSpecies = new JCheckBox[theProjects.size()];
		for(int x=0; x<incSpecies.length; x++) {
			speciesName[x] = theProjects.get(x).getDisplayName();
			incSpecies[x] = new JCheckBox(speciesName[x]);
			incSpecies[x].setBackground(Color.WHITE);
			incSpecies[x].setEnabled(false);
		}
		exSpecies = new JCheckBox[theProjects.size()];
		for(int x=0; x<exSpecies.length; x++) {
			exSpecies[x] = new JCheckBox(speciesName[x]);
			exSpecies[x].setBackground(Color.WHITE);
			exSpecies[x].setEnabled(false);
		}		
		
		Vector<String> sortedGroups = new Vector<String> ();
		for(int x=0; x<theProjects.size(); x++) {
			if(!sortedGroups.contains(theProjects.get(x).getCategory())) 
				sortedGroups.add(theProjects.get(x).getCategory());
		}
		Collections.sort(sortedGroups);
		
		for(int grpIdx=0; grpIdx<sortedGroups.size(); grpIdx++) {
			String catName = sortedGroups.get(grpIdx);
			boolean firstOne = true;

			JPanel row = new JPanel();
			row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
			row.setBackground(Color.WHITE);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			row.add(new JLabel("Include: "));
			for(int x=0; x<incSpecies.length; x++) {
				if(catName.equals(theProjects.get(x).getCategory())) {
					if(firstOne) {
						panel.add(Box.createVerticalStrut(10));
						if(sortedGroups.size() > 1)
							panel.add(new JLabel(catName.toUpperCase()));
						firstOne = false;
					}
					row.add(incSpecies[x]);
					row.add(Box.createHorizontalStrut(5));
				}
			}
			panel.add(row);
	
			row = new JPanel();
			row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
			row.setBackground(Color.WHITE);
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
	
			row.add(new JLabel("Exclude: "));
			for(int x=0; x<exSpecies.length; x++) {
				if(catName.equals(theProjects.get(x).getCategory())) {
					if(firstOne) {
						panel.add(Box.createVerticalStrut(10));
						if(sortedGroups.size() > 1)
							panel.add(new JLabel(catName.toUpperCase()));
						firstOne = false;
					}
					row.add(exSpecies[x]);
					row.add(Box.createHorizontalStrut(5));
				}
			}
			if (nSpecies>2) panel.add(row);
		}
		
		chkUnannotInc = new JCheckBox("No annotation to included species");	  chkUnannotInc.setEnabled(false);
		chkLinkageInc = new JCheckBox("Complete linkage of included species"); chkLinkageInc.setEnabled(false);
		chkOnlyInc = 	new JCheckBox("At least one hit for each included species"); chkOnlyInc.setEnabled(false);
		chkUnannotInc.setAlignmentX(Component.LEFT_ALIGNMENT); chkUnannotInc.setBackground(Color.WHITE);
		chkLinkageInc.setAlignmentX(Component.LEFT_ALIGNMENT); chkLinkageInc.setBackground(Color.WHITE);
		chkOnlyInc.setAlignmentX(Component.LEFT_ALIGNMENT); chkOnlyInc.setBackground(Color.WHITE);
		
		panel.add(Box.createVerticalStrut(10));
		panel.add(new JLabel("PgeneF must have: "));
		panel.add(Box.createVerticalStrut(2));
		panel.add(chkUnannotInc); panel.add(Box.createVerticalStrut(2));
		if (nSpecies>2) {
			panel.add(chkLinkageInc);panel.add(Box.createVerticalStrut(2));
			panel.add(chkOnlyInc);
		}
		panel.setMaximumSize(panel.getPreferredSize());
	
		return panel;
	}

	private JPanel createButtonPanel(int width) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBackground(Color.WHITE);
		
		btnExecute = new JButton("Run Search");
		btnExecute.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnExecute.setBackground(Color.WHITE);

		JButton btnClear = new JButton("Clear Filters");
		btnExecute.setAlignmentX(Component.LEFT_ALIGNMENT);
		btnClear.setBackground(Color.white);
		btnClear.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setClearFilters();
			}
		});

		JButton btnHelp = new JButton("Help");
		btnHelp.setAlignmentX(Component.RIGHT_ALIGNMENT);
		btnHelp.setBackground(Utilities.HELP_PROMPT);
		
		btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				Utilities.showHTMLPage(null, "SyMAP Query Help", "/html/QueryHelp.html");
			}
		});
		
		panel.add(btnClear);
		panel.add(Box.createHorizontalStrut(180));
		panel.add(btnExecute);
		panel.add(Box.createHorizontalStrut(240));
		panel.add(btnHelp);
		
		panel.setMaximumSize(panel.getPreferredSize());
		return panel;
	}
	
	private JPanel createFilter1TextPanel() {
		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.PAGE_AXIS));
		page.setBackground(Color.WHITE);
		
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
		row.setBackground(Color.WHITE);

		JLabel aLabel = new JLabel("Annotation Description");
		txtAnno = new JTextField(30);
		row.add(aLabel); row.add(Box.createHorizontalStrut(10));
		row.add(txtAnno); 
		row.setMaximumSize(row.getPreferredSize());
		row.setAlignmentX(LEFT_ALIGNMENT);
		page.add(row); 
		
		return page;
	}	
	
	private void setClearFilters() {
		txtAnno.setText("");
		blockRow.setValue(4);
		annoRow.setValue(4);
		txtCollinearN.setText("");
		chkOrphan.setSelected(false); cmbOrphanSpecies.setSelectedIndex(0);
		speciesPanel.clear();
		chkBlock.setSelected(false); txtBlock.setText("");
		chkHitIdx.setSelected(false); txtHitIdx.setText("");
		
		chkPgeneF.setSelected(false);
		chkOnlyInc.setSelected(false);
		chkUnannotInc.setSelected(false);
		chkLinkageInc.setSelected(false);
		for(int x=0; x<exSpecies.length; x++)  exSpecies[x].setSelected(false);
		for(int x=0; x<incSpecies.length; x++) incSpecies[x].setSelected(false);
		
		setBasicEnabled(true);
		chkPgeneF.setEnabled(true);
		setPgenefEnable(false);
	}
	/**********************************************************************
	 * 
	 */
	public class OptionsRow extends JPanel implements ItemSelectable, ItemListener
	{
		private static final long serialVersionUID = -8714286499322636798L;
		
		private JLabel titleLabel;
		private JRadioButton case1Button, case2Button, case3Button, ignButton;
		private ButtonGroup group;
		private Vector<ItemListener> listeners;
		
		public OptionsRow ( String titleText, String strCase1Text, 
								String strCase2Text, String strCase3Text, String strIgnText, 
										int nInitialValue, int width )
		{
			titleLabel    = new JLabel(titleText);
			case1Button = 	new JRadioButton(strCase1Text);
			case2Button = 	new JRadioButton(strCase2Text);
			case3Button = 	new JRadioButton(strCase3Text);
			ignButton  = 	new JRadioButton(strIgnText);
			
		    group = new ButtonGroup();
		    group.add(case1Button); group.add(case2Button); group.add(case3Button); group.add(ignButton);
			
			setLayout( new BoxLayout ( this, BoxLayout.X_AXIS ) );
			super.setBackground(Color.WHITE);
			add(titleLabel);
			if (width>0 && width > titleLabel.getPreferredSize().width) 
				add(Box.createHorizontalStrut(width-titleLabel.getPreferredSize().width));
			if (!strCase1Text.equals(""))  	{ case1Button.setBackground(Color.WHITE);	add(case1Button); }
			if (!strCase2Text.equals(""))	{ case2Button.setBackground(Color.WHITE);	add(case2Button); }
			if (!strCase3Text.equals(""))	{ case3Button.setBackground(Color.WHITE);	add(case3Button); }
			if (!strIgnText.equals(""))		{ ignButton.setBackground(Color.WHITE);		add(ignButton); }
			
			listeners = new Vector<ItemListener>();
			case1Button.addItemListener(this);
			case2Button.addItemListener(this);
			case3Button.addItemListener(this);
			ignButton.addItemListener(this);

			setValue ( nInitialValue );
		}
		
		public void setEnabled(boolean enabled) {
			titleLabel.setEnabled(enabled);
			case1Button.setEnabled(enabled);
			case2Button.setEnabled(enabled);
			case3Button.setEnabled(enabled);
			ignButton.setEnabled(enabled);
		}
		
		public void itemStateChanged(ItemEvent e) 
		{
		    	Iterator<ItemListener> iter = listeners.iterator();
		    	while ( iter.hasNext() ) {
		    		JRadioButton b = (JRadioButton)e.getItem();
		    		iter.next().itemStateChanged(
		    				new ItemEvent(this, e.getID(), b.getText(), e.getStateChange()));
		    	}
		}
		public boolean isOne() 		{return case1Button.isSelected();}
		public boolean isTwo()  		{return case2Button.isSelected();}
		public boolean isThree()  	{return case3Button.isSelected();}
		
		public int getValue() {
			if (case1Button.isSelected()) return 1;
			if (case2Button.isSelected()) return 2;
			if (case3Button.isSelected()) return 3;
			return 4;
		}
		public void setValue( int nVal )
		{
			switch ( nVal ) {
			case 1: group.setSelected(case1Button.getModel(), true); break;
			case 2:	group.setSelected(case2Button.getModel(), true); break;		
			case 3:	group.setSelected(case3Button.getModel(), true); break;	
			case 4:	group.setSelected(ignButton.getModel(), true); break;
			}
		}
		
		public void addItemListener(ItemListener l) {
			listeners.add(l);
		}
		
		public Object[] getSelectedObjects() {
			return null;
		}
		
		public void removeItemListener(ItemListener l) {
			listeners.remove(l);
		}
		
		public String toString() {
			if (case1Button.isSelected()) return titleLabel.getText() + " " + case1Button.getText();
			else if (case2Button.isSelected()) return titleLabel.getText() + " " + case2Button.getText();
			else if (case3Button.isSelected()) return titleLabel.getText() + " " + case3Button.getText();
			else if (ignButton.isSelected()) return titleLabel.getText() + " " + ignButton.getText();
			else return "<blank>";
		}
	}
    /************************************************************/
	private SyMAPQueryFrame theParentFrame = null;
	
	// Not big enough to bother adding buttons to pnlStepOne.expand(); pnlStepOne.collapse()
	private CollapsiblePanel pnlStepOne = null, pnlStepTwo = null; 
	
	private JTextField txtAnno = null; 
	
	private SpeciesSelectPanel speciesPanel = null;	
	private JCheckBox [] incSpecies = null, exSpecies = null;
	private String [] speciesName = null;
	
	private JCheckBox chkOrphan = null,
			chkOnlyInc = null, chkUnannotInc = null, chkLinkageInc = null;
	
	// CAS504 add
	private OptionsRow  blockRow = null, annoRow=null;
	private JTextField txtCollinearN = null;
	private JLabel lblCollinearN = null;
	
	private JComboBox cmbOrphanSpecies = null;
	private JCheckBox chkPgeneF = null;
	private JCheckBox chkBlock = null, chkHitIdx = null;
	private JTextField txtBlock = null, txtHitIdx = null;
	
	private JButton btnExecute = null;
	
	private int nSpecies=0;
	private HashMap <Integer, String> grpCoords = new HashMap <Integer, String> ();
}
