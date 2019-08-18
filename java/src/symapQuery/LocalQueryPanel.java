package symapQuery;

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
import util.Utilities;

import java.util.Collections;
import java.util.Vector;

import backend.Utils;

public class LocalQueryPanel extends JPanel {
	private static final long serialVersionUID = 2804096298674364175L;
	
	private static final String [] HELP_COL = {
		"General","","",
		"","","","","","","","","",
		"1. Annotation String search:", 
		"2. Include/Exclude",
		"3. Only synteny hits",
		"4. Show only hits to","  included species", 
		"5. Show orhpan genes","",
		"6. Show only hits in a ","   collinear gene pair",
		"7. Show only groups having","   no annotation to","   included species",
		"8. Require complete interlinking","   of included species",
	};
	
	private static final String [] HELP_VAL = {
		"The query returns hits (anchors) between genomes.",
		"The hits are grouped into cross-species groups  ",
		"based on sequence overlap.",
		"Filters 1,3,6 filter the hits before grouping.",
		"Filter 4 filters hits after grouping, i.e. groups",
		"are built, but not all their hits are shown.",
		"Filters 2,7,8 filter groups as a whole, i.e. no",
		"hits in the group are shown if group does not pass.",
		"Filter 5 causes single-species annotations to be shown",
		"in addition to hits.",
		"",
		"Filters:",
		"Only use hits annotated with the given keyword",
		"Only show groups that include/do not include this species",
		"Only use hits which are in synteny blocks",
		"Only show hits involving at least one of the included",
		"   species (note that groups are formed before this filter)",
		"Show annotations which do not overlap a hit in the result",
		"   list. Keyword filter is applied to annotations" ,
		"Only use hits which are part of a collinear set of ",
		"   two or more homologous genes",
		"Only show groups for which no hit has an annotation",
		"   to an included species",
		"",
		"Only show groups for which all included species pairs are",
		"   linked by hits",
	};

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
	
	public String getSubQuery() {
		String retVal = "";

		//Species selection
		int numSpecies = speciesPanel.getNumSpecies();
		if(numSpecies > 0) {
//			retVal = combineBool(retVal, getRefGroupQuery(), true);
			retVal = combineBool(retVal, getAnnoLocalFilter(), true);
			if (isNonself()) retVal = combineBool(retVal, " PH.proj1_idx != PH.proj2_idx ", true);
			retVal = combineBool(retVal, getPairGroupQuery(), true);
		}
		return retVal; 
	}
	public String getAnnotSubQuery() {
		String retVal = "";

		//Species selection
		int numSpecies = speciesPanel.getNumSpecies();
		if(numSpecies > 0) {
//			retVal = combineBool(retVal, getRefGroupQuery(), true);
			retVal = combineBool(retVal, getAnnoLocalFilter(), true);
			retVal = combineBool(retVal, getAnnotGroupQueryAll(), true);
		}
		return retVal; 
	}	
	public String getSubQuerySummary() { 
		String retVal = "", temp = "";
		
		//Species selection
		int numSpecies = speciesPanel.getNumSpecies();
		if(numSpecies > 0) {
//			temp = getRefGroupQuerySummary();
//			if(temp.length() > 0)
//				retVal += "[" + temp + "]";
			temp = getAnnoLocalFilterSummary();
			if(temp.length() > 0)
				retVal += "[" + temp + "]";
			temp = getPairGroupQuerySummary();
			if(temp.length() > 0)
				retVal += "[" + temp + "]";
			if(isSynteny())
				retVal += "[Only synteny hits]";
		}
		return retVal; 
	}
	
	public boolean isSynteny() { return chkSynteny.isSelected(); }
	public boolean isCollinear() { return chkCollinear.isSelected(); }
	public boolean isClique() { return chkClique.isSelected(); }
	public boolean isUnannot() { return chkUnannot.isSelected(); }
	public boolean isOnlyIncluded() { return chkIncludeOnly.isSelected(); }
	public boolean isOrphan() { return chkOrphan.isSelected(); }
	public boolean isNonself() { return true; } //chkNonself.isSelected(); }
	
