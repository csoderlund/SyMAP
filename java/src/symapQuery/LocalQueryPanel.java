package symapQuery;

/***************************************************************
 * The Filter panel. 
 * Note - the Local prefix has no meaning.
 */
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import symap.projectmanager.common.Project;
import util.UserPrompt;
import util.Utilities;

import java.util.Collections;
import java.util.Vector;

import backend.Utils;

public class LocalQueryPanel extends JPanel {
	private static final long serialVersionUID = 2804096298674364175L;

	public LocalQueryPanel(SyMAPQueryFrame parentFrame) {
		theParentFrame = parentFrame;
		
		pnlStepOne = new CollapsiblePanel("1. Filter hits", "");
		pnlStepTwo = new CollapsiblePanel("2. Filter putative gene families (PgeneFs)", "");
		pnlStepThree = new CollapsiblePanel("3. Filter displayed hits", "");

		createSpeciesPanel();
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(Color.WHITE);
		
		JPanel searchPanel = createStringSearchPanel();
		JPanel buttonPanel = createButtonPanel(searchPanel.getPreferredSize().width); 

		add(buttonPanel);
		add(Box.createVerticalStrut(25));
		pnlStepOne.add(Box.createVerticalStrut(10));
		pnlStepOne.add(searchPanel);
		pnlStepOne.add(Box.createVerticalStrut(25));
		pnlStepOne.add(speciesPanel);
		pnlStepOne.add(createFilterPanel());
		pnlStepOne.collapse();
		pnlStepOne.expand();
		
		pnlStepTwo.add(createFilterGroupPanel());
		pnlStepTwo.collapse();
		pnlStepTwo.expand();
		
		pnlStepThree.add(createFilterHitPanel());
		pnlStepThree.collapse();
		pnlStepThree.expand();
		
		add(pnlStepOne);
		add(Box.createVerticalStrut(5));
		add(pnlStepTwo);
		add(Box.createVerticalStrut(5));
		add(pnlStepThree);
		add(Box.createVerticalStrut(5));
	}

	public void addExecuteListener(ActionListener l) { btnExecute.addActionListener(l);}
	public boolean isSynteny() { return chkSynteny.isSelected(); }
	public boolean isCollinear() { return chkCollinear.isSelected(); }
	public boolean isClique() { return chkClique.isSelected(); }
	public boolean isUnannot() { return chkUnannot.isSelected(); }
	public boolean isOnlyIncluded() { return chkIncludeOnly.isSelected(); }
	public boolean isOrphan() { return chkOrphan.isSelected(); }
	public boolean isInclude(int species) { return incSpecies[species].isSelected(); }
	public boolean isExclude(int species) {return exSpecies[species].isSelected();}
	
	/****************************************************
	 * Build query - following three are called by ListDataPanel
	 */
	public String getSubQuery() {
		String retVal = "";

		int numSpecies = speciesPanel.getNumSpecies(); //Species selection
		if(numSpecies > 0) {
			retVal = combineBool(retVal, getAnnotationNameFilter(), true);
			retVal = combineBool(retVal, " PH.proj1_idx != PH.proj2_idx ", true);
			retVal = combineBool(retVal, getPairGroupQueryAll(), true);
		}
		return retVal; 
	}
	public String getAnnotSubQuery() {
		String retVal = "";

		int numSpecies = speciesPanel.getNumSpecies(); //Species selection
		if(numSpecies > 0) {
			retVal = combineBool(retVal, getAnnotationNameFilter(), true);
			retVal = combineBool(retVal, getAnnotGroupQueryAll(), true);
		}
		return retVal; 
	}	
	// CAS503 made this a complete list of filters
	public String getSubQuerySummary() { 
		int numSpecies = speciesPanel.getNumSpecies();
		if(numSpecies ==0) return "No species"; // not possible?
		
		String retVal = getAnnotationNameFilterSummary();
		retVal = join(retVal, getSpeciesLocFilterSummary(), ";  ");
		
		if(isSynteny())		retVal = join(retVal, "Only synteny hits", ";  ");
		if(isCollinear())	retVal = join(retVal, "Only colliner hits", ";  ");
		
		// XXX only include this if Only included, Only orphans, or whatever else uses it.
		retVal = join(retVal, getIncExcFilterSummary(), ";  ");
		
		if(isOrphan())		retVal = join(retVal, "Only orphans", ";  ");
		if(isUnannot())		retVal = join(retVal, "Not annotated", ";  ");
		if(isClique())		retVal = join(retVal, "Linkage", ";  ");
		if(isOnlyIncluded())	retVal = join(retVal, "Only included", ";  ");
	
		return retVal; 
	}
	private String join (String s1, String s2, String delim) {
		if (!s1.equals("") && !s2.equals("")) return s1 + delim + s2;
		else if (!s1.equals("")) return s1;
		else if (!s2.equals("")) return s2;
		else return "";
	}
	private String getAnnotationNameFilter() {
		String text = txtAnnotation.getText();
		if(text.length() == 0) return "";
		return "PA.name LIKE '%" + text + "%'";
	}
	
