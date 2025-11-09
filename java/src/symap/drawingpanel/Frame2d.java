package symap.drawingpanel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import props.PersistentProps;
import symap.Globals;
import symap.frame.HelpBar;
import util.ErrorReport;

/***********************************************************
 * The 2D frame containing the drawingpanel, controlpanel and optional helpbar if on the bottom
 * SyMAP2d creates the components of 2D display, and this puts them together
 */
public class Frame2d extends JFrame implements KeyListener, ContainerListener{	
	private static final long serialVersionUID = 1L;
	private static final String DISPLAY_SIZE_COOKIE = "SyMAPDisplaySize";
	private static final String DISPLAY_POSITION_COOKIE = "SyMAPDispayPosition";

	private PersistentProps sizeProp, positionProp;
	
	private ControlPanel cp;
	private DrawingPanel dp;
	private SyMAP2d symap2d;
	
	public Frame2d(SyMAP2d symap2d, ControlPanel cp, DrawingPanel dp, HelpBar hb, 
			boolean showHelpBar, PersistentProps persistentProps) {
		super("SyMAP "+ Globals.VERSION);
		addComponentListener(new CompListener());
		
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.symap2d = symap2d;
		this.cp = cp;
		this.dp = dp;
		
		if (persistentProps != null) {  
			sizeProp = persistentProps.copy(DISPLAY_SIZE_COOKIE);
			positionProp = persistentProps.copy(DISPLAY_POSITION_COOKIE);
		}
		dp.setListener(this);
		dp.setCntl(cp); // so drawing panel has access for history

		Container container = getContentPane();
		container.setLayout(new BorderLayout());

		JPanel bottomPanel = new JPanel(new BorderLayout());
		bottomPanel.add(cp,BorderLayout.NORTH);
		bottomPanel.add(dp.getView(),BorderLayout.CENTER);

		container.add(bottomPanel,BorderLayout.CENTER);
		if (hb != null && showHelpBar) 		// Otherwise, put in Explorer on left
			container.add(hb, BorderLayout.SOUTH);

		setSizeAndLocationByProp(util.Jcomp.getScreenBounds(this));

		addKeyAndContainerListenerRecursively(this);
	}
	// Called for non-Explorer 2d: so can close connection
	public void dispose() { // override
		symap2d.clear();
		super.dispose();
	}
	public void showX() {// For non-explorer 2d; had to rename show() to something different
		try {
			if (isShowing()) dp.closeFilters();
			
			Dimension d = new Dimension();
			int nMaps = dp.getNumMaps();
			int nAnnots = dp.getNumAnnots();
			int width = Math.min(1000, 300 + 300*nMaps + 130*nAnnots); 
			d.setSize(width, 900);	 
			setSize(d);
			super.setVisible(true); // Has to be displayed first, or the build will not happen
			
			// sort of fixes DotPlot Sequence.buildGraphics timing problem; it displays a flash of window
			if (!dp.amake()) super.setVisible(false); 
		}
		catch (Exception e) {ErrorReport.print(e, "Show non-explorer 2d");}
	}

	public void hideX() {
		dp.closeFilters();
		super.setVisible(false); 
	}

	protected void setFrameEnabled(boolean enable) { // DrawingPanel and Frame2d
		if (enable) dp.getParent().setCursor(Globals.DEFAULT_CURSOR); 
		else 		dp.getParent().setCursor(Globals.WAIT_CURSOR); 
	
		if (cp != null) cp.setPanelEnabled(enable);
		
		dp.setVisible(enable);
	}

	protected Frame getFrame() 				{return (Frame)this;}

	/*********************************************************/
	private void setSizeAndLocationByProp(Rectangle defaultRect) {
		Dimension dimP = getDisplayProp(positionProp);
		if (dimP != null) setLocation(new Point(dimP.width,dimP.height));
		else              setLocation(new Point(defaultRect.x,defaultRect.y));
		
		Dimension dimS = getDisplayProp(sizeProp);
		if (dimS != null) setSize(dimS);
		else              setSize(new Dimension(defaultRect.width,defaultRect.height));
	}
	
	private void addKeyAndContainerListenerRecursively(Component c) {
		c.removeKeyListener(this);
		c.addKeyListener(this);
		if (c instanceof Container){
			Container cont = (Container)c;
			cont.removeContainerListener(this);
			cont.addContainerListener(this);
			Component[] children = cont.getComponents();
			for(int i = 0; i < children.length; i++){
				addKeyAndContainerListenerRecursively(children[i]);
			}
		}
	}

	private void removeKeyAndContainerListenerRecursively(Component c) {
		c.removeKeyListener(this);
		if(c instanceof Container){
			Container cont = (Container)c;
			cont.removeContainerListener(this);
			Component[] children = cont.getComponents();
			for(int i = 0; i < children.length; i++){
				removeKeyAndContainerListenerRecursively(children[i]);
			}
		}
	}

	public void componentAdded(ContainerEvent e) {
		addKeyAndContainerListenerRecursively(e.getChild());
	}

	public void componentRemoved(ContainerEvent e) {
		removeKeyAndContainerListenerRecursively(e.getChild());
	}
	public void keyReleased(KeyEvent e) { }
	public void keyTyped(KeyEvent e) { }
	public void keyPressed(KeyEvent e) { }

	private class CompListener implements ComponentListener {
		public void componentMoved(ComponentEvent e) { 
			setDisplayProp(positionProp,getLocation());
		}
		public void componentResized(ComponentEvent e) { 
			setDisplayProp(positionProp,getLocation());
			setDisplayProp(sizeProp,getSize());
		}
		public void componentHidden(ComponentEvent e) { }
		public void componentShown(ComponentEvent e) { }
		
		private void setDisplayProp(PersistentProps prop, Point p) {
			setDisplayProp(prop,new Dimension(p.x,p.y));
		}
		private void setDisplayProp(PersistentProps prop, Dimension dim) {
			if (prop != null) {
				if (dim != null) {
					String ds = dim.width + "," + dim.height;
					prop.setProp(ds);
				}
				else System.err.println("Dimension is null in Frame2d#setDisplayProp (PersistentProps,Dimension)!!!!");
			}
		}
	}
		
	private Dimension getDisplayProp(PersistentProps prop) {
		if (prop == null) return null;

		Dimension dim = null;
		String ds = prop.getProp();
		if (ds != null && ds.length() > 0) {
			int ind = ds.indexOf(',');
			if (ind >= 0) {
				try {
					int w = Integer.parseInt(ds.substring(0,ind)); 
					int h = Integer.parseInt(ds.substring(ind+1));
					dim = new Dimension(w,h);
				}
				catch (Exception e) {ErrorReport.print(e, "get display plops");}
			}
		}
		return dim;
	}
}
