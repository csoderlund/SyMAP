package util;

import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.GraphicsConfiguration;
import java.awt.event.ComponentListener;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import javax.swing.JFrame;
import props.PersistentProps;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SizedJFrame extends JFrame implements KeyListener, ContainerListener {

	private PersistentProps sizeProp, positionProp;

	public SizedJFrame() {
		super();
		addComponentListener(new CompListener());
	}

	public SizedJFrame(GraphicsConfiguration gc) {
		super(gc);
		addComponentListener(new CompListener());
	}

	public SizedJFrame(String title) {
		super(title);
		addComponentListener(new CompListener());
	}

	public SizedJFrame(String title, GraphicsConfiguration gc) {
		super(title,gc);
		addComponentListener(new CompListener());
	}

	public void setSizeAndLocationByProp(Rectangle defaultRect) {
		setLocationByProp(new Point(defaultRect.x,defaultRect.y));
		setSizeByProp(new Dimension(defaultRect.width,defaultRect.height));
	}

	public void setSizeByProp(Dimension defaultSize) {
		Dimension dim = getDisplayProp(sizeProp);
		if (dim != null) setSize(dim);
		else             setSize(defaultSize);
	}

	public void setLocationByProp(Point defaultLocation) {
		Dimension dim = getDisplayProp(positionProp);
		if (dim != null) setLocation(new Point(dim.width,dim.height));
		else             setLocation(defaultLocation);
	}

	public void setSizeAndLocationProp(PersistentProps baseProp, String sizePropName, String locationPropName) {
		setSizeProp(baseProp.copy(sizePropName));
		setLocationProp(baseProp.copy(locationPropName));
	}

	public void setLocationProp(PersistentProps prop) {
		positionProp = prop;
	}

	public void setSizeProp(PersistentProps prop) {
		sizeProp = prop;
	}

	public PersistentProps getLocationProp() {
		return positionProp;
	}

	public PersistentProps getSizeProp() {
		return sizeProp;
	}

	protected void addKeyAndContainerListenerRecursively(Component c) {
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

	protected void removeKeyAndContainerListenerRecursively(Component c) {
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

	public void keyPressed(KeyEvent e) { }
	public void keyReleased(KeyEvent e) { }
	public void keyTyped(KeyEvent e) { }

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
	}

	private Dimension getDisplayProp(PersistentProps prop) {
		if (prop == null) return null;

		Dimension dim = null;
		String ds = prop.getProp();
		if (ds != null && ds.length() > 0) {
			int ind = ds.indexOf(',');
			if (ind >= 0) {
				try {
					int w = new Integer(ds.substring(0,ind)).intValue();
					int h = new Integer(ds.substring(ind+1)).intValue();
					dim = new Dimension(w,h);
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return dim;
	}

	private void setDisplayProp(PersistentProps prop, Point p) {
		setDisplayProp(prop,new Dimension(p.x,p.y));
	}

	private void setDisplayProp(PersistentProps prop, Dimension dim) {
		if (prop != null) {
			if (dim != null) {
				String ds = (new Integer(dim.width))+","+(new Integer(dim.height));
				prop.setProp(ds);
			}
			else System.err.println("Dimension is null in SizedJFrame#setDisplayProp(PersistentProps,Dimension)!!!!");
		}
	}

}