	private String getAnnotationNameFilterSummary() {
		String text = txtAnnotation.getText();
		if(text.length() == 0) return "";
		return text;
	}
		
	private String getSpeciesLocFilterSummary() {
		int numSpecies = speciesPanel.getNumSpecies();

		String retVal = ""; // CAS503 added info and remove some wordiness, i.e. do not need to list species unless location

		for(int x=0; x<numSpecies; x++) {
			String species = speciesPanel.getSpecies(x);
			String chroms =  speciesPanel.getChromosome(x);
			String start = speciesPanel.getStartStr(x);
			String end = speciesPanel.getStopStr(x);
			if(!chroms.equals("All")) {
				String loc = " Chr " + chroms;
				if (!start.equals("") && !end.equals("")) loc += " Range " + start + "-" + end; 
				else if (!start.equals("")) loc += " Start " + start; 
				else if (!end.equals(""))   loc += " Range 1-" + end;
				retVal = join(retVal, species + loc , ", ");
			}
		}		
		return retVal;
	}

	private String getIncExcFilterSummary() {
		String inc="", exc="";
		for (int i=0; i<speciesName.length; i++) {
			if (isInclude(i)) inc = join(inc, speciesName[i],  ",");
			if (isExclude(i)) exc = join(exc, speciesName[i],  ",");
		}
		if (!inc.equals("")) inc = "In (" + inc + ")";
		if (!exc.equals("")) exc = "Ex (" + exc + ")";
		return join(inc, exc, ", ");
	}
	// If changed, must change getAnnotGroupQueryAll()!!!
	private String getPairGroupQueryAll() {
		String retVal = "";
		int numSpecies = speciesPanel.getNumSpecies();
		
		Vector<String> speciesIn = new Vector<String>();
		Vector<String> grpIn = new Vector<String>();
		Vector<String> locList = new Vector<String>();
		
		for(int x=0; x<numSpecies; x++) {
			int index = speciesPanel.getChromosomeIndex(x);

			//Species selected only, no chromosome
			if(index < 0) {
				int idx = speciesPanel.getSpeciesIndex(x);
				speciesIn.add(String.valueOf(idx));
			}
			else {
				grpIn.add(String.valueOf(index));
				String start = speciesPanel.getChromosomeStart(x);
				String stop = speciesPanel.getChromosomeStop(x);
				if(start.length() > 0 && stop.length() > 0) {
					String tempLeft = "PH.grp1_idx != " + index;
					String tempRight = "PH.grp2_idx != " + index;
					tempLeft  = "(" + combineBool(tempLeft,  "greatest(" + start + ", PH.start1) <= least(" + stop + ", PH.end1)", false) + ")";
					tempRight = "(" + combineBool(tempRight, "greatest(" + start + ", PH.start2) <= least(" + stop + ", PH.end2)", false) + ")";
					locList.add(tempLeft);
					locList.add(tempRight);
				}
			}
		}
		Vector<String> allClauses = new Vector<String>();
		String specInClause = null;
		String grpInClause = null;
		if (speciesIn.size() > 0)
		{
			specInClause = "(" + Utils.join(speciesIn, ",") + ")";
		}
		if (grpIn.size() > 0)
		{
			grpInClause = "(" + Utils.join(grpIn, ",") + ")";
		}
		String inClause = null;

		if (specInClause != null)
		{
			if (grpInClause != null)
			{
				inClause = " ( ";
				inClause += "((PH.proj1_idx IN " + specInClause + ") OR (PH.grp1_idx IN " + grpInClause + "))";
				inClause += " AND ";
				inClause += "((PH.proj2_idx IN " + specInClause + ") OR (PH.grp2_idx IN " + grpInClause + "))";
				inClause += " ) ";
			}
			else
			{
				inClause = " ( ";
				inClause += " (PH.proj1_idx IN " + specInClause + ") ";
				inClause += " AND ";
				inClause += "(PH.proj2_idx IN " + specInClause + ")";
				inClause += " ) ";				
			}
		}
		else
		{
			inClause = " ( ";
			inClause += " (PH.grp1_idx IN " + grpInClause + ") ";
			inClause += " AND ";
			inClause += "(PH.grp2_idx IN " + grpInClause + ")";
			inClause += " ) ";						
		}
		if (inClause != null) allClauses.add(inClause);
		
		String locClause = null;
		if (locList.size() > 0)
		{
			locClause = Utils.join(locList, " AND ");
		}
		if (locClause != null) allClauses.add(locClause);
		
		retVal = (allClauses.size() > 0 ? "(" + Utils.join(allClauses, " AND ") + ")" : "");
		return retVal;
	}
	// Chrom/Location part of the orphan query, exactly parallel to getPairGroupQueryAll()!!!
	private String getAnnotGroupQueryAll() {
		String retVal = "";
		int numSpecies = speciesPanel.getNumSpecies();
		
		Vector<String> speciesIn = new Vector<String>();
		Vector<String> grpIn = new Vector<String>();
		Vector<String> locList = new Vector<String>();
		
		for(int x=0; x<numSpecies; x++) {
			int index = speciesPanel.getChromosomeIndex(x);

			//Species selected only, no chromosome
			if(index < 0) {
				int idx = speciesPanel.getSpeciesIndex(x);
				speciesIn.add(String.valueOf(idx));
			}
			else {
				grpIn.add(String.valueOf(index));
				String start = speciesPanel.getChromosomeStart(x);
				String stop = speciesPanel.getChromosomeStop(x);
				if(start.length() > 0 && stop.length() > 0) {
					String tempLeft = "PA.grp_idx != " + index;
					tempLeft  = "(" + combineBool(tempLeft,  "greatest(" + start + ", PA.start) <= least(" + stop + ", PA.end)", false) + ")";
					locList.add(tempLeft);
				}
			}
		}
		Vector<String> allClauses = new Vector<String>();
		String specInClause = null;
		String grpInClause = null;
		if (speciesIn.size() > 0)
		{
			specInClause = "(" + Utils.join(speciesIn, ",") + ")";
		}
		if (grpIn.size() > 0)
		{
			grpInClause = "(" + Utils.join(grpIn, ",") + ")";
		}
		String inClause = null;

		if (specInClause != null)
		{
			if (grpInClause != null)
			{
				inClause = "((G1.proj_idx IN " + specInClause + ") OR (PA.grp_idx IN " + grpInClause + "))";
			}
			else
			{
				inClause = " (G1.proj_idx IN " + specInClause + ") ";
			}
		}
		else
		{
			inClause = " (PA.grp_idx IN " + grpInClause + ") ";
		}
		if (inClause != null) allClauses.add(inClause);
		
		String locClause = null;
		if (locList.size() > 0)
		{
			locClause = Utils.join(locList, " AND ");
		}
		if (locClause != null) allClauses.add(locClause);
		allClauses.add(" (PA.type='gene') ");
		retVal = (allClauses.size() > 0 ? "(" + Utils.join(allClauses, " AND ") + ")" : "");
		return retVal;
	}
		
	
	private static String combineBool(String left, String right, boolean isAND) {
		if(left.length() == 0) return right;
		if(right.length() == 0) return left;
		if(isAND) return left + " AND " + right;
		return left + " OR " + right;
	}
	/********************************************************************
	 * Filter panels
	 */
	private JPanel createFilterPanel() {
		JPanel retVal = new JPanel();
		retVal.setLayout(new BoxLayout(retVal, BoxLayout.PAGE_AXIS));
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setBackground(Color.WHITE);
		
		chkSynteny = new JCheckBox("Only synteny hits");		
		chkSynteny.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkSynteny.setBackground(Color.WHITE);
		
		chkCollinear = new JCheckBox("Only hits in a collinear gene pair");		
		chkCollinear.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkCollinear.setBackground(Color.WHITE);

		chkOrphan = new JCheckBox("Only orphan genes (See Help)");	// CAS503 was Show orphan genes	
		chkOrphan.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkOrphan.setBackground(Color.WHITE);
		chkOrphan.addActionListener(new ActionListener() { // CAS503 disabled others if orphans checked
			public void actionPerformed(ActionEvent e) {
				boolean b= chkOrphan.isSelected();
				if (b) { // unselect any selected
					chkSynteny.setSelected(false);
					chkCollinear.setSelected(false);
					chkUnannot.setSelected(false);
					chkClique.setSelected(false);
					chkCollinear.setSelected(false);
					chkIncludeOnly.setSelected(false);
				}
				b = !b;
				chkSynteny.setEnabled(b);
				chkCollinear.setEnabled(b);
				chkUnannot.setEnabled(b);
				chkClique.setEnabled(b);
				chkCollinear.setEnabled(b);
				chkIncludeOnly.setEnabled(b);
			}
		});
			
		retVal.add(chkSynteny);
		retVal.add(chkCollinear);
		retVal.add(chkOrphan);

		return retVal;
	}
	
