package dotplot;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Component;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;

import colordialog.ColorDialogHandler;

import javax.swing.JComboBox;

import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ImageViewer;
import util.Jcomp;

/**********************************************************
 * The upper row for the DotPlot
 * CAS533 I removed implements Observer, and it worked as before; 
 * 		but after removing Observer from Data, had to add plot.repaint 
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ControlPanel extends JPanel implements HelpListener {
    private Data data; 		// changes to zoom, etc set in data
    private Plot plot; 		// for show image and repaint
    private Filter filter=null; 	// CAS533 add; was recreating every time 
    
    private JButton homeButton, minusButton, plusButton;
    private JButton filterButton, showImageButton, editColorsButton;
    private JButton helpButtonLg, helpButtonSm, statsButton;	// CAS551 add Info & Stats button to replace statsOpt pull-down
    private JCheckBox scaleCheckbox; 	
    private JLabel referenceLabel;      
    private JComboBox <Project> referenceSelector;
    private ColorDialogHandler cdh;
   
  
    protected ControlPanel(Data d, Plot p, HelpBar hb, ColorDialogHandler cdh) {
		this.data = d;
		this.plot = p;
		this.cdh = cdh;
		
		filter = new Filter(d, this);
	
		helpButtonLg = util.Jhtml.createHelpIconUserLg(util.Jhtml.dotplot);
		helpButtonSm= (JButton) Jcomp.createButton(this,"/images/info.png",
				"Quick Help Popup",hb,buttonListener,false, false);
		statsButton = (JButton) Jcomp.createButton(this,"/images/s.png",
				"Stats Popup",hb,buttonListener,false, false);
		
		homeButton       = (JButton)  Jcomp.createButton(this,"/images/home.gif",
				"Home: Go back to full view.",hb,buttonListener,false, data.isTileView());
		filterButton     = (JButton)  Jcomp.createButton(this,"Filters",
				"Filters: Change filter settings.",hb,buttonListener,false, false);
		minusButton      = (JButton)  Jcomp.createButton(this,"/images/minus.gif",
				"Shrink: Decrease the scale.",hb,buttonListener,false, false);
		plusButton       = (JButton)  Jcomp.createButton(this,"/images/plus.gif",
				"Grow: Increase the scale.",hb,buttonListener,false, false);
		showImageButton  = (JButton)  Jcomp.createButton(this,"/images/print.gif",
				"Save: Save as image.", hb,buttonListener,false, false);
		scaleCheckbox = (JCheckBox) Jcomp.createButton(this,"Scale",
				"Scale: Draw to BP scale.",hb,buttonListener,true, false); 
		scaleCheckbox.setBackground(getBackground()); 
		editColorsButton = (JButton) Jcomp.createButton(this,"/images/colorchooser.gif",
				"Colors: Edit the color settings",hb,buttonListener,false, false);
		
		referenceLabel = new JLabel("Reference:");
		referenceSelector = new JComboBox <Project> ();
		referenceSelector.addActionListener(buttonListener);
		referenceSelector.setName("Reference: Change reference (x-axis) project.");
		referenceSelector.setToolTipText("Reference: Change reference (x-axis) project.");
		
		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		setLayout(gbl);
		gbc.fill = 2;
		gbc.gridheight = 1;
		gbc.ipadx = 5;
		gbc.ipady = 8;
		
		// 
		addToGrid(gbl,gbc,homeButton,1,1);		addToGrid(gbl,gbc,minusButton,1,0);	addToGrid(gbl,gbc,plusButton,1,1);
		addToGrid(gbl,gbc,scaleCheckbox,1,1); 	
		addToGrid(gbl,gbc,referenceLabel,1,0);	addToGrid(gbl,gbc,referenceSelector,1,1); 
	 	
		addToGrid(gbl,gbc,filterButton,1,1);
		addToGrid(gbl,gbc,editColorsButton,1,1); 
		addToGrid(gbl,gbc,showImageButton,1,1);
		addToGrid(gbl,gbc,helpButtonLg,1,1);
		addToGrid(gbl,gbc,helpButtonSm,1,1);
		addToGrid(gbl,gbc,statsButton,GridBagConstraints.REMAINDER,0);
    }
    protected void kill() {filter.setVisible(false);} // DotPlotFrame on shutdown; only needed if !modal
    protected void update() {plot.repaint();} // Filter change
    
    private ActionListener buttonListener = new ActionListener() {
	    public void actionPerformed(ActionEvent evt) {
			Object src = evt.getSource();
			if (src == filterButton) {
				filter.showX();
			}
			else {
			    if (src == homeButton) data.setHome(); // CAS533 remove && data.getNumVisibleGroups() > 2
			    else if (src == minusButton)      data.factorZoom(0.95);
			    else if (src == plusButton)       data.factorZoom(1/0.95);
			    else if (src == showImageButton)  ImageViewer.showImage("DotPlot", plot); // CAS532 data.getSyMAP().getImageViewer().showImage(plot);
			    else if (src == editColorsButton) cdh.showX();
			    else if (src == scaleCheckbox) { 
			    	if (scaleCheckbox.isSelected())
			    		data.setScale(true);
			    	else {
			    		data.setZoom(Data.DEFAULT_ZOOM); 
			    		data.setScale(false); 
			    	}
			    }
			    else if (src == referenceSelector) {
			    	data.setReference((Project)referenceSelector.getSelectedItem());
			    }
			    else if (src == helpButtonSm) popupHelp();
			    else if (src == statsButton) popupStats();
			    plot.repaint();
			}
	    }
	};
    private void addToGrid(GridBagLayout gbl, GridBagConstraints gbc, Component comp, int i, int sep) {
		gbc.gridwidth = i;
		gbl.setConstraints(comp,gbc);
		add(comp);
		if (sep > 0) addToGrid(gbl,gbc,new JLabel(),1,0);
		if (sep > 1) addToGrid(gbl,gbc,new JSeparator(JSeparator.VERTICAL),1,0);
    }
    
	public String getHelpText(MouseEvent event) {  // symap.frame.Helpbar
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
	
	protected void setProjects(Project[] projects) { // DotPlotFrame
		if (projects == null) {
			referenceLabel.setVisible(false);
			referenceSelector.setVisible(false);
			return;
		}
		
		for (Project p : projects)
			referenceSelector.addItem(p);
		
		// Disable if self-alignment
		if (projects.length == 2 && projects[0].getID() == projects[1].getID())
			referenceSelector.setEnabled(false);
	}
	private void popupHelp() {
		String msg = 
				"If multiple chr-by-chr cells are shown, click on a cell to view the cell only.\n\n";
		msg +=  "For the chr-by-chr cell view only:"
				+ "\n   Double-click on a synteny block (boundary must be showing), "
				+ "\n   or create a region by dragging the mouse and double-click on it."
				+ "\nThe first click will turn the region beige, the second click will bring up"
				+ "\nthe 2D display of the region.";
		util.Utilities.displayInfoMonoSpace(this, "Quick Help", msg, false);
	}
	private void popupStats() {
		String msg = plot.getStats();
		util.Utilities.displayInfoMonoSpace(this, "Dot plot stats", msg, false);
	}
}
