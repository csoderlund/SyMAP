package symap.frame;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

import java.applet.Applet;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JComboBox;
import javax.swing.SwingConstants;

import colordialog.ColorDialogHandler;
import history.HistoryControl;
import symap.SyMAP;
import symap.SyMAPConstants;
import symap.drawingpanel.DrawingPanel;
import util.ImageViewer;
import util.Utilities;

/**
 * The top panel containing things such as the forward/back buttons, 
 * reset button, etc...
 * 
 * Handles the actions of those buttons through the arguments passed into 
 * the constructor.
 * 
 * @author Austin Shoemaker
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ControlPanel extends JPanel implements SyMAPConstants,
	HelpListener // mdb added 3/30/07 #112
{
//	private static final double DDOWN_FACTOR = .5;								// mdb removed 1/29/09
	private static final double DOWN_FACTOR = 4.0/5.0;	//1.0 / Math.sqrt(2);	// mdb changed 2/18/09
	private static final double UP_FACTOR = 6.0/5.0; 	//Math.sqrt(2); 		// mdb changed 2/18/09
//	private static final double DUP_FACTOR = 2; 								// mdb removed 1/29/09

// mdb removed 1/29/08 #159 simplify properties	
//	public static final boolean CHANGECOLORS, ZOOMCONTROL, HISTORYCONTROL, PRINTCONTROL, EXITCONTROL;
//	static {
//		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/controlpanel.properties"));
//		CHANGECOLORS   = props.getBoolean("changecolors");
//		ZOOMCONTROL    = props.getBoolean("zoomcontrol");
//		HISTORYCONTROL = props.getBoolean("historycontrol");
//		PRINTCONTROL   = props.getBoolean("printcontrol");
//		EXITCONTROL    = props.getBoolean("exitcontrol");
//	}

	private JButton scaleButton;
	//private JButton dDownButton, dUpButton; 	// mdb removed 1/29/09
	private JButton upButton, downButton;
	private JButton showImageButton, editColorsButton;
	//private JButton exitButton; 				// mdb removed 5/20/09
	private DrawingPanel dp;
	private HistoryControl hc;
	private ImageViewer imageViewer;
	private ColorDialogHandler cdh;

	/**
	 * Creates a new <code>ControlPanel</code> instance. 
	 *
	 * @param dp a <code>DrawingPanel</code> value of the drawing panel which 
	 * the control panel will use when certain buttons are pressed.
	 * @param hc a <code>HistoryControl</code> value of the history control in 
	 * which the control panel will register the appropriate buttons with.
	 * @param iv an <code>ImageViewer</code> value of the image viewer used in 
	 * acquiring the icons and showing the image when the print image button 
	 * is pressed.
	 * @param cdh a <code>ColorDialogHandler</code> value of the color dialog 
	 * handler which will be shown when the corresponding button is pressed.
	 * @param bar a <code>HelpBar</code> value of the HelpBar if there is an 
	 * associated help bar to register the button tips with (optional).
	 */
	public ControlPanel(final Applet applet, DrawingPanel dp, HistoryControl hc, ImageViewer iv, 
			ColorDialogHandler cdh, HelpBar bar)
	{
		super();
		this.dp = dp;
		this.hc = hc;
		this.cdh = cdh;
		this.imageViewer = iv;

		//PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/controlpanel.properties")); // mdb removed 1/29/09 #159

		JButton homeButton       = (JButton) Utilities.createButton(this,"/images/home.gif","Home: Go back in history to the first view",bar,null,false);
		//JButton resetButton      = createButton("/images/reset.gif","Reset: Set all of the tracks to their original settings",bar,false); // mdb removed 2/18/09
		//JButton doubleBackButton = createButton("/images/doubleback.gif","Back: Go back in history to last track change",bar,false); // mdb removed 5/20/09
		JButton backButton       = (JButton) Utilities.createButton(this,"/images/back.gif","Back: Go back in history",bar,null,false);
		JButton forwardButton    = (JButton) Utilities.createButton(this,"/images/forward.gif","Forward: Go forward in history",bar,null,false);

		//exitButton = createButton("/images/exit.gif","Close: Close this window",bar,true); // mdb removed 5/20/09

		if (hc != null) hc.setButtons(homeButton,null/*resetButton*/,null/*doubleBackButton*/,backButton,forwardButton,null/*exitButton*/);

		//dDownButton      = createButton("/images/minusminus.gif","Decrease the scale greatly",bar,true); // mdb removed 1/29/09
		downButton       = (JButton) Utilities.createButton(this,"/images/minus.gif","Shrink the alignment region",bar,buttonListener,false);
		upButton         = (JButton) Utilities.createButton(this,"/images/plus.gif","Grow the alignment region",bar,buttonListener,false);
		//dUpButton        = createButton("/images/plusplus.gif","Increase the scale greatly",bar,true); // mdb removed 1/29/09
		scaleButton      = (JButton) Utilities.createButton(this,"/images/scale.gif","Scale: Draw all of the tracks to BP scale",bar,buttonListener,false);
		showImageButton  = (JButton) Utilities.createButton(this,"/images/print.gif",
				"Save: Save as image." + Utilities.getBrowserPopupMessage(applet),
				bar,buttonListener,false);
		editColorsButton = (JButton) Utilities.createButton(this,"/images/colorchooser.gif","Colors: Edit the color settings",bar,buttonListener,false);
		
		JButton helpButton = null;
		
// mdb removed 4/30/09 #162	
//		if (SyMAP.isHelp() && hasHelpButton) {
//			helpButton = createButton("/images/help.gif","Help: Online documentation",bar,false);
//			SyMAP.enableHelpOnButton(helpButton,null);
//		}
		
		// mdb added 5/1/09 #162
		helpButton = (JButton) Utilities.createButton(this,"/images/help.gif",
				"Help: Online documentation." + Utilities.getBrowserPopupMessage(applet),
				bar,null,false);
		helpButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String url = SyMAP.USER_GUIDE_URL + "#alignment_display_2d";
				if ( !Utilities.tryOpenURL(applet, url) )
					System.err.println("Error opening URL: " + url);
			}
		});

		GridBagLayout gridbag = new GridBagLayout();
		GridBagConstraints constraints = new GridBagConstraints();
		setLayout(gridbag);
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.gridheight = 1;
		constraints.ipadx = 5;
		constraints.ipady = 8;

		if (hc != null /*&& HISTORYCONTROL*/) {
			addToGrid(this, gridbag, constraints, homeButton, 1);
			//addToGrid(this, gridbag, constraints, resetButton, 1); // mdb removed 2/18/09	

			addToGrid(this, gridbag, constraints, new JLabel(), 1);
			addToGrid(this, gridbag, constraints, new JSeparator(SwingConstants.VERTICAL), 1);
			//addToGrid(this, gridbag, constraints, new JLabel(), 1); 		// mdb removed 2/18/09

			//addToGrid(this, gridbag, constraints, doubleBackButton, 1); 	// mdb removed 5/20/09
			addToGrid(this, gridbag, constraints, backButton, 1);
			addToGrid(this, gridbag, constraints, forwardButton, 1);

			addToGrid(this, gridbag, constraints, new JLabel(), 1);
			addToGrid(this, gridbag, constraints, new JSeparator(SwingConstants.VERTICAL), 1);
			//addToGrid(this, gridbag, constraints, new JLabel(), 1); 	// mdb removed 2/18/09
		}

		//if (ZOOMCONTROL) {
			//addToGrid(this, gridbag, constraints, dDownButton, 1); 	// mdb removed 1/29/09
			addToGrid(this, gridbag, constraints, downButton, 1);
			addToGrid(this, gridbag, constraints, upButton, 1);
			//addToGrid(this, gridbag, constraints, dUpButton, 1); 		// mdb removed 1/29/09
			//addToGrid(this, gridbag, constraints, new JLabel(), 1); 	// mdb removed 1/29/09
			//addToGrid(this, gridbag, constraints, new JSeparator(SwingConstants.VERTICAL), 1); // mdb removed 1/29/09
			//addToGrid(this, gridbag, constraints, new JLabel(), 1); 	// mdb removed 1/29/09

			addToGrid(this, gridbag, constraints, scaleButton, 1);

			addToGrid(this, gridbag, constraints, new JLabel(), 1);
			addToGrid(this, gridbag, constraints, new JSeparator(SwingConstants.VERTICAL), 1);
			//addToGrid(this, gridbag, constraints, new JLabel(), 1); 	// mdb removed 2/18/09
		//}
			
		// mdb added 4/21/09 #161
		addToGrid(this, gridbag, constraints, new JLabel("Mouse:"), 1);
		addToGrid(this, gridbag, constraints, createMouseFunctionSelector(bar), 1);
		addToGrid(this, gridbag, constraints, new JLabel(), 1);
		addToGrid(this, gridbag, constraints, new JSeparator(SwingConstants.VERTICAL), 1);
		
		//if (PRINTCONTROL) 
		addToGrid(this, gridbag, constraints, showImageButton, 1);

		if (cdh != null /*&& CHANGECOLORS*/) addToGrid(this,gridbag,constraints,editColorsButton,1);
		if (helpButton != null) addToGrid(this,gridbag,constraints, helpButton,1);
		
		// mdb removed 5/20/09
