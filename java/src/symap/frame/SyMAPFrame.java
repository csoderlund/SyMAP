package symap.frame;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.KeyEvent;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import props.PersistentProps;
import symap.SyMAP;
import symap.SyMAPConstants;
import symap.drawingpanel.DrawingPanel;
import symap.drawingpanel.DrawingPanelListener;
import util.Utilities;
import util.SizedJFrame;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SyMAPFrame extends SizedJFrame implements DrawingPanelListener
{	
	private static final String DISPLAY_SIZE_COOKIE = "SyMAPDisplaySize";
	private static final String DISPLAY_POSITION_COOKIE = "SyMAPDispayPosition";

	private ControlPanel cp;
	private DrawingPanel dp;
	private HelpBar hb;

	public SyMAPFrame(ControlPanel cp, DrawingPanel dp, HelpBar hb, 
			boolean showHelpBar, // for 3D
			PersistentProps persistentProps) {
		super(/*FRAME_TITLE*/"SyMAP "+SyMAP.VERSION);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.cp = cp;
		this.dp = dp;
		this.hb = hb;
		if (DISPLAY_SIZE_COOKIE != null && persistentProps != null)     
			setSizeProp(persistentProps.copy(DISPLAY_SIZE_COOKIE));
		if (DISPLAY_POSITION_COOKIE != null && persistentProps != null) 
			setLocationProp(persistentProps.copy(DISPLAY_POSITION_COOKIE));

		dp.setListener(this);

		Container container = getContentPane();
		container.setLayout(new BorderLayout());

		JPanel bottomPanel = new JPanel(new BorderLayout());
		if (cp != null /*&& HAS_CONTROL_PANEL*/) 
			bottomPanel.add(cp,BorderLayout.NORTH);
		bottomPanel.add(dp.getView(),BorderLayout.CENTER);

		container.add(bottomPanel,BorderLayout.CENTER);
		if (hb != null && showHelpBar/*&& HAS_HELPBAR*/) 
			container.add(hb, BorderLayout.SOUTH);

		setSizeAndLocationByProp(Utilities.getScreenBounds(this));

		addKeyAndContainerListenerRecursively(this);
	}

	public void show() {
		try {
			if (isShowing()) dp.closeFilters();
			Dimension d = new Dimension();
			int nMaps = dp.getNumMaps();
			int nAnnots = dp.getNumAnnots();
			int width = Math.min(1000, 300 + 300*nMaps + 400*nAnnots);
			d.setSize(width, 900);
			setSize(d);
			// setVisible(true);CAS507 doesn't work, and needs show
			super.show(); 
			if (dp.tracksSet())
				dp.amake();
			else
				setFrameEnabled(false);
		}
		catch (Exception e) {e.printStackTrace();}
	}

	public void hide() {
		dp.closeFilters();
		//dp.getPools().clearPools();
		super.hide(); 
		// setVisible(false);
	}

	/**
	 * Method <code>setFrameEnabled</code> sets the frame to be enabled/disabled.
	 *
	 * @param enable a <code>boolean</code> value
	 */
	public void setFrameEnabled(boolean enable) {
		if (enable) {
			dp.getParent().setCursor(SyMAPConstants.DEFAULT_CURSOR); 
			if (hb != null) hb.setBusy(false, this);
		}
		else {
			dp.getParent().setCursor(SyMAPConstants.WAIT_CURSOR); 
			if (hb != null) hb.setBusy(true, this);
		}
		if (cp != null) cp.setPanelEnabled(enable);
		dp.setVisible(enable);
	}

	public void displayWarning(String message) {
		if (hb != null) hb.addWarningMessage(message);
	}

	public void displayError(String message) {
		System.err.println("ERROR MESSAGE: "+message);
		JOptionPane.showMessageDialog(this,message,"Error",JOptionPane.ERROR_MESSAGE);
	}

	public Frame getFrame() {
		return (Frame)this;
	}

	public ControlPanel getControlPanel() {
		return cp;
	}

	public DrawingPanel getDrawingPanel() {
		return dp;
	}

	public void keyPressed(KeyEvent e) {
		if (e.isAltDown() && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C && dp != null)
			dp.resetData();
	}
}