	private JPanel createFilterGroupPanel() {		
		JPanel retVal = new JPanel();
		retVal.setLayout(new BoxLayout(retVal, BoxLayout.PAGE_AXIS));
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setBackground(Color.WHITE);

		Vector<Project> theProjects = theParentFrame.getProjects();
		
		speciesName = new String [theProjects.size()];
		incSpecies = new JCheckBox[theProjects.size()];
		for(int x=0; x<incSpecies.length; x++) {
			speciesName[x] = theProjects.get(x).getDisplayName();
			incSpecies[x] = new JCheckBox(speciesName[x]);
			incSpecies[x].setBackground(Color.WHITE);
		}
		exSpecies = new JCheckBox[theProjects.size()];
		for(int x=0; x<exSpecies.length; x++) {
			exSpecies[x] = new JCheckBox(speciesName[x]);
			exSpecies[x].setBackground(Color.WHITE);
		}		
		
		Vector<String> sortedGroups = new Vector<String> ();
		for(int x=0; x<theProjects.size(); x++) {
			if(!sortedGroups.contains(theProjects.get(x).getCategory())) 
				sortedGroups.add(theProjects.get(x).getCategory());
		}
		Collections.sort(sortedGroups);
		
		for(int grpIdx=0; grpIdx<sortedGroups.size(); grpIdx++) {
			String groupName = sortedGroups.get(grpIdx);
			boolean firstOne = true;

			JPanel tempRow = new JPanel();
			tempRow.setLayout(new BoxLayout(tempRow, BoxLayout.LINE_AXIS));
			tempRow.setBackground(Color.WHITE);
			tempRow.setAlignmentX(Component.LEFT_ALIGNMENT);
			
			tempRow.add(new JLabel("Include: "));
			for(int x=0; x<incSpecies.length; x++) {
				if(groupName.equals(theProjects.get(x).getCategory())) {
					if(firstOne) {
						retVal.add(Box.createVerticalStrut(10));
						if(sortedGroups.size() > 1)
							retVal.add(new JLabel(groupName));
						firstOne = false;
					}
					tempRow.add(incSpecies[x]);
					tempRow.add(Box.createHorizontalStrut(5));
				}
			}
			retVal.add(tempRow);
	
			tempRow = new JPanel();
			tempRow.setLayout(new BoxLayout(tempRow, BoxLayout.LINE_AXIS));
			tempRow.setBackground(Color.WHITE);
			tempRow.setAlignmentX(Component.LEFT_ALIGNMENT);
	
			tempRow.add(new JLabel("Exclude: "));
			for(int x=0; x<exSpecies.length; x++) {
				if(groupName.equals(theProjects.get(x).getCategory())) {
					if(firstOne) {
						retVal.add(Box.createVerticalStrut(10));
						if(sortedGroups.size() > 1)
							retVal.add(new JLabel(groupName));
						firstOne = false;
					}
					tempRow.add(exSpecies[x]);
					tempRow.add(Box.createHorizontalStrut(5));
				}
			}
			retVal.add(tempRow);
		}
		
		chkUnannot = new JCheckBox("No annotation of hits for the included species");	// CAS503 added 'for hit'
		chkClique = new JCheckBox("Complete linkage for the included species");		
		chkUnannot.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkUnannot.setBackground(Color.WHITE);
		chkClique.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkClique.setBackground(Color.WHITE);
		
		retVal.add(Box.createVerticalStrut(10));
		retVal.add(chkUnannot);
		retVal.add(chkClique);

		retVal.setMaximumSize(retVal.getPreferredSize());
		
		return retVal;
	}
	
