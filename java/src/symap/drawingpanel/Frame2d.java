package symap.drawingpanel;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.KeyEvent;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import props.PersistentProps;
import symap.Globals;
import symap.frame.HelpBar;
import util.Utilities;

/***********************************************************
 * SyMAP2d creates the components of 2D display, and this puts them together
 * CAS534 renamed from SyMAPFrame=> Frame2d; remove 'implement DrawingPanelListener'
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class Frame2d extends FSized2d 
{	
	private static final String DISPLAY_SIZE_COOKIE = "SyMAPDisplaySize";
	private static final String DISPLAY_POSITION_COOKIE = "SyMAPDispayPosition";

	private ControlPanel cp;
	private DrawingPanel dp;
	private SyMAP2d symap2d;
	
	public Frame2d(SyMAP2d symap2d, ControlPanel cp, DrawingPanel dp, HelpBar hb, 
			boolean showHelpBar, PersistentProps persistentProps) {
		super("SyMAP "+ Globals.VERSION);
		
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.symap2d = symap2d;
		this.cp = cp;
		this.dp = dp;
		
		if (persistentProps != null) {  // CAS517 was checking for null DISPLAY_ values
			setSizeProp(persistentProps.copy(DISPLAY_SIZE_COOKIE));
			setLocationProp(persistentProps.copy(DISPLAY_POSITION_COOKIE));
		}
		dp.setListener(this);

		Container container = getContentPane();
		container.setLayout(new BorderLayout());

		JPanel bottomPanel = new JPanel(new BorderLayout());
		if (cp != null) 
			bottomPanel.add(cp,BorderLayout.NORTH);
		bottomPanel.add(dp.getView(),BorderLayout.CENTER);

		container.add(bottomPanel,BorderLayout.CENTER);
		if (hb != null && showHelpBar) 
			container.add(hb, BorderLayout.SOUTH);

		setSizeAndLocationByProp(Utilities.getScreenBounds(this));

		addKeyAndContainerListenerRecursively(this);
	}
	// CAS541 add this so can close connection
	public void dispose() { // override
		symap2d.clear();
		super.dispose();
	}
	public void showX() {// CAS512 had to rename show() to something different
		try {
			if (isShowing()) dp.closeFilters();
			Dimension d = new Dimension();
			int nMaps = dp.getNumMaps();
			int nAnnots = dp.getNumAnnots();
			int width = Math.min(1000, 300 + 300*nMaps + 400*nAnnots);
			d.setSize(width, 900);
			setSize(d);
			super.setVisible(true); // CAS512 super.show();
			if (dp.tracksSet())
				dp.amake();
			else
				setFrameEnabled(false);
		}
		catch (Exception e) {e.printStackTrace();}
	}

	public void hideX() {
		dp.closeFilters();
		super.setVisible(false); // CAS512 super.hide(); 
	}

	public void setFrameEnabled(boolean enable) {
		if (enable) {
			dp.getParent().setCursor(Globals.DEFAULT_CURSOR); 
			// CAS521 does nothing if (hb != null) hb.setBusy(false, this);
		}
		else {
			dp.getParent().setCursor(Globals.WAIT_CURSOR); 
			// CAS521 does nothing  if (hb != null) hb.setBusy(true, this);
		}
		if (cp != null) cp.setPanelEnabled(enable);
		dp.setVisible(enable);
	}

	public void displayWarning(String message) {
		// CAS dead if (hb != null) hb.addWarningMessage(message);
		System.err.println("Warning: " + message);
		JOptionPane.showMessageDialog(this, message, "Warning" ,JOptionPane.WARNING_MESSAGE);
	}
	public void displayError(String message) {
		System.err.println("Error: " + message);
		JOptionPane.showMessageDialog(this, message, "Error" ,JOptionPane.ERROR_MESSAGE);
	}

	public Frame getFrame() 				{return (Frame)this;}

	public ControlPanel getControlPanel() 	{return cp;}

	public DrawingPanel getDrawingPanel() 	{return dp;}

	public void keyPressed(KeyEvent e) {
		if (e.isAltDown() && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C && dp != null)
			dp.resetData();
	}
}
