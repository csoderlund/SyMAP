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
		super("SyMAP "+SyMAP.VERSION);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.cp = cp;
		this.dp = dp;
		this.hb = hb;
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

	public Frame getFrame() 				{return (Frame)this;}

	public ControlPanel getControlPanel() 	{return cp;}

	public DrawingPanel getDrawingPanel() 	{return dp;}

	public void keyPressed(KeyEvent e) {
		if (e.isAltDown() && e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C && dp != null)
			dp.resetData();
	}
}
