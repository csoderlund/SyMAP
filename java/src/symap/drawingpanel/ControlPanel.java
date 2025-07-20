package symap.drawingpanel;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSeparator;

import colordialog.ColorDialogHandler;
import symap.Globals;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ImageViewer;
import util.Jcomp;
import util.Jhtml;

/**
 * 2D drawingpanel Control Panel
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ControlPanel extends JPanel implements HelpListener {	
	// These are accessed in DrawingPanel to match button text with function
	protected static final String MOUSE_FUNCTION_ZOOM_ALL 		= "Zoom All Tracks";
	protected static final String MOUSE_FUNCTION_ZOOM_SINGLE 	= "Zoom Select Track";
	protected static final String MOUSE_FUNCTION_CLOSEUP 		= "Align (Max " + Globals.MAX_CLOSEUP_K + ")"; 
	protected static final String MOUSE_FUNCTION_SEQ 			= "Seq Options";
	
	private JButton homeButton, backButton, forwardButton; // Home does not clear highlighting
	private JButton zoomPopupButton;
	private JRadioButtonMenuItem zoomAllRadio	= new JRadioButtonMenuItem(MOUSE_FUNCTION_ZOOM_ALL);
	private JRadioButtonMenuItem zoomSelRadio	= new JRadioButtonMenuItem(MOUSE_FUNCTION_ZOOM_SINGLE);
	private JCheckBox zoomBox = new JCheckBox("");
	
	private JButton showPopupButton;
	private JRadioButtonMenuItem showAlignRadio	= new JRadioButtonMenuItem(MOUSE_FUNCTION_CLOSEUP);
	private JRadioButtonMenuItem showSeqRadio	= new JRadioButtonMenuItem(MOUSE_FUNCTION_SEQ);
	private JCheckBox showBox = new JCheckBox("");
	
	private JButton plusButton, minusButton, shrinkButton, scaleButton;
	private JButton showImageButton, editColorsButton, helpButtonLg, helpButtonSm;
	
	private DrawingPanel dp;
	private HistoryControl hc;
	private ColorDialogHandler cdh;
	
	public ControlPanel(DrawingPanel dp, HistoryControl hc, ColorDialogHandler cdh, HelpBar bar, boolean bIsCE){
		super();
		this.dp = dp;
		this.hc = hc;
		this.cdh = cdh;

		homeButton       = Jcomp.createIconButton(this,bar,null,"/images/home.gif",
				"Home: Go to original zoom view.");	// CAS570 add 'zoom' as filters are not included in history
		backButton       =  Jcomp.createIconButton(this,bar,null,"/images/back.gif",
				"Back: Go back in zoom history"); 
		forwardButton    = Jcomp.createIconButton(this,bar,null,"/images/forward.gif",
				"Forward: Go forward in zoom history"); 

		if (hc != null) hc.setButtons(homeButton,backButton,forwardButton); // See HistoryControl

		minusButton     = Jcomp.createIconButton(this,bar,buttonListener,"/images/minus.gif",
				"Decrease the bases shown");
		plusButton       =  Jcomp.createIconButton(this,bar,buttonListener,"/images/plus.gif",
				"Increase the bases shown");
		shrinkButton   = Jcomp.createIconButton(this,bar,buttonListener,"/images/shrink.png",
				"Shrink space between tracks");
		scaleButton    =  Jcomp.createBorderIcon(this,bar,buttonListener,"/images/scale.gif",
				"Scale: Draw all of the tracks to bp scale");
		showImageButton  =  Jcomp.createIconButton(this, bar,buttonListener,"/images/print.gif",
				"Save: Save as image.");
		editColorsButton =  Jcomp.createIconButton(this,bar,buttonListener,"/images/colorchooser.gif",
				"Colors: Edit the color settings");
		
		helpButtonLg = Jhtml.createHelpIconUserLg(Jhtml.align2d);
		helpButtonSm=  Jcomp.createIconButton(this,bar,buttonListener,"/images/info.png",
				"Quick Help Popup");
		
		createMouseFunctionSelector(bar); // for drag&drop
		
		zoomPopupButton = Jcomp.createButton(this,bar, null,"Zoom",
				"Click checkmark to activate; Select button for menu; see Info (i)");
		showPopupButton = Jcomp.createButton(this,bar, null,"Show",
				"Click checkmark to activate; Select button for menu; see Info (i)");
		createSelectPopups(); 
		
		JPanel row = Jcomp.createGrayRowPanel();
		
		GridBagLayout gbl = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		setLayout(gbl); 		// If row.setLayout(gbl), cannot add Separators, but ipadx, etc work
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.gridheight = 1;
		gbc.ipadx = gbc.ipady = 0;
		
		String sp0="", sp1=" ", sp2="  ";
		if (hc != null) {
			addToGrid(row, gbl, gbc, homeButton, sp1);
			addToGrid(row, gbl, gbc, backButton, sp1);
			addToGrid(row, gbl, gbc, forwardButton, sp2);
		}	
		addToGrid(row, gbl, gbc, plusButton, sp1);	
		addToGrid(row, gbl, gbc, minusButton, sp2);
		addToGrid(row, gbl, gbc, shrinkButton, sp2);	
		addToGrid(row, gbl, gbc, scaleButton, sp2);
			
		addToGrid(row, gbl, gbc, zoomBox,    sp0);
		addToGrid(row, gbl, gbc, zoomPopupButton, sp1);
		addToGrid(row, gbl, gbc, new JSeparator(JSeparator.VERTICAL), sp1); // End home functions
		
		addToGrid(row, gbl, gbc, showBox,    sp0);
		addToGrid(row, gbl, gbc, showPopupButton, sp1); 
		
		addToGrid(row, gbl, gbc, editColorsButton, sp2); 
		addToGrid(row, gbl, gbc, new JSeparator(JSeparator.VERTICAL), sp2); // End edit display 
		
		addToGrid(row, gbl, gbc, showImageButton, sp1);
		addToGrid(row, gbl, gbc, helpButtonLg, sp1);
		addToGrid(row, gbl, gbc, helpButtonSm, sp1);
		
		add(row);
	}
	
	private void addToGrid(JPanel cp, GridBagLayout gbl, GridBagConstraints gbc, Component comp, String sp) {
		gbl.setConstraints(comp,gbc);
		cp.add(comp);
		if (sp.length()>0) addToGrid(cp, gbl,gbc,new JLabel(sp),"");
    }
	
	private void createSelectPopups() {
		 PopupListener listener = new PopupListener();
		 zoomAllRadio.addActionListener(listener);
		 zoomSelRadio.addActionListener(listener);
		 showAlignRadio.addActionListener(listener);
		 showSeqRadio.addActionListener(listener);
		 
		 ///// zoom
		 JPopupMenu zoomPopupMenu = new JPopupMenu("Zoom"); zoomPopupMenu.setBackground(Globals.white);
	     JMenuItem zpopupTitle = new JMenuItem("Select Region of Track");
		 zpopupTitle.setEnabled(false);
		 zoomPopupMenu.add(zpopupTitle);
		 zoomPopupMenu.addSeparator();
		 zoomPopupMenu.add(zoomAllRadio);
		 zoomPopupMenu.add(zoomSelRadio);
		 ButtonGroup grp = new ButtonGroup();
		 grp.add(zoomAllRadio);grp.add(zoomSelRadio);
		
		 zoomPopupButton.setComponentPopupMenu(zoomPopupMenu); // zoomPopupButton create in main
		 zoomBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				boolean b = zoomBox.isSelected();
				zoomPopupButton.setEnabled(b);
				showPopupButton.setEnabled(!b);
				showBox.setSelected(!b);
				
				if (zoomAllRadio.isSelected())  dp.setMouseFunction(MOUSE_FUNCTION_ZOOM_ALL);
				else  							dp.setMouseFunction(MOUSE_FUNCTION_ZOOM_SINGLE);
			}
		 }); 
		 zoomPopupButton.addMouseListener(new MouseAdapter() { // allows either left/right mouse to popup
            public void mousePressed(MouseEvent e) {
                zoomPopupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
         });
		 zoomBox.setSelected(true);
		 zoomPopupButton.setEnabled(true);
		 zoomAllRadio.setSelected(true);
		
		 ////// show
		 JPopupMenu showPopupMenu = new JPopupMenu("Show"); showPopupMenu.setBackground(Globals.white);
		 JMenuItem spopupTitle = new JMenuItem("Select Region of Track");
		 spopupTitle.setEnabled(false);
		 showPopupMenu.add(spopupTitle);
		 showPopupMenu.addSeparator();
		 showPopupMenu.add(showAlignRadio);
		 showPopupMenu.add(showSeqRadio);
		 ButtonGroup grp2 = new ButtonGroup();
		 grp2.add(showAlignRadio); grp2.add(showSeqRadio);
		 
		 showPopupButton.setComponentPopupMenu(showPopupMenu); // showPopupButton create in main
		 showBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				boolean b = showBox.isSelected();
				zoomPopupButton.setEnabled(!b);
				showPopupButton.setEnabled(b);
				zoomBox.setSelected(!b);
				
				if (showAlignRadio.isSelected())  	dp.setMouseFunction(MOUSE_FUNCTION_CLOSEUP);
				else  								dp.setMouseFunction(MOUSE_FUNCTION_SEQ);
			}
		 }); 
		 showPopupButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                showPopupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
         });
		 showBox.setSelected(false);
		 showPopupButton.setEnabled(false);
		 showAlignRadio.setSelected(true);
	}
	
	private class PopupListener implements ActionListener {
		private PopupListener() { }
		public void actionPerformed(ActionEvent event) { 
			Object src = event.getSource();
			if (src == zoomAllRadio) {
				 zoomAllRadio.setSelected(true);
				 dp.setMouseFunction(MOUSE_FUNCTION_ZOOM_ALL);
				 zoomPopupButton.setText("Zoom");
			}
			else if (src == zoomSelRadio) {
				 zoomSelRadio.setSelected(true);
				 dp.setMouseFunction(MOUSE_FUNCTION_ZOOM_SINGLE);
				 zoomPopupButton.setText("Zoom Select");
			}
			else if (src == showAlignRadio) {
				 showAlignRadio.setSelected(true);
				 dp.setMouseFunction(MOUSE_FUNCTION_CLOSEUP);
				 showPopupButton.setText("Show");
			}
			else  if (src == showSeqRadio) {
				 showSeqRadio.setSelected(true);
				 dp.setMouseFunction(MOUSE_FUNCTION_SEQ);
				 showPopupButton.setText("Show Seq");
			}
		}
	}
	private ActionListener buttonListener = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			final Object source = e.getSource();
			if (source == scaleButton)    {
				dp.isToScale = !dp.isToScale;
				scaleButton.setBackground(Jcomp.getBorderColor(dp.isToScale ));
				dp.drawToScale();
			}
	
			else if (source == minusButton)      dp.changeShowRegion(0.5);
			else if (source == plusButton)       dp.changeShowRegion(2.0); 
			else if (source == shrinkButton)     dp.shrinkSpace(); 
			
			else if (source == editColorsButton) cdh.showX();
			else if (source == showImageButton)  ImageViewer.showImage("Exp_2D", (JPanel)dp); 
			else if (source == helpButtonSm) 	 popupHelp();
		}
	};
	
	//////////////////////////////////////////////////
	// Use to be a functional drop-down for these functions; still used for Drag&drop to work
	private JComboBox <String> createMouseFunctionSelector(HelpBar bar) {
		String [] labels = { MOUSE_FUNCTION_ZOOM_ALL,MOUSE_FUNCTION_ZOOM_SINGLE,MOUSE_FUNCTION_CLOSEUP,MOUSE_FUNCTION_SEQ};
		JComboBox <String> comboMouseFunctions = new JComboBox <String> (labels); 
		
		comboMouseFunctions.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
		       String item = (String) comboMouseFunctions.getSelectedItem();
		       dp.setMouseFunction(item);
			}
		});
		comboMouseFunctions.setSelectedIndex(0); 
		if (bar != null) bar.addHelpListener(comboMouseFunctions,this);
		return comboMouseFunctions;
	}
	//////////////////////////////////////////////////////////
	protected void setPanelEnabled(boolean enable) {
		scaleButton.setEnabled(enable); 
		showImageButton.setEnabled(enable);
		editColorsButton.setEnabled(enable);

		minusButton.setEnabled(enable);
		plusButton.setEnabled(enable);
		shrinkButton.setEnabled(enable);
		if (hc != null) hc.setEnabled(enable);	 
	}
	
	protected void clear() { 
		dp.setMouseFunction(MOUSE_FUNCTION_ZOOM_ALL);
		zoomBox.setSelected(true); showBox.setSelected(false);
	
		scaleButton.setSelected(false);
		dp.isToScale = false;
		scaleButton.setBackground(Jcomp.getBorderColor(false));
	}
	
	public String getHelpText(MouseEvent event) { 
		Component comp = (Component)event.getSource(); 
		Object source = event.getSource();
		String msg = comp.getName();
		if (source == scaleButton) {
			if (dp.isToScale) msg += "; Scaled" ;
			else msg += "; Not scaled";
		}
		else if (source == backButton) {
			msg += "; Back: " + hc.nBack(); 
		}
		else if (source == forwardButton) {
			msg += "; Forward: " + hc.nForward(); 
		}
		return msg;
	}
	public void setHistory(boolean toScale, String mouseFunction) { 
		scaleButton.setSelected(toScale);
		dp.isToScale = toScale;
		scaleButton.setBackground(Jcomp.getBorderColor(toScale));
		
		// always put back to Zoom as Show is not a history event
		showPopupButton.setSelected(false);
		showBox.setSelected(false);
		showPopupButton.setEnabled(false);
		showPopupButton.setText("Show"); // Show is not a coordinate change, so even if set to SEQ, wasn't activated
		
		boolean bAll = !mouseFunction.equals(MOUSE_FUNCTION_ZOOM_SINGLE);
		if (bAll) dp.setMouseFunction(MOUSE_FUNCTION_ZOOM_ALL);
		else      dp.setMouseFunction(MOUSE_FUNCTION_ZOOM_SINGLE);
		
		zoomPopupButton.setSelected(true);
		zoomBox.setSelected(true);
		zoomPopupButton.setEnabled(true);
		String label = (bAll) ? "Zoom" : "Zoom Select";
		zoomPopupButton.setText(label);
		
		zoomAllRadio.setSelected(bAll);
		zoomSelRadio.setSelected(!bAll);
	}
	
	///////////////// (i) ////////////////////////////////////////
	
	static final String SEQ_help = "Track Information:"
			+ "\n Hover on gene for information."
			+ "\n Right-click on gene for popup with the full description."
			+ "\n Right-click in non-gene track space for subset filter popup."
			+ "\n Problem: if there are more than 2 tracks and the sequence 'Annotation' is"
			+ "\n   shown for the 2nd track, the mouse hover sometimes does not work correctly "
			+ "\n   in the 3rd track."
			+ "\n\n";

	static final String HIT_help = "Hit-wire Information:"
			+ "\n Hover on hit-wire for information (it will be highlighted)."
			+ "\n Right-click on hit-wire for popup for full information "
			+ "\n     (it must be highlighted to be clickable)."
			+ "\n Right-click in white space of hit area for subset filter popup."
			+ "\n\n";
	
	static final String FIL_help = "History Events (Home, > and <):" 
			+ "\n Changing the coordinates of the display is a history event."
			+ "\n Coordinates are changed by any of the icons in the Control panel up to the first '|',"
			+ "\n     plus the Sequence Filter coordinate changes."
			+ "\n Use < and > icons to go back and forward through history events."
			+ "\n Use the home icon to go back to starting display."
			+ "\n\n";
	
	static final String DRAG_help = 
			    "The 'Zoom' and 'Show' buttons respond to selecting a region in a track."
			+ "\nTo select a region, position the mouse over a track, press the left mouse button and drag, "
			+ "\n    release the button at the end of the region to be selected."
			+ "\nThe mouse function used depends on which button is checked, and which item "
			+ "\n    in the checked Zoom or Show popup menu is selected."
			+ "\n  Zoom: " 
			+ "\n    " + MOUSE_FUNCTION_ZOOM_ALL + ":   zoom the region and all tracks to match (default)."
			+ "\n    " + MOUSE_FUNCTION_ZOOM_SINGLE + ": only zoom the selected region."
			+ "\n  Show: " 
			+ "\n    " + MOUSE_FUNCTION_CLOSEUP + ":  the region will be aligned with the "
			+ "\n                       opposite track in a popup window (default)."
			+ "\n    " + MOUSE_FUNCTION_SEQ + ":       a popup menu will provide options to"
			+ "\n                       view the underlying sequence in a popup window."
			+ "\n\n";
			;	
	static final String Q_HELP = "See ? for details\n";
			
	private void popupHelp() {
		util.Utilities.displayInfoMonoSpace(this, "2d Control Quick Help", 
				SEQ_help+HIT_help+FIL_help+DRAG_help+Q_HELP, false);
	}
}
