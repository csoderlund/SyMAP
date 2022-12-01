package colordialog;

import java.util.Vector;
import java.util.Iterator;

import java.lang.ref.WeakReference;

import util.PropertiesReader;
//import util.HelpHandler;
import props.PersistentProps;
import symap.SyMAP;

/**
 * Class ColorDialogHandler handles the showing and setting of colors of a color dialog box.  
 * @see ColorDialog and SyMAP.java (caller)
 */
public class ColorDialogHandler {

	private Vector<WeakReference<ColorListener>> listeners;

	private PersistentProps cookie;
	private PropertiesReader props;
	private ColorDialog dialog = null;
	private final String propsFile = "/properties/colors.properties"; // CAS521 moved from SyMAP.java
	
	/**
	 * @param cookie - in user's directory file .symap_saved_props
	 * @param props - java/src/properties
	 */
	public ColorDialogHandler(PersistentProps cookie)  { //CAS521 remove hasFPC
		props = new PropertiesReader(SyMAP.class.getResource(propsFile));
		listeners = new Vector<WeakReference<ColorListener>>();
		this.cookie = cookie;
		setColors(); // CAS521 moved from SyMAP.java to here
	}
	
	public void showX() {
		if (dialog == null) {
			dialog = new ColorDialog(props,cookie);
			dialog.setColors();
		}
		dialog.setVisible(true); // CAS512 dialog.show();
		notifyListeners();
	}
	public void setColors() {
		if (dialog != null) {
			dialog.setVisible(false); 
			dialog.defaultAction();
			dialog.okAction();
		}
		dialog = new ColorDialog(props,cookie);
		dialog.setColors();
		notifyListeners();
	}

	/**
	 * Method addListener adds listener to the list of objects listening to this dialog.  The
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

