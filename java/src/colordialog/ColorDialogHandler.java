package colordialog;

import java.util.Vector;
import java.util.Iterator;

import java.lang.ref.WeakReference;

import util.PropertiesReader;
//import util.HelpHandler;
import props.PersistentProps;

/**
 * Class <code>ColorDialogHandler</code> handles the showing and setting of colors of a color dialog box.  
 * @see ColorDialog
 */
public class ColorDialogHandler {

	private Vector<WeakReference<ColorListener>> listeners;

	private PersistentProps cookie;
	private PropertiesReader props;

	private ColorDialog dialog = null;

	/**
	 * Creates a new <code>ColorDialogHandler</code> instance.
	 *
	 * @param cookie a <code>PersistentProps</code> value of the persistent props handler if desired
	 * @param help a <code>HelpHandler</code> value of the help handler if the help button should be enabled
	 * @param props a <code>PropertiesReader</code> value of the properties file to be used to set up the ColorDialog
	 * @see ColorDialog
	 */
	public ColorDialogHandler(PersistentProps cookie, 
			PropertiesReader props) 
	{
		listeners = new Vector<WeakReference<ColorListener>>();
		this.cookie = cookie;
		this.props = props;
	}

	/**
	 * Method <code>show</code> shows the dialog, first creating it if the color dialog hasn't been
	 * shown before and notifies the listeners.
	 */
	public void showX() {
		if (dialog == null) {
			dialog = new ColorDialog(props,cookie/*,help*/);
			dialog.setColors();
		}
		// CAS512 dialog.show();
		dialog.setVisible(true);
		notifyListeners();
	}

	/**
	 * Method <code>setColors</code> sets the colors of the
	 * color dialog and notifies the listeners.
	 *
	 */
	public void setColors() {
		if (dialog != null) {
			dialog.setVisible(false); 
			dialog.defaultAction();
			dialog.okAction();
		}
		dialog = new ColorDialog(props,cookie/*,help*/);
		dialog.setColors();
		notifyListeners();
	}

	/**
	 * Method <code>addListener</code> adds listener to the list of objects listening to this dialog.  The
	 * listener's resetColors() method is called when the colors change.
	 *
	 * @param listener a <code>ColorListener</code> value
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

	/**
	 * Method <code>removeListener</code> removes the given listener from the list of listeners
	 *
	 * @param listener a <code>ColorListener</code> value
	 */
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

	/**
	 * Method <code>removeAllListeners</code> removes all of the listeners.
	 *
	 */
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