	private JPanel createFilterHitPanel() {		
		JPanel retVal = new JPanel();
		retVal.setLayout(new BoxLayout(retVal, BoxLayout.PAGE_AXIS));
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setBackground(Color.WHITE);

		chkIncludeOnly = new JCheckBox("Show only hits to the included species");	
		chkIncludeOnly.setBackground(Color.WHITE);
		retVal.add(chkIncludeOnly);
		retVal.setMaximumSize(retVal.getPreferredSize());
		
		return retVal;
	}

	private JPanel createButtonPanel(int width) {
		JPanel retVal = new JPanel();
		retVal.setLayout(new BoxLayout(retVal, BoxLayout.LINE_AXIS));
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setBackground(Color.WHITE);
		
		JButton btnExpand = new JButton("Expand");
		btnExpand.setBackground(Color.white);
		btnExpand.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				pnlStepOne.expand();
				pnlStepTwo.expand();
				pnlStepThree.expand();
			}
		});
		JButton btnCollapse = new JButton("Collapse");
		btnCollapse.setBackground(Color.white);
		btnCollapse.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				pnlStepOne.collapse();
				pnlStepTwo.collapse();
				pnlStepThree.collapse();
			}
		});

		retVal.add(btnExpand);
		retVal.add(Box.createHorizontalStrut(3));
		retVal.add(btnCollapse);
		retVal.add(Box.createHorizontalStrut(70));
		
		btnExecute = new JButton("Search");
		btnExecute.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnExecute.setBackground(Color.WHITE);
		retVal.add(btnExecute);
		retVal.add(Box.createHorizontalStrut(70));

		btnHelp = new JButton("Help");
		btnHelp.setAlignmentX(Component.RIGHT_ALIGNMENT);
		btnHelp.setBackground(UserPrompt.PROMPT);
		
		btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				Utilities.showHTMLPage(null, "SyMAP Query Help", "/html/QueryHelp.html");
			}
		});
		retVal.add(btnHelp);
		
		retVal.setMaximumSize(retVal.getPreferredSize());
		return retVal;
	}
	

	private JPanel createStringSearchPanel() {
		JPanel retVal = new JPanel();
		retVal.setLayout(new BoxLayout(retVal, BoxLayout.PAGE_AXIS));
		retVal.setBackground(Color.WHITE);
		
		JPanel annoPanel = new JPanel();
		annoPanel.setLayout(new BoxLayout(annoPanel, BoxLayout.LINE_AXIS));
		annoPanel.setBackground(Color.WHITE);

		annoPanel.add(new JLabel("Annotation Description")); // CAS501 change text
		annoPanel.add(Box.createHorizontalStrut(10));
		txtAnnotation = new JTextField(30);
		annoPanel.add(txtAnnotation);
		
		annoPanel.setMaximumSize(annoPanel.getPreferredSize());
		annoPanel.setAlignmentX(LEFT_ALIGNMENT);
		
		retVal.add(annoPanel);
		
		return retVal;
	}	
	
	private void createSpeciesPanel() {
		speciesPanel = new SpeciesSelectPanel(theParentFrame);
	}
	
	private SyMAPQueryFrame theParentFrame = null;
	private CollapsiblePanel pnlStepOne = null, pnlStepTwo = null, pnlStepThree = null;
	
	private JTextField txtAnnotation = null; 
	
	public SpeciesSelectPanel speciesPanel = null;	
	private JCheckBox [] incSpecies = null, exSpecies = null;
	private String [] speciesName = null;
	
	private JCheckBox chkSynteny = null, chkIncludeOnly = null, chkOrphan = null,
			chkCollinear = null, chkUnannot = null, chkClique = null;
	
	private JButton btnExecute = null, btnHelp = null;
}
