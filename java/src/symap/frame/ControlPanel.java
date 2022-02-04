package symap.frame;

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
import javax.swing.JSeparator;
import javax.swing.JComboBox;
import javax.swing.SwingConstants;

import colordialog.ColorDialogHandler;
import history.HistoryControl;
import symap.SyMAP;
import symap.SyMAPConstants;
import symap.closeup.CloseUp;
import symap.drawingpanel.DrawingPanel;
import util.ImageViewer;
import util.Utilities;

/**
 * The top panel containing things such as the forward/back buttons, 
 * reset button, etc...
 * 
 * Handles the actions of those buttons through the arguments passed into 
 * the constructor.
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ControlPanel extends JPanel implements SyMAPConstants,
	HelpListener 
{								
	private JButton scaleButton;
	
	private JButton upButton, downButton;
	private JButton showImageButton, editColorsButton;
	
	private DrawingPanel dp;
	private HistoryControl hc;
	
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
	public ControlPanel(DrawingPanel dp, HistoryControl hc, ImageViewer iv, 
			ColorDialogHandler cdh, HelpBar bar)
	{
		super();
		this.dp = dp;
		this.hc = hc;
		this.cdh = cdh;

		JButton homeButton       = (JButton) Utilities.createButton(this,"/images/home.gif","Home: Go back in history to the first view",bar,null,false);
		JButton backButton       = (JButton) Utilities.createButton(this,"/images/back.gif","Back: Go back in history",bar,null,false);
		JButton forwardButton    = (JButton) Utilities.createButton(this,"/images/forward.gif","Forward: Go forward in history",bar,null,false);

		if (hc != null) hc.setButtons(homeButton,null/*resetButton*/,null/*doubleBackButton*/,backButton,forwardButton,null/*exitButton*/);

		downButton       = (JButton) Utilities.createButton(this,"/images/minus.gif","Shrink the alignment region",bar,buttonListener,false);
		upButton         = (JButton) Utilities.createButton(this,"/images/plus.gif","Grow the alignment region",bar,buttonListener,false);
		scaleButton      = (JButton) Utilities.createButton(this,"/images/scale.gif","Scale: Draw all of the tracks to BP scale",bar,buttonListener,false);
		showImageButton  = (JButton) Utilities.createButton(this,"/images/print.gif",
				"Save: Save as image.", bar,buttonListener,false);
		editColorsButton = (JButton) Utilities.createButton(this,"/images/colorchooser.gif","Colors: Edit the color settings",bar,buttonListener,false);
		
		JButton helpButton = null;
		
		helpButton = (JButton) Utilities.createButton(this,"/images/help.gif",
				"Help: Online documentation.",
				bar,null,false);
		helpButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String url = SyMAP.USER_GUIDE_URL + "#alignment_display_2d";
				if ( !Utilities.tryOpenURL(url) )
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

		if (hc != null) {
			addToGrid(this, gridbag, constraints, homeButton, 1);
			
			addToGrid(this, gridbag, constraints, new JLabel(), 1);
			addToGrid(this, gridbag, constraints, new JSeparator(SwingConstants.VERTICAL), 1);
			
			addToGrid(this, gridbag, constraints, backButton, 1);
			addToGrid(this, gridbag, constraints, forwardButton, 1);

			addToGrid(this, gridbag, constraints, new JLabel(), 1);
			addToGrid(this, gridbag, constraints, new JSeparator(SwingConstants.VERTICAL), 1);
		}	
		addToGrid(this, gridbag, constraints, downButton, 1);
		addToGrid(this, gridbag, constraints, upButton, 1);		
		addToGrid(this, gridbag, constraints, scaleButton, 1);
		addToGrid(this, gridbag, constraints, new JLabel(), 1);
		addToGrid(this, gridbag, constraints, new JSeparator(SwingConstants.VERTICAL), 1);
			
		addToGrid(this, gridbag, constraints, new JLabel("Selected:"), 1);
		addToGrid(this, gridbag, constraints, createMouseFunctionSelector(bar), 1);
		addToGrid(this, gridbag, constraints, new JLabel(), 1);
		addToGrid(this, gridbag, constraints, new JSeparator(SwingConstants.VERTICAL), 1);
		
		
		addToGrid(this, gridbag, constraints, showImageButton, 1);

		if (cdh != null) addToGrid(this,gridbag,constraints,editColorsButton,1);
		if (helpButton != null) addToGrid(this,gridbag,constraints, helpButton,1);
	}
	
	private ActionListener buttonListener = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			final Object source = e.getSource();
			if (source == scaleButton)           dp.drawToScale();
	
			else if (source == downButton)       dp.changeAlignRegion(0.5);
			else if (source == upButton)         dp.changeAlignRegion(2.0);

			else if (source == editColorsButton) cdh.show();
			else if (source == showImageButton)  ImageViewer.showImage(dp); // CAS507 made static
		}
	};
	/**
	 * Enables/disables all of the buttons on the panel except for the help button.
	 * 
	 * @param enable
	 */
	public void setPanelEnabled(boolean enable) {
		scaleButton.setEnabled(enable);
		showImageButton.setEnabled(enable);
		editColorsButton.setEnabled(enable);

		downButton.setEnabled(enable);
		upButton.setEnabled(enable);
		if (hc != null) hc.setEnabled(enable);
	}

	private void addToGrid(Container cp, GridBagLayout layout,
			GridBagConstraints constraints, Component comp, int width) 
	{
		constraints.gridwidth = width;
		layout.setConstraints(comp, constraints);
		cp.add(comp);
	}
	
	public String getHelpText(MouseEvent event) { 
		Component comp = (Component)event.getSource();
		return comp.getName();
	}
	public static final String MOUSE_FUNCTION_SEQ 			= "Show Sequence";
	public static final String MOUSE_FUNCTION_CLOSEUP 		= "Align (Max " + CloseUp.MAX_CLOSEUP_BP + "bp)";
	public static final String MOUSE_FUNCTION_ZOOM_SINGLE 	= "Zoom Selected Track";
	public static final String MOUSE_FUNCTION_ZOOM_ALL 		= "Zoom All Tracks";
	private Component createMouseFunctionSelector(HelpBar bar) {
		String [] labels = { // CAS504 change order and add _SEQ
				MOUSE_FUNCTION_ZOOM_ALL,
				MOUSE_FUNCTION_ZOOM_SINGLE,
				MOUSE_FUNCTION_CLOSEUP,
				MOUSE_FUNCTION_SEQ
		};
		JComboBox <String> comboMouseFunctions = new JComboBox <String> (labels); // CAS507 got rid on one warning
		
		comboMouseFunctions.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
		        JComboBox <String> cb = (JComboBox) e.getSource();
		        String item = (String)cb.getSelectedItem();
		        dp.setMouseFunction(item);
			}
		});
		
		comboMouseFunctions.setSelectedIndex(0); // default to "zoom all"
		comboMouseFunctions.setName("Set the function of the left mouse button (click+drag)."); // set help text
		if (bar != null) bar.addHelpListener(comboMouseFunctions,this);
		
		return comboMouseFunctions;
	}
}
