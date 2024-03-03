package symap.drawingpanel;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JComboBox;

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
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ControlPanel extends JPanel implements HelpListener {	
	private static final String HELP					    = "? Online Help"; // CAS532 add
	public static final String MOUSE_FUNCTION_SEQ 			= "Show Seq Options";
	public static final String MOUSE_FUNCTION_CLOSEUP 		= "Align (Max " + Globals.MAX_CLOSEUP_K + ")";
	public static final String MOUSE_FUNCTION_ZOOM_SINGLE 	= "Zoom Select Track";
	public static final String MOUSE_FUNCTION_ZOOM_ALL 		= "Zoom All Tracks";
	
	private JButton scaleButton, upButton, downButton;
	private JButton showImageButton, editColorsButton;
	private JButton helpButtonLg, helpButtonSm;
	private JComboBox <String> mouseFunction; // CAS531 added so can reset
	
	private DrawingPanel dp;
	private HistoryControl hc;
	private ColorDialogHandler cdh;
	
	private static final String SEQ_help = 
			"Track Information:"
			+ "\n Hover on gene for information."
			+ "\n Right-click on gene for popup of full description."
			+ "\n Right-click in non-gene track space for subset filter popup.";
	
	private static final String HIT_help = 
			"Hit-wire Information:"
			+ "\n Hover on hit-wire for information."
			+ "\n Right-click on hit-wire for popup of full information."
			+ "\n Right-click in white space of hit area for subset filter popup.";
	
	private static final String FIL_help =
			"Filter Information:" 
			+ "\n Changing filters are retained in subsequent displays."
			+ "\n Any filter change is a History event.";

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

		if (hc != null) hc.setButtons(homeButton,backButton,forwardButton);

		downButton       = (JButton) Jcomp.createButton(this,"/images/minus.gif",
				"Shrink the view (less bp)",bar,buttonListener,false, false);
		upButton         = (JButton) Jcomp.createButton(this,"/images/plus.gif",
				"Grow the view (more bp)",bar,buttonListener,false, false);
		scaleButton      = (JButton) Jcomp.createButton(this,"/images/scale.gif",
				"Scale: Draw all of the tracks to bp scale",bar,buttonListener,false, false);
		showImageButton  = (JButton) Jcomp.createButton(this,"/images/print.gif",
				"Save: Save as image.", bar,buttonListener,false, false);
		editColorsButton = (JButton) Jcomp.createButton(this,"/images/colorchooser.gif",
				"Colors: Edit the color settings",bar,buttonListener,false, false);
		
		helpButtonLg = Jhtml.createHelpIconUserLg(Jhtml.align2d);
		
		// icon from https://icons8.com/icons/set/info
		helpButtonSm= (JButton) Jcomp.createButton(this,"/images/info.png",
				"Quick Help Popup",bar,buttonListener,false, false);
		
		mouseFunction = createMouseFunctionSelector(bar);
		
		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints constraints = new GridBagConstraints();
		setLayout(gridbag);
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.gridheight = 1;
		constraints.ipadx = 5;
		constraints.ipady = 8;

		if (hc != null) {
			addToGrid(this, gridbag, constraints, homeButton, 1);
			addToGrid(this, gridbag, constraints, backButton, 1);
			addToGrid(this, gridbag, constraints, forwardButton, 1);
			addToGrid(this, gridbag, constraints, new JLabel(" "), 1); 
			//addToGrid(this, gridbag, constraints, new JSeparator(SwingConstants.VERTICAL), 1); // CAS534 remove 4 of these
		}	
		addToGrid(this, gridbag, constraints, downButton, 1);
		addToGrid(this, gridbag, constraints, upButton, 1);		
		addToGrid(this, gridbag, constraints, scaleButton, 1);
		addToGrid(this, gridbag, constraints, new JLabel(" "), 1);
			
		addToGrid(this, gridbag, constraints, new JLabel("Selected:"), 1); // CAS541 not enough room if !CE
		addToGrid(this, gridbag, constraints, mouseFunction, 1);
		addToGrid(this, gridbag, constraints, new JLabel(" "), 1);
		
		if (cdh != null) addToGrid(this,gridbag,constraints,editColorsButton,1); // CAS517 put before Print
		addToGrid(this, gridbag, constraints, showImageButton, 1);
		addToGrid(this,gridbag,constraints, helpButtonLg,1);
		addToGrid(this,gridbag,constraints, helpButtonSm,1);
	}
	
	private ActionListener buttonListener = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			final Object source = e.getSource();
			if (source == scaleButton)           dp.drawToScale();
	
			else if (source == downButton)       dp.changeAlignRegion(0.5);
			else if (source == upButton)         dp.changeAlignRegion(2.0); // CAS550
			
			else if (source == editColorsButton) cdh.showX();
			else if (source == showImageButton)  ImageViewer.showImage("Exp_2D", (JPanel)dp); // CAS507 made static
			else if (source == helpButtonSm) popupHelp();
		}
	};
	
	public void setPanelEnabled(boolean enable) {
		scaleButton.setEnabled(enable);
		showImageButton.setEnabled(enable);
		editColorsButton.setEnabled(enable);

		downButton.setEnabled(enable);
		upButton.setEnabled(enable);
		if (hc != null) hc.setEnabled(enable);	 
	}
	public void clear() { mouseFunction.setSelectedIndex(0);  }// CAS531 put back to zoom
		
	private void addToGrid(Container cp, GridBagLayout layout, GridBagConstraints constraints, Component comp, int width) {
		constraints.gridwidth = width;
		layout.setConstraints(comp, constraints);
		cp.add(comp);
	}
	
	public String getHelpText(MouseEvent event) { 
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
	
	private void popupHelp() {
		util.Utilities.displayInfoMonoSpace(this, "2d Quick Help", SEQ_help+"\n\n"+HIT_help+"\n\n"+FIL_help, false);
	}
	private JComboBox <String> createMouseFunctionSelector(HelpBar bar) {
		String [] labels = { // CAS504 change order and add _SEQ
				MOUSE_FUNCTION_ZOOM_ALL,
				MOUSE_FUNCTION_ZOOM_SINGLE,
				MOUSE_FUNCTION_CLOSEUP,
				MOUSE_FUNCTION_SEQ,
				HELP
		};
		JComboBox <String> comboMouseFunctions = new JComboBox <String> (labels); // CAS507 got rid on one warning
		
		comboMouseFunctions.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
		        String item = (String) comboMouseFunctions.getSelectedItem();
		        if (item.contentEquals(HELP)) {
		        	Jhtml.onlineHelp(Jhtml.control);
		        	comboMouseFunctions.setSelectedIndex(0);
		        }
		        else dp.setMouseFunction(item);
			}
		});
		
		comboMouseFunctions.setSelectedIndex(0); // default to "zoom all"
		comboMouseFunctions.setName("Set the function of the left mouse button (click+drag)."); // set help text
		comboMouseFunctions.setToolTipText("Set the function of the left mouse button (click+drag)."); // CAS543 add
		if (bar != null) bar.addHelpListener(comboMouseFunctions,this);
		
		return comboMouseFunctions;
	}
}
