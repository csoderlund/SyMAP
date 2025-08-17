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
import javax.swing.JComboBox;

import colordialog.ColorDialogHandler;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ImageViewer;
import util.Jcomp;

/**********************************************************
 * The upper row for the DotPlot
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ControlPanel extends JPanel implements HelpListener {
    private Data data; 		// changes to zoom, etc set in data
    private Plot plot; 		// for show image and repaint
    private Filter filter=null; 								
    
    private JButton homeButton, minusButton, plusButton;
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
				"/images/home.gif", "Reset to full view with default size.");
		minusButton =  Jcomp.createIconButton(this, hb, buttonListener,
				"/images/minus.gif","Decrease the size of the dotplot.");
		plusButton  =  Jcomp.createIconButton(this, hb, buttonListener,
				"/images/plus.gif", "Increase the size of the dotplot.");
		
		scaleToggle  =  Jcomp.createBorderIcon(this, hb, buttonListener,
				"/images/scale.gif", "Draw to BP scale."); 
		
		referenceSelector = new JComboBox <Project> ();
		referenceSelector.addActionListener(buttonListener);
		referenceSelector.setToolTipText("Reference: Change reference (x-axis) project.");
		referenceSelector.setName("Reference: Change reference (x-axis) project.");	
		hb.addHelpListener(referenceSelector, this);
		
		filter = new Filter(d, this);
		filterButton     = Jcomp.createButton(this,hb, buttonListener, 
				"Filters", "Filters: Change filter settings.");
	
		showImageButton  =  Jcomp.createIconButton(this, hb, buttonListener,
				"/images/print.gif", "Save: Save as image.");
		editColorsButton = Jcomp.createIconButton(this, hb, buttonListener,
				"/images/colorchooser.gif", "Colors: Edit the color settings");
		helpButtonLg     = util.Jhtml.createHelpIconUserLg(util.Jhtml.dotplot);
		helpButtonSm     = Jcomp.createIconButton(this, hb, buttonListener,
				"/images/info.png", "Quick Help Popup");
		statsButton = (JButton) Jcomp.createIconButton(this, hb, buttonListener,
				"/images/s.png", "Stats Popup");
		
		JPanel row = Jcomp.createGrayRowPanel(); 
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
    protected void kill() {filter.setVisible(false);} // DotPlotFrame on shutdown; only needed if !modal
    protected void update() {plot.repaint();} // Filter change
    
    private ActionListener buttonListener = new ActionListener() {
	    public void actionPerformed(ActionEvent evt) {
			Object src = evt.getSource();
			if (src == filterButton) {
				filter.showX();
			}
			else {
			    if (src == homeButton) 			  data.setHome(); 
			    else if (src == minusButton)      data.factorZoom(0.95);
			    else if (src == plusButton)       data.factorZoom(1/0.95);
			  
			    else if (src == scaleToggle) 	  {
			    	data.bIsScaled = !data.bIsScaled;
			    	scaleToggle.setBackground(Jcomp.getBorderColor(data.bIsScaled));
			    }
			    else if (src == referenceSelector)data.setReference((Project)referenceSelector.getSelectedItem());
			    else if (src == showImageButton)  ImageViewer.showImage("DotPlot", plot); 
			    else if (src == editColorsButton) cdh.showX();
			    else if (src == helpButtonSm) 	  popupHelp();
			    else if (src == statsButton)  	  popupStats();
			    plot.repaint();
			}
			setEnable();
	    }
	};
	protected void setEnable() {
		homeButton.setEnabled(!data.isHome());
	}
    
	public String getHelpText(MouseEvent event) {  // symap.frame.Helpbar
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
	
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
	private void popupHelp() {
		String msg = 
				"If multiple chr-by-chr cells are shown, click on a cell to view the cell only.";
		msg +=  "\n\nFor the chr-by-chr cell view only:"
				+ "\n   Double-click on a synteny block (boundary must be showing), "
				+ "\n   or create a region by dragging the mouse and double-click on it."
				+ "\nThe first click will turn the region beige, the second click will bring up"
				+ "\nthe 2D display of the region.";
		msg += "\n\nFor the genome display: change the reference by selecting it via the dropdown.";
		msg += "\n\nSee ? for details.\n";
		
		util.Utilities.displayInfoMonoSpace(this, "Quick Help", msg, false);
	}
	private void popupStats() {
		String msg = plot.prtCntsS(); // CAS571 renamed
		util.Utilities.displayInfoMonoSpace(this, "Dot plot stats", msg, false);
	}
}
