package colordialog;

import java.util.Vector;
import java.util.Iterator;
import java.lang.ref.WeakReference;

import props.PersistentProps;

/**
 * The shows and setting of colors of a color dialog box.  
 */
public class ColorDialogHandler {

	private Vector<WeakReference<ColorListener>> listeners;

	private PersistentProps cookie; // read from file
	private ColorDialog dialog = null;
	
	/**
	 * Created in circview.ControlPanelCirc, dotplot.DotPlotFrame, drawingPanel.SyMAP2d
	 * cookie - in user's directory file .symap_saved_props
	 */
	public ColorDialogHandler(PersistentProps cookie)  { 
		listeners = 	new Vector<WeakReference<ColorListener>>();
		this.cookie  =  cookie;
		setColors(); 									
	}
	// frame.ControlPanel calls when color icon clicked
	public void showX() {
		if (dialog == null) dialog = new ColorDialog(cookie); 					
		dialog.setVisible(true); 						
		notifyListeners();
	}
	
	public void setDotPlot() {
		dialog.setDotplot();
	}
	public void setCircle() {
		dialog.setCircle();
	}
	private void setColors() {
		if (dialog != null) {
			dialog.setVisible(false); 
			dialog.defaultAction();
			dialog.okAction();
		}
		dialog = new ColorDialog(cookie); 
		notifyListeners();
	}

	/* adds listener to the list of objects listening to this dialog.  The
	 * listener's resetColors() method is called when the colors change.
	 */
	public void addListener(ColorListener listener) {
		ColorListener l;
		Iterator<WeakReference<ColorListener>> iter = listeners.iterator();
		while (iter.hasNext()) {
			l = iter.next().get();
			if (l == null) iter.remove();
			else if (l.equals(listener)) return ;
		}
		listeners.add(new WeakReference<ColorListener>(listener));
	}
	
	public void removeListener(ColorListener listener) {
		ColorListener l;
		Iterator<WeakReference<ColorListener>> iter = listeners.iterator();
		while (iter.hasNext()) {
			l = iter.next().get();
			if (l == null) iter.remove();
			else if (l.equals(listener)) {
				iter.remove();
				break;
			}
		}
	}

	public void removeAllListeners() {
		listeners.clear();
	}

	private synchronized void notifyListeners() {
		Iterator<WeakReference<ColorListener>> iter = listeners.iterator();
		ColorListener l;
		while (iter.hasNext()) {
			l = iter.next().get();
			if (l == null) iter.remove();
			else l.resetColors();
		}
	}
}