//		if (/*EXITCONTROL*/hasExitButton) 
//			addToGrid(this,gridbag,constraints,exitButton,GridBagConstraints.REMAINDER);
	}
	
	private ActionListener buttonListener = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			final Object source = e.getSource();
			if (source == scaleButton)           dp.drawToScale();
			//else if (source == dDownButton)    dp.changeZoomFactor(DDOWN_FACTOR); // mdb removed 1/29/09	
			else if (source == downButton)       dp.changeAlignRegion(0.5);
			else if (source == upButton)         dp.changeAlignRegion(2.0);
			//else if (source == dUpButton)      dp.changeZoomFactor(DUP_FACTOR); // mdb removed 1/29/09	
			else if (source == editColorsButton) cdh.show();
			else if (source == showImageButton)  imageViewer.showImage(dp);
//mdb removed 5/20/09
//			else if (source == exitButton) {
//				//dp.getFrame().hide(); // mdb removed 6/29/07 #118
//				dp.getFrame().setVisible(false); // mdb added 6/29/07 #118
//			}
		}
	};

// mdb unused 2/18/09	
//	public HistoryControl getHistoryControl() {
//		return hc;
//	}

	/**
	 * Enables/disables all of the buttons on the panel except for the help button.
	 * 
	 * @param enable
	 */
	public void setPanelEnabled(boolean enable) {
		scaleButton.setEnabled(enable);
		showImageButton.setEnabled(enable);
		editColorsButton.setEnabled(enable);

		//dDownButton.setEnabled(enable); 	// mdb removed 1/29/09
		downButton.setEnabled(enable);
		upButton.setEnabled(enable);
		//dUpButton.setEnabled(enable); 	// mdb removed 1/29/09
		if (hc != null) hc.setEnabled(enable);
	}

	private void addToGrid(Container cp, GridBagLayout layout,
			GridBagConstraints constraints, Component comp, int width) 
	{
		constraints.gridwidth = width;
		layout.setConstraints(comp, constraints);
		cp.add(comp);
	}
	
	// mdb added 3/30/07 #112
	public String getHelpText(MouseEvent event) { 
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
	
	// mdb added 4/21/09 #161 - add mouse function buttons to Control Panel
	public static final String MOUSE_FUNCTION_CLOSEUP 		= "Base View";
	public static final String MOUSE_FUNCTION_ZOOM_SINGLE 	= "Zoom";
	public static final String MOUSE_FUNCTION_ZOOM_ALL 		= "Zoom All";
	private Component createMouseFunctionSelector(HelpBar bar) {
		JComboBox comboMouseFunctions = new JComboBox(
			new String[] {
				MOUSE_FUNCTION_CLOSEUP,
				MOUSE_FUNCTION_ZOOM_SINGLE,
				MOUSE_FUNCTION_ZOOM_ALL
				//"Move" // mdb removed 6/24/09
		} );
		
		comboMouseFunctions.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
		        JComboBox cb = (JComboBox)e.getSource();
		        String item = (String)cb.getSelectedItem();
		        dp.setMouseFunction(item);
			}
		});
		
		comboMouseFunctions.setSelectedIndex(2); // default to "zoom all"
		comboMouseFunctions.setName("Set the function of the left mouse button (click+drag)."); // set help text
		if (bar != null) bar.addHelpListener(comboMouseFunctions,this);
		
		return comboMouseFunctions;
	}
}