	private String getAnnoLocalFilter() {
		return getAnnotationNameFilter();
	}
	
	private String getAnnoLocalFilterSummary() {
		return getAnnotationNameFilterSummary();
	}
	
	private String getAnnotationNameFilter() {
		String text = txtAnnotation.getText();
		if(text.length() == 0) return "";
		return "PA.name LIKE '%" + text + "%'";
	}
	
	private String getAnnotationNameFilterSummary() {
		String text = txtAnnotation.getText();
		if(text.length() == 0) return "";
		return "Annotation contains: " + text;
	}
	
//	private String getRefGroupQuery() {
		//If searching all, no reference in the query
//		if(btnAnnoAll.isSelected()) return "";
//		int index =speciesPanel.getChromosomeIndex(speciesPanel.getReferenceIndex());
		
//		if(index < 0)
//		{
//			int idx = speciesPanel.getSpeciesIndex(speciesPanel.getReferenceIndex());
//			return " (PH.proj1_idx = " + idx + " OR PH.proj2_idx = " + idx + ") ";
//		}
//		return " (PH.grp1_idx = " + index + " OR PH.grp2_idx = " + index + ") ";
//	}
/*	private String getRefGroupQuery2() {
		//If searching all, no reference in the query
		if(btnAnnoAll.isSelected()) return "";
		int index =speciesPanel.getChromosomeIndex(speciesPanel.getReferenceIndex());
		
		if(index < 0)
			return "G.proj_idx = " + speciesPanel.getSpeciesIndex(speciesPanel.getReferenceIndex());
		return "G.idx = " + index;
	}	*/
//	private String getRefGroupQuerySummary() {
//		String retVal = "Reference: ";
//		if(btnAnnoAll.isSelected()) return "";
//		String theChrom = speciesPanel.getChromosome(speciesPanel.getReferenceIndex());
//		String theSpecies = speciesPanel.getSpecies(speciesPanel.getReferenceIndex());
//		
//		retVal += "Species: " + theSpecies;
//		if(theChrom.length() > 0)
//			retVal += ", Chr: " + theChrom;
//		return retVal;
//	}
	
	private String getPairGroupQuery() {
		return getPairGroupQueryAll();
	}
	
