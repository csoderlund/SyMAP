package dotplot;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.JComboBox;

import colordialog.ColorDialogHandler;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ImageViewer;
import util.Jcomp;
import util.Utilities;
import util.Popup;

/**********************************************************
 * The Control Panel for the DotPlot (top row)
 */
public class ControlPanel extends JPanel implements HelpListener { 
	private static final long serialVersionUID = 1L;
	private Data data; 		// changes to zoom, etc set in data
    private Plot plot; 		// for show image and repaint
    private Filter filter=null; 								
  
    private JButton homeButton, minusButton, plusButton;
    private JTextField numField;	
    private JButton filterButton, showImageButton, editColorsButton;
    private JButton helpButtonLg, helpButtonSm, statsButton;	
    private JButton scaleToggle; 							 
    private JComboBox <Project> referenceSelector;
    private ColorDialogHandler cdh;
   
    protected ControlPanel(Data d, Plot p, HelpBar hb, ColorDialogHandler cdh) {
		this.data = d;
		this.plot = p;
		this.cdh = cdh;
		
		homeButton  = Jcomp.createIconButton(this, hb, buttonListener,
				"/images/home.gif", "Reset to full view with default size");
		minusButton =  Jcomp.createIconButton(this, hb, buttonListener,
				"/images/minus.gif","Decrease the size of the dotplot");
		plusButton  =  Jcomp.createIconButton(this, hb, buttonListener,
				"/images/plus.gif", "Increase the size of the dotplot");
		numField = Jcomp.createTextField("1",  
				"Amount to increase/decrease +/-; Min 1, Max 10", 2);
		scaleToggle  =  Jcomp.createBorderIconButton(this, hb, buttonListener,
				"/images/scale.gif", "Draw to BP scale."); 
		
		referenceSelector = new JComboBox <Project> ();
		referenceSelector.addActionListener(buttonListener);
		referenceSelector.setToolTipText("Change reference (x-axis) project");
		referenceSelector.setName("Change reference (x-axis) project");	
		hb.addHelpListener(referenceSelector, this);
		
		filter = new Filter(d, this);
		filterButton     = Jcomp.createButtonGray(this,hb, buttonListener, 
				"Filters", "Filters and display options");
	
		showImageButton  =  Jcomp.createIconButton(this, hb, buttonListener,
				"/images/print.gif", "Save as image");
		editColorsButton = Jcomp.createIconButton(this, hb, buttonListener,
				"/images/colorchooser.gif", "Edit the color settings");
		helpButtonLg     = util.Jhtml.createHelpIconUserLg(util.Jhtml.dotplot);
		helpButtonSm     = Jcomp.createIconButton(this, hb, buttonListener,
				"/images/info.png", "Quick Help Popup");
		statsButton = (JButton) Jcomp.createIconButton(this, hb, buttonListener,
				"/images/s.png", "Stats Popup");
		
		JPanel row = Jcomp.createRowPanelGray(); 
		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		setLayout(gbl);
		gbc.fill = GridBagConstraints.HORIZONTAL;;
		gbc.gridheight = 1;
		gbc.ipadx = gbc.ipady = 0;
		
		String sp1=" ", sp2="  ";
		addToGrid(row, gbl,gbc,homeButton,sp1);	
		addToGrid(row, gbl,gbc,plusButton, sp1);
		addToGrid(row, gbl,gbc,minusButton,sp1);	
		addToGrid(row, gbl,gbc,numField,sp1);	
		addToGrid(row, gbl,gbc,scaleToggle,sp2); 
		addToGrid(row, gbl, gbc, new JSeparator(JSeparator.VERTICAL), sp1); // End home functions
		
		addToGrid(row, gbl,gbc,referenceSelector,sp1); 
		addToGrid(row, gbl,gbc,filterButton,sp1);
		addToGrid(row, gbl,gbc,editColorsButton,sp2); 
		addToGrid(row, gbl, gbc, new JSeparator(JSeparator.VERTICAL), sp1); // End change display
		
		addToGrid(row, gbl,gbc,showImageButton,sp1);
		addToGrid(row, gbl,gbc,helpButtonLg,sp1);
		addToGrid(row, gbl,gbc,helpButtonSm,sp1);
		addToGrid(row, gbl,gbc,statsButton,sp1);
		add(row);
		
		setEnable();
    }
    private void addToGrid(JPanel cp, GridBagLayout gbl, GridBagConstraints gbc, Component comp, String blank) {
		gbl.setConstraints(comp,gbc);
		cp.add(comp);
		if (blank.length()>0) addToGrid(cp, gbl,gbc,new JLabel(blank), "");
    }
   
