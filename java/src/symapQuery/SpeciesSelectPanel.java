package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collections;
import java.util.Iterator;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import symap.projectmanager.common.Project;

public class SpeciesSelectPanel extends JPanel {
	private static final long serialVersionUID = -6558974015998509926L;
	private static final int REFERENCE_SELECT_WIDTH = 60;
	private static final int SPECIES_NAME_WIDTH = 150;

	public SpeciesSelectPanel(SyMAPQueryFrame parentFrame) {
		theParentFrame = parentFrame;
		speciesPanels = new Vector<SpeciesSelect> ();
		setBackground(Color.WHITE);
		
		setSpeciesData();
		createInitialPanels();
		refreshPanels();
	}
	
	public int getNumSpecies() {
		return speciesPanels.size();
	}
	
	public int getSpeciesIndex(int panel) {
		return theSpeciesIndicies[panel];
	}
	
	public String getSpecies(int panel) {
		return speciesPanels.get(panel).getSpeciesName();
	}
	
	public int getChromosomeIndex(int panel) {
		String species = speciesPanels.get(panel).getSpeciesName();
		String chrom = speciesPanels.get(panel).getSelectedChromosome();
		
		int index = findIndexOf(species, theSpecies);
		if(chrom.equals("All")) {
			return -1;
		} else {
			int chIndex = findIndexOf(chrom, theChromosomes[index].split(","));
			return Integer.parseInt(theChromosomeIndicies[index].split(",")[chIndex]);
		}
	}
	
	public String getChromosome(int panel) {
		return speciesPanels.get(panel).getSelectedChromosome();
	}
	
	public String getChromosomeStart(int panel) {
		return speciesPanels.get(panel).getStartRange();
	}
	
	public String getChromosomeStop(int panel) {
		return speciesPanels.get(panel).getStopRange();
	}
	
	private static int findIndexOf(String val, String [] values) {
		for(int x=0; x<values.length; x++)
			if(values[x].trim().equals(val.trim()))
				return x;
		return -1;
	}
	
	
	private void createInitialPanels() {
		for(int x=0; x<theSpecies.length; x++) {
			lblInclude = new JLabel("Include");
			lblInclude.setAlignmentX(Component.CENTER_ALIGNMENT);
			lblExclude = new JLabel("Exclude");
			lblExclude.setAlignmentX(Component.CENTER_ALIGNMENT);

			SpeciesSelect temp = new SpeciesSelect(this, lblInclude.getPreferredSize().width);
			temp.setSpecies(theSpecies[x]);
			temp.setGroup(theGroups[x]);
			temp.setChromosomes(getChromosomeListForSpecies(temp.getSpeciesName()));
			speciesPanels.add(temp);
		}		
	}
	
	private String [] getChromosomeListForSpecies(String species) {
		for(int x=0; x<theSpecies.length; x++) {
			if(species.equals(theSpecies[x])) {
				return theChromosomes[x].split(",");
			}
		}
		return null;
	}
	
	private void refreshPanels() {
		if(speciesPanels == null || speciesPanels.size() == 0) return;
		removeAll();
		
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		add(Box.createVerticalStrut(10));
		JPanel labelPanel = new JPanel();
		labelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.LINE_AXIS));
		labelPanel.setBackground(Color.WHITE);
		
		JPanel includePanel = new JPanel();
		includePanel.setLayout(new BoxLayout(includePanel, BoxLayout.LINE_AXIS));
		includePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		includePanel.setBackground(Color.WHITE);
		
//		includePanel.add(lblInclude);
		
		JPanel excludePanel = new JPanel();
		excludePanel.setLayout(new BoxLayout(excludePanel, BoxLayout.LINE_AXIS));
		excludePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		excludePanel.setBackground(Color.WHITE);
		
