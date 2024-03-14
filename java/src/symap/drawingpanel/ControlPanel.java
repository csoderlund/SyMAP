package symap.drawingpanel;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

import colordialog.ColorDialogHandler;
import symap.Globals;
import symap.frame.HelpBar;
import symap.frame.HelpListener;
import util.ImageViewer;
import util.Jcomp;
import util.Jhtml;

/**
 * The top panel containing things such as the forward/back buttons, reset button, etc...
 * Handles the actions of those buttons through the arguments passed into the constructor.
 * CAS534 squash buttons so can view all from popup; 
 * CAS550 remove Stats button that I added in 541; I added little ? instead
 * CAS551 add shrink button
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ControlPanel extends JPanel implements HelpListener {	
	// These are accessed in DrawingPanel to match button text with function
	public static final String MOUSE_FUNCTION_ZOOM_ALL 		= "Zoom All Tracks";
	public static final String MOUSE_FUNCTION_ZOOM_SINGLE 	= "Zoom Select Track";
	public static final String MOUSE_FUNCTION_CLOSEUP 		= "Align (Max " + Globals.MAX_CLOSEUP_K + ")"; 
	public static final String MOUSE_FUNCTION_SEQ 			= "Seq Options";
	
	private boolean bUseNew=true;
	private JButton scaleButton, upButton, downButton, shrinkButton;
	private JButton showImageButton, editColorsButton;
	private JButton helpButtonLg, helpButtonSm;
	private JButton popupButton;  // CAS551 replace JComboBox with popup
	private JComboBox <String> mouseFunction; // left in because drag&relase does not work w/o
	
	private DrawingPanel dp;
	private HistoryControl hc;
	private ColorDialogHandler cdh;
	
	public ControlPanel(DrawingPanel dp, HistoryControl hc, ColorDialogHandler cdh, HelpBar bar, boolean bIsCE){
		super();
		this.dp = dp;
		this.hc = hc;
		this.cdh = cdh;

		JButton homeButton       = (JButton) Jcomp.createButton(this,"/images/home.gif",
				"Home: Go back in history to the first view",bar,null,false, false);
		JButton backButton       = (JButton) Jcomp.createButton(this,"/images/back.gif",
				"Back: Go back in history",bar,null,false, false);
		JButton forwardButton    = (JButton) Jcomp.createButton(this,"/images/forward.gif",
				"Forward: Go forward in history",bar,null,false, false);

		if (hc != null) hc.setButtons(homeButton,backButton,forwardButton); // See HistoryControl

		downButton       = (JButton) Jcomp.createButton(this,"/images/minus.gif",
				"Decrease the bases shown",bar,buttonListener,false, false);
		upButton         = (JButton) Jcomp.createButton(this,"/images/plus.gif",
				"Increase the bases shown",bar,buttonListener,false, false);
		shrinkButton         = (JButton) Jcomp.createButton(this,"/images/shrink.png",
				"Shrink space between tracks",bar,buttonListener,false, false);
		scaleButton      = (JButton) Jcomp.createButton(this,"/images/scale.gif",
				"Scale: Draw all of the tracks to bp scale",bar,buttonListener,false, false);
		showImageButton  = (JButton) Jcomp.createButton(this,"/images/print.gif",
				"Save: Save as image.", bar,buttonListener,false, false);
		editColorsButton = (JButton) Jcomp.createButton(this,"/images/colorchooser.gif",
				"Colors: Edit the color settings",bar,buttonListener,false, false);
		
		helpButtonLg = Jhtml.createHelpIconUserLg(Jhtml.align2d);
		helpButtonSm= (JButton) Jcomp.createButton(this,"/images/info.png",
				"Quick Help Popup",bar,buttonListener,false, false);
		
		mouseFunction = createMouseFunctionSelector(bar); // for drag&drop
		createPopup();
		
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints constraints = new GridBagConstraints();
		setLayout(gridbag);
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.gridheight = 1;
		constraints.ipadx = 5;
		constraints.ipady = 8;

		if (hc != null) {
			addToGrid(this, gridbag, constraints, homeButton, 1, 1);
			addToGrid(this, gridbag, constraints, backButton, 1, 0);
			addToGrid(this, gridbag, constraints, forwardButton, 1, 2);
			//addToGrid(this, gridbag, constraints, new JLabel(" "), 1, 1); 
		}	
		addToGrid(this, gridbag, constraints, downButton, 1, 0);
		addToGrid(this, gridbag, constraints, upButton, 1, 1);	
		addToGrid(this, gridbag, constraints, shrinkButton, 1, 1);	
		addToGrid(this, gridbag, constraints, scaleButton, 1, 2);
		//addToGrid(this, gridbag, constraints, new JLabel(" "), 1);
			
		//if (bIsCE) addToGrid(this, gridbag, constraints, new JLabel("Selected:"), 1); // CAS541 not enough room if !CE
		if (bUseNew) addToGrid(this, gridbag, constraints, popupButton, 1, 2);
		else 		addToGrid(this, gridbag, constraints, mouseFunction, 1, 2);
		//addToGrid(this, gridbag, constraints, new JLabel(" "), 1);
		
		if (cdh != null) addToGrid(this,gridbag,constraints,editColorsButton,1, 1); 
		addToGrid(this, gridbag, constraints, showImageButton, 1, 1);
		addToGrid(this,gridbag,constraints, helpButtonLg,1, 1);
		addToGrid(this,gridbag,constraints, helpButtonSm,1, 1);
	}
	
	private ActionListener buttonListener = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			final Object source = e.getSource();
			if (source == scaleButton)           dp.drawToScale();
	
			else if (source == downButton)       dp.changeShowRegion(0.5);
			else if (source == upButton)         dp.changeShowRegion(2.0); 
			else if (source == shrinkButton)     dp.shrinkSpace(); 
			
			else if (source == editColorsButton) cdh.showX();
			else if (source == showImageButton)  ImageViewer.showImage("Exp_2D", (JPanel)dp); // CAS507 made static
			else if (source == helpButtonSm) 	 popupHelp();
		}
	};
	
	public void setPanelEnabled(boolean enable) {
		scaleButton.setEnabled(enable); 
		showImageButton.setEnabled(enable);
		editColorsButton.setEnabled(enable);

		downButton.setEnabled(enable);
		upButton.setEnabled(enable);
		shrinkButton.setEnabled(enable);
		if (hc != null) hc.setEnabled(enable);	 
	}
	public void clear() { // CAS531 put back to zoom
		dp.setMouseFunction(MOUSE_FUNCTION_ZOOM_ALL);
		popupButton.setText(MOUSE_FUNCTION_ZOOM_ALL);
	}
	// CAS551 add sep 	
	private void addToGrid(Container cp, GridBagLayout gbl, GridBagConstraints gbc, Component comp, int w, int sep) {
		gbc.gridwidth = w;
		gbl.setConstraints(comp,gbc);
		add(comp);
		if (sep > 0) addToGrid(cp, gbl,gbc,new JLabel(),1,0);
		if (sep > 1) addToGrid(cp, gbl,gbc,new JSeparator(JSeparator.VERTICAL),1,0);
    }
	
	public String getHelpText(MouseEvent event) { 
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
	///////////////// (i) ////////////////////////////////////////
	static final String Q_HELP = "See ? for details\n\n";
	
	static final String SEQ_help = "Track Information:"
			+ "\n Hover on gene for information."
			+ "\n Right-click on gene for popup of full description."
			+ "\n Right-click in non-gene track space for subset filter popup."
			+ "\n\n";

	static final String HIT_help = "Hit-wire Information:"
			+ "\n Hover on hit-wire for information."
			+ "\n Right-click on hit-wire for popup of full information."
			+ "\n Right-click in white space of hit area for subset filter popup."
			+ "\n\n";
	
	static final String FIL_help = "Filter Information:" 
			+ "\n Changing filters are retained in subsequent displays."
			+ "\n Any filter change is a History event."
			+ "\n\n";
	
	static final String DRAG_help = "Selected Track Options: Default '" + MOUSE_FUNCTION_ZOOM_ALL + "'"
			+ "\n Select the button followed by the desired function; the text on the button will change."
			+ "\n In a track, press the left mouse button and drag, release the button to select region."
			+ "\n The selected option will be applied to the selected track region, as follows:"
			+ "\n    " + MOUSE_FUNCTION_ZOOM_ALL + ": selected region on one track, zoom all tracks to match."
			+ "\n    " + MOUSE_FUNCTION_ZOOM_SINGLE + ": only zoom region of selected track."
			+ "\n    " + MOUSE_FUNCTION_CLOSEUP + ": popup window of aligned sequence to the region."
			+ "\n    " + MOUSE_FUNCTION_SEQ + ": popup window of options, followed by showing sequence in region."
			;	
	private void popupHelp() {
		util.Utilities.displayInfoMonoSpace(this, "2d Quick Help", 
				Q_HELP+SEQ_help+HIT_help+FIL_help+DRAG_help, false);
	}
	////////////////////// Select ///////////////////////////
	private void createPopup() {
		final JPopupMenu popup = new JPopupMenu();
		popup.setBackground(symap.Globals.white);
		
		JMenuItem popupTitle = new JMenuItem();
		popupTitle.setText("Selected Track Options");
		popupTitle.setEnabled(false);
		popup.add(popupTitle);
		popup.addSeparator();
		
		popup.add(new JMenuItem(new AbstractAction(MOUSE_FUNCTION_ZOOM_ALL) {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(ActionEvent e) {
				dp.setMouseFunction(MOUSE_FUNCTION_ZOOM_ALL);
				popupButton.setText(MOUSE_FUNCTION_ZOOM_ALL);
			}
		}));
		popup.add(new JMenuItem(new AbstractAction(MOUSE_FUNCTION_ZOOM_SINGLE) {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(ActionEvent e) {
				dp.setMouseFunction(MOUSE_FUNCTION_ZOOM_SINGLE);
				popupButton.setText(MOUSE_FUNCTION_ZOOM_SINGLE);
			}
		}));
		popup.add(new JMenuItem(new AbstractAction(MOUSE_FUNCTION_CLOSEUP) {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(ActionEvent e) {
				dp.setMouseFunction(MOUSE_FUNCTION_CLOSEUP);
				popupButton.setText(MOUSE_FUNCTION_CLOSEUP);
			}
		}));
		popup.add(new JMenuItem(new AbstractAction(MOUSE_FUNCTION_SEQ) {
			private static final long serialVersionUID = 1L;
			public void actionPerformed(ActionEvent e) {
				dp.setMouseFunction(MOUSE_FUNCTION_SEQ);
				popupButton.setText(MOUSE_FUNCTION_SEQ);
			}
		}));
		
		popupButton = new JButton(MOUSE_FUNCTION_ZOOM_ALL);
		popupButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
		popupButton.setToolTipText("Selected Track Option; see (i)");
		Dimension d = new Dimension(120, 25); // w,d
		popupButton.setPreferredSize(d); 
		popupButton.setMaximumSize(d);
		popupButton.setMinimumSize(d);
	}
	//////////////////////////////////////////////////
	// leave in because drag&release does not work without it; popupButton.addActionListioner does not fix it 
	private JComboBox <String> createMouseFunctionSelector(HelpBar bar) {
		String [] labels = { // CAS504 change order and add _SEQ
				MOUSE_FUNCTION_ZOOM_ALL,MOUSE_FUNCTION_ZOOM_SINGLE,MOUSE_FUNCTION_CLOSEUP,MOUSE_FUNCTION_SEQ
		};
		JComboBox <String> comboMouseFunctions = new JComboBox <String> (labels); // CAS507 got rid of one warning
		
		comboMouseFunctions.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
		       String item = (String) comboMouseFunctions.getSelectedItem();
		       dp.setMouseFunction(item);
			}
		});
		comboMouseFunctions.setSelectedIndex(0); // default to "zoom all"
		comboMouseFunctions.setToolTipText("Set the function of the left mouse button (click+drag)."); // CAS543 add
		if (bar != null) bar.addHelpListener(comboMouseFunctions,this);
		
		return comboMouseFunctions;
	}
}