    private ActionListener buttonListener = new ActionListener() {
	    public void actionPerformed(ActionEvent evt) {
			Object src = evt.getSource();
			if (src == filterButton) {
				filter.showX();
			}
			else {
				boolean rp = false;
			    if (src == homeButton) 		{data.setHome(); rp=true;} 
			    else if (src == minusButton)  {
			    	data.factorZoom(getNumVal()); 
			    	plot.bPlusMinusScroll=true;
			    	rp=true;
			    }
			    else if (src == plusButton)      {
			    	data.factorZoom(1/getNumVal()); 
			    	plot.bPlusMinusScroll=true;
			    	rp=true;
			    }
			    else if (src == scaleToggle) 	  {
			    	data.bIsScaled = !data.bIsScaled;
			    	scaleToggle.setBackground(Jcomp.getBorderColor(data.bIsScaled));
			    	rp=true;
			    }
			    else if (src == referenceSelector) {
			    	data.setReference((Project)referenceSelector.getSelectedItem());
			    	rp=true;
			    }
			    else if (src == editColorsButton) {
			    	cdh.showX();
			    	rp=true;
			    }
			    else if (src == showImageButton)  ImageViewer.showImage("DotPlot", plot); 
			    else if (src == helpButtonSm) 	  popupHelp();
			    else if (src == statsButton)  	  popupStats();
			    
			    if (rp==true) plot.repaint();	
			}
			setEnable();
	    }
	};
	private double getNumVal() {
		int n = Utilities.getInt(numField.getText());
    	if (n<1) {
    		n = 1;
    		numField.setText("1");
    	}
    	else if (n>10) {
    		n = 10;
    		numField.setText("10");
    	}
    	return 1.0 - ((double) n * 0.05);
	}
	private void popupHelp() {
		String msg = 
				"If multiple chr-by-chr cells are shown, click on a cell to view the cell only.";
		msg +=  "\n\nFor the chr-by-chr cell view only:"
				+ "\n   Select a block by double-clicking on it (boundary must be showing), "
				+ "\n      or select a region by dragging the mouse and double-click on it."
				+ "\n\n   The first click will turn the block/region beige, the second click will bring up"
				+ "\n      the 2D display of the block/region." 
				+ "\n\n   A selected beige block/region will stay in view "
				+ "\n      while changing the DotPlot size using the +/- buttons.";
		msg += "\n\nFor the genome display: change the reference by selecting it via the dropdown.";
		msg += "\n\nSee ? for details.\n";
		
		Popup.displayInfoMonoSpace(this, "Quick Help", msg, false);
	}
	private void popupStats() {
		String msg = plot.prtCntsS(); 
		Popup.displayInfoMonoSpace(this, "Dot plot stats", msg, false);
	}
	 
    protected void kill() {filter.setVisible(false);} // DotPlotFrame on shutdown; only needed if !modal
    
    protected void update() {plot.repaint();} // Filter change
    
	protected void setEnable() {homeButton.setEnabled(!data.isHome());}
	
	protected void setProjects(Project[] projects) { // DotPlotFrame
		if (projects == null) {
			referenceSelector.setVisible(false);
			return;
		}
		
		for (Project p : projects)
			referenceSelector.addItem(p);
		
		// Disable if self-alignment
		if (projects.length == 2 && projects[0].getID() == projects[1].getID())
			referenceSelector.setEnabled(false);
	}
	
	public String getHelpText(MouseEvent event) {  // symap.frame.Helpbar
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
}