//		excludePanel.add(lblExclude);
		
		Dimension dim = includePanel.getPreferredSize();
		dim.width = REFERENCE_SELECT_WIDTH;
		includePanel.setMinimumSize(dim);
		includePanel.setMaximumSize(dim);
		
		dim = excludePanel.getPreferredSize();
		dim.width = REFERENCE_SELECT_WIDTH;
		excludePanel.setMinimumSize(dim);
		excludePanel.setMaximumSize(dim);
		
		labelPanel.add(includePanel);
		labelPanel.add(Box.createHorizontalStrut(5));
		labelPanel.add(excludePanel);
		labelPanel.setMaximumSize(labelPanel.getPreferredSize());
		
		add(labelPanel);
		
		//Adjust the chromosome select controls
		Iterator<SpeciesSelect> iter = speciesPanels.iterator();
		Dimension maxSize = new Dimension(0,0);
		while(iter.hasNext()) {
			Dimension tempD = iter.next().getChromSize();
			if(tempD.width > maxSize.width)
				maxSize = tempD;
		}

		for(int x=0; x<speciesPanels.size(); x++) {
			SpeciesSelect temp = speciesPanels.get(x);
			temp.setChromSize(maxSize);
			speciesPanels.set(x, temp);
		}

		Vector<String> sortedGroups = new Vector<String> ();
		for(int x=0; x<theGroups.length; x++) {
			if(!sortedGroups.contains(theGroups[x])) 
				sortedGroups.add(theGroups[x]);
		}
		Collections.sort(sortedGroups);
		

		for(int x=0; x<sortedGroups.size(); x++) {
			String groupName = sortedGroups.get(x);
			boolean firstOne = true;
			iter = speciesPanels.iterator();
			while(iter.hasNext()) {
				SpeciesSelect temp = iter.next();
	
				if(groupName.equals(temp.getGroup())) {
					if(firstOne) {
						if(sortedGroups.size() > 1)
							add(new JLabel(groupName));
						firstOne = false;
					}
					JPanel row = new JPanel();
					row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
					row.setAlignmentX(Component.LEFT_ALIGNMENT);
					row.setBackground(Color.WHITE);
					
					temp.setChromSize(maxSize);
					row.add(temp);
					
					add(row);
					add(Box.createVerticalStrut(10));
				}
			}
		}
		setAlignmentX(Component.LEFT_ALIGNMENT);
		revalidate();
	}
	
	private void setSpeciesData() {
		try {
			Vector<Project> theProjects = theParentFrame.getProjects();
			Iterator<Project> iter = theProjects.iterator();
			
			Connection conn = theParentFrame.getDatabase().getConnection();
			
			Statement stmt = conn.createStatement();
			
			theSpecies = new String[theProjects.size()];
			theGroups = new String[theProjects.size()];
			theSpeciesIndicies = new int[theProjects.size()];
			theChromosomes = new String[theProjects.size()];
			theChromosomeIndicies = new String[theProjects.size()];
			
			for(int x=0; x<theSpecies.length; x++) {
				Project temp = iter.next();
				theSpecies[x] = temp.getDisplayName();
				theSpeciesIndicies[x] = temp.getID();
				theGroups[x] = temp.getCategory();
				theChromosomes[x] = "";
				theChromosomeIndicies[x] = "";
				
				String strQ = "SELECT groups.name, groups.idx FROM groups ";
				strQ += "JOIN projects ON groups.proj_idx = projects.idx ";
				strQ += "WHERE projects.name = '" + temp.getDBName() + "' ";
				strQ += "ORDER BY groups.sort_order ASC";
				
				ResultSet rset = stmt.executeQuery(strQ);
				if(rset.next()) {
					theChromosomes[x] = rset.getString("groups.name");
					theChromosomeIndicies[x] = rset.getString("groups.idx");
					while(rset.next()) {
						theChromosomes[x] += "," + rset.getString("groups.name");
						theChromosomeIndicies[x] += "," + rset.getString("groups.idx");
					}
				}
			}
			stmt.close();
			conn.close();
		} 
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
//	private Vector<SpeciesSelect> orderByGroup(Vector<SpeciesSelect> species) {
////		Vector<Vector<SpeciesSelect>> groups = new Vector<Vector<SpeciesSelect>> ();
//		Vector<String>
//	}
	
	private SyMAPQueryFrame theParentFrame = null;
	private Vector<SpeciesSelect> speciesPanels = null;
	
	private String [] theSpecies = null;
	private String [] theGroups = null;
	private int [] theSpeciesIndicies = null;
	private String [] theChromosomes = null;
	public String [] theChromosomeIndicies = null;
	
	private JLabel lblInclude = null;
	private JLabel lblExclude = null;
	
	private class SpeciesSelect extends JPanel {
		private static final long serialVersionUID = 2963964322257904265L;

		public SpeciesSelect(SpeciesSelectPanel parent, int checkBoxWidth) {
			theParent = parent;
			nCheckBoxWidth = checkBoxWidth;
			controlPanel = new JPanel();
						
			lblSpecies = new JLabel("");
			lblSpecies.setBackground(Color.WHITE);
			lblChrom = new JLabel("Chrom: ");
			cmbChroms = new JComboBox();
			cmbChroms.setBackground(Color.WHITE);
			cmbChroms.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent arg0) {
					boolean enableRange = !(cmbChroms.getSelectedItem().equals("All"));
					txtStart.setEnabled(enableRange);
					txtStop.setEnabled(enableRange);
					cmbScale.setEnabled(enableRange);
					lblStart.setEnabled(enableRange);
					lblStop.setEnabled(enableRange);
				}
			});
	
			lblStart = new JLabel("From");
			lblStart.setBackground(Color.WHITE);
			lblStart.setEnabled(false);
			txtStart = new JTextField(10);
			txtStart.setBackground(Color.WHITE);
			txtStart.setEnabled(false);
			lblStop = new JLabel("To");
			lblStop.setBackground(Color.WHITE);
			lblStop.setEnabled(false);
			txtStop = new JTextField(10);
			txtStop.setBackground(Color.WHITE);
			txtStop.setEnabled(false);
			cmbScale = new JComboBox();
			cmbScale.setBackground(Color.WHITE);
			cmbScale.addItem("bp");
			cmbScale.addItem("kb");
			cmbScale.addItem("mb");
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
			lblSpecies.setPreferredSize(d);
			lblSpecies.setMinimumSize(d);
			controlPanel.add(lblChrom);
			controlPanel.add(cmbChroms);
			controlPanel.add(Box.createHorizontalStrut(5));
			controlPanel.add(lblStart);
			controlPanel.add(txtStart);
			controlPanel.add(lblStop);
			controlPanel.add(txtStop);				
			controlPanel.add(cmbScale);
			
			add(controlPanel);
			add(Box.createVerticalStrut(5));

			theParent.refreshPanels();
		}
				
		public String getSpeciesName() {
			return lblSpecies.getText();
		}
		
		public String getStartRange() {
			try {
				long temp = Long.parseLong(txtStart.getText());
				if(temp < 0) return "";
				if (temp == 0) return "0";
				return temp + getScaleDigits();
			} catch(NumberFormatException e) {
				return "";
			}
		}
		
		public String getStopRange() {
			try {
				long temp = Long.parseLong(txtStop.getText());
				if(temp <= 0) return "";
				return temp + getScaleDigits();
			} catch(NumberFormatException e) {
				return "";
			}
		}
		
		private String getScaleDigits() {
			if(cmbScale.getSelectedIndex() == 1) return "000";
			if(cmbScale.getSelectedIndex() == 2) return "000000";
			return "";
		}
		
		public String getSelectedChromosome() {
			return (String)cmbChroms.getSelectedItem();
		}
		
		public void setSpecies(String species) {
			lblSpecies.setText(species);
		}
		
		public String getGroup() { return strGroup; }
		public void setGroup(String group) { strGroup = group; }
		
		public void setChromosomes(String [] names) {
			//Need to disable any listeners while we populate
			ActionListener [] listeners = (ActionListener [])cmbChroms.getListeners(ActionListener.class);
			if(listeners != null && listeners.length > 0)
				for(int x=0; x<listeners.length; x++)
					cmbChroms.removeActionListener(listeners[x]);
			
			cmbChroms.removeAllItems();
			cmbChroms.addItem("All");
			for(int x=0; x<names.length; x++)
				cmbChroms.addItem(names[x]);
			
			if(listeners != null && listeners.length > 0)
				for(int x=0; x<listeners.length; x++)
					cmbChroms.addActionListener(listeners[x]);
			
			cmbChroms.setSelectedIndex(0);
		}
		
		public void adjustSize() {
			setMaximumSize(getPreferredSize());
		}
		
		public void setChromSize(Dimension d) {
			cmbChroms.setPreferredSize(d);
			cmbChroms.setMaximumSize(d);
			cmbChroms.setMinimumSize(d);
		}
		
		public Dimension getChromSize() { return cmbChroms.getPreferredSize(); }
		
		private int nCheckBoxWidth = -1;
		private JLabel lblSpecies = null;
		private JComboBox cmbChroms = null;
		private JTextField txtStart = null;
		private JTextField txtStop = null;
		private JComboBox cmbScale = null;
		private JLabel lblStart = null;
		private JLabel lblStop = null;
		private JLabel lblChrom = null;
		private String strGroup = "";

		private JPanel controlPanel = null;
		
		private SpeciesSelectPanel theParent = null;
	}
}