	private String getPairGroupQuerySummary() {
//		if(btnAnnoRef.isSelected())
//			return getPairGroupQueryRefSummary();
		return getPairGroupQueryAllSummary();
	}
	// If changed, must change getAnnotGroupQueryAll()!!!
	private String getPairGroupQueryAll() {
		String retVal = "";
		int numSpecies = speciesPanel.getNumSpecies();
		
		Vector<String> speciesIn = new Vector<String>();
		Vector<String> grpIn = new Vector<String>();
		Vector<String> locList = new Vector<String>();
//		Vector<String> locList2 = new Vector<String>();
//		Vector<String> clauses = new Vector<String>();
		
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
//		Vector<String> locList2 = new Vector<String>();
//		Vector<String> clauses = new Vector<String>();
		
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
		
	private String getPairGroupQueryAllSummary() {
		int numSpecies = speciesPanel.getNumSpecies();

		String retVal = "Paired with ";
		//If by reference, only need to search all other species
		for(int x=0; x<numSpecies; x++) {
			String species = speciesPanel.getSpecies(x);
			String chroms = speciesPanel.getChromosome(x);
			if(!chroms.equals("All"))
				retVal += "(Species: " + species + ", Chr: " + chroms + ")";
			else
				retVal += "(Species: " + species + ")";
		}		
		return retVal;

	}
	
	private String getPairGroupQueryRefSummary() {
		int numSpecies = speciesPanel.getNumSpecies();

		String retVal = "Paired with ";
		//If by reference, only need to search all other species
		for(int x=1; x<numSpecies; x++) {
			String species = speciesPanel.getSpecies(x);
			String chroms = speciesPanel.getChromosome(x);
			if(!chroms.equals("All"))
				retVal += "(Species: " + species + ", Chr: " + chroms + ")";
			else
				retVal += "(Species: " + species + ")";
		}		
		return retVal;
	}

	private static String combineBool(String left, String right, boolean isAND) {
		if(left.length() == 0) return right;
		if(right.length() == 0) return left;
		if(isAND) return left + " AND " + right;
		return left + " OR " + right;
	}
	
	public void addExecuteListener(ActionListener l) {
		btnExecute.addActionListener(l);
	}
	
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

		chkOrphan = new JCheckBox("Show orphan genes");		
		chkOrphan.setAlignmentX(Component.LEFT_ALIGNMENT);
		chkOrphan.setBackground(Color.WHITE);

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
		
		incSpecies = new JCheckBox[theProjects.size()];
		for(int x=0; x<incSpecies.length; x++) {
			incSpecies[x] = new JCheckBox(theProjects.get(x).getDisplayName());
			incSpecies[x].setBackground(Color.WHITE);
		}
		exSpecies = new JCheckBox[theProjects.size()];
		for(int x=0; x<exSpecies.length; x++) {
			exSpecies[x] = new JCheckBox(theProjects.get(x).getDisplayName());
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
		
		chkUnannot = new JCheckBox("No annotation to included species");		
		chkClique = new JCheckBox("Complete linkage of included species");		
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

	public boolean isInclude(int species) {
		return incSpecies[species].isSelected();
	}
	
	public boolean isExclude(int species) {
		return exSpecies[species].isSelected();
	}
	
	private JPanel createButtonPanel(int width) {
		int totalWidth = 0;
		JPanel retVal = new JPanel();
		retVal.setLayout(new BoxLayout(retVal, BoxLayout.LINE_AXIS));
		retVal.setAlignmentX(Component.LEFT_ALIGNMENT);
		retVal.setBackground(Color.WHITE);
		
		JButton btnExpand = new JButton("Expand All");
		btnExpand.setBackground(Color.white);
		btnExpand.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				pnlStepOne.expand();
				pnlStepTwo.expand();
				pnlStepThree.expand();
			}
		});
		
		JButton btnCollapse = new JButton("Collapse All");
		btnCollapse.setBackground(Color.white);
		btnCollapse.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				pnlStepOne.collapse();
				pnlStepTwo.collapse();
				pnlStepThree.collapse();
			}
		});

		retVal.add(btnExpand);
		retVal.add(Box.createHorizontalStrut(5));
		retVal.add(btnCollapse);
		retVal.add(Box.createHorizontalStrut(30));
		btnExecute = new JButton("Do Search");
		btnExecute.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnExecute.setBackground(Color.WHITE);
		retVal.add(btnExecute);
		totalWidth += btnExecute.getPreferredSize().width;
		
		btnHelp = new JButton("Help");
		btnHelp.setAlignmentX(Component.RIGHT_ALIGNMENT);
		btnHelp.setBackground(UserPrompt.PROMPT);
		totalWidth += btnHelp.getPreferredSize().width;
		retVal.add(Box.createHorizontalStrut(30));
		btnHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
//				UserPrompt.displayInfo("Query Help", HELP_TEXT);
				Utilities.showHTMLPage(null, "SyMAP Query Help", "/html/QueryHelp.html");
				//UserPrompt.displayInfo(theParentFrame, "Query Help", HELP_COL, HELP_VAL, true);
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

		annoPanel.add(new JLabel("Annotation String Search"));
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
	//Search type controls
	private JTextField txtAnnotation = null;
	//Species 
	public SpeciesSelectPanel speciesPanel = null;	
	//Species Include/Exclude
	private JCheckBox [] incSpecies = null;
	private JCheckBox [] exSpecies = null;
	
	private CollapsiblePanel pnlStepOne = null, pnlStepTwo = null, pnlStepThree = null;
	//Other
	private JCheckBox chkSynteny = null, chkIncludeOnly = null, chkOrphan = null,
			chkCollinear = null, chkUnannot = null, chkClique = null;
	private JButton btnExecute = null;
	private JButton btnHelp = null;
}
