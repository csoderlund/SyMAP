package history;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractButton;

/**
 * Class <code>HistoryControl</code> can be the control point for a History object.
 * It will update the buttons when needed and call HistoryListener.setHistory(Object) when
 * a button is pressed.
 *
 * A user can choose which buttons they want to be used by just passing null values in for
 * the undesired buttons.
 *
 * HistoryControl does not set the clear button given, if any, enabled or disabled.  It simply
 * calls history.clear() when it's been clicked and updates the other buttons (if any).
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see ActionListener
 * @see History
 * @see HistoryListener
 */
public class HistoryControl implements ActionListener {

	private HistoryListener listener;
	private AbstractButton home, reset, dBack, back, forward, clear;
	private History history;

	/**
	 * Creates a new <code>HistoryControl</code> instance with all of it's buttons set to null.
	 *
	 * @param history a <code>History</code> value
	 */
	public HistoryControl(History history) {
		this.history = history;
	}

	/**
	 * Creates a new <code>HistoryControl</code> instance. All the AbstractButton arguments are optional.
	 * However, if they are all null then HistoryListener.setHistory(Object) will never be called.
	 *
	 * @param listener a <code>HistoryListener</code> value
	 * @param history an <code>History</code> value of the history object to use
	 * @param home an <code>AbstractButton</code> value
	 * @param reset an <code>AbstractButton</code> value
	 * @param dBack an <code>AbstractButton</code> value for the double back button.
	 * @param back an <code>AbstractButton</code> value
	 * @param forward an <code>AbstractButton</code> value
	 * @param clear an <code>AbstractButton</code> value
	 */
	public HistoryControl(HistoryListener listener, History history, AbstractButton home, AbstractButton reset,
			AbstractButton dBack, AbstractButton back, AbstractButton forward, AbstractButton clear) {
		this.history = history;

		this.listener = listener;

		this.home    = home;
		this.reset   = reset;
		this.dBack   = dBack;
		this.back    = back;
		this.forward = forward;
		this.clear   = clear;

		setButtons();

		if (home    != null) home.addActionListener(this);
		if (reset   != null) reset.addActionListener(this);
		if (dBack   != null) dBack.addActionListener(this);
		if (back    != null) back.addActionListener(this);
		if (forward != null) forward.addActionListener(this);
		if (clear   != null) clear.addActionListener(this);
	}

	/**
	 * Creates a new <code>HistoryControl</code> instance. All the AbstractButton arguments are optional.
	 * However, if they are all null then HistoryListener.setHistory(Object) will never be called.
	 *
	 * @param listener a <code>HistoryListener</code> value
	 * @param size an <code>int</code> value of the maximum size of the History
	 * @param home an <code>AbstractButton</code> value
	 * @param reset an <code>AbstractButton</code> value
	 * @param dBack an <code>AbstractButton</code> value for the double back button.
	 * @param back an <code>AbstractButton</code> value
	 * @param forward an <code>AbstractButton</code> value
	 * @param clear an <code>AbstractButton</code> value
	 */
	public HistoryControl(HistoryListener listener, int size, AbstractButton home, AbstractButton reset,
			AbstractButton dBack, AbstractButton back, AbstractButton forward, AbstractButton clear) {
		history = new History(size);

		this.listener = listener;

		this.home    = home;
		this.reset   = reset;
		this.dBack   = dBack;
		this.back    = back;
		this.forward = forward;
		this.clear   = clear;

		setButtons();

		if (home    != null) home.addActionListener(this);
		if (reset   != null) reset.addActionListener(this);
		if (dBack   != null) dBack.addActionListener(this);
		if (back    != null) back.addActionListener(this);
		if (forward != null) forward.addActionListener(this);
		if (clear   != null) clear.addActionListener(this);
	}

	/**
	 * Method <code>isEmpty</code> returns true if the history is empty.
	 *
	 * @return a <code>boolean</code> value
	 * @see History#isEmpty()
	 */
	public boolean isEmpty() {
		return history.isEmpty();
	}

	/**
	 * Method <code>setButtons</code> changes the buttons. The buttons that aren't desired can be set to null.
	 *
	 * @param home an <code>AbstractButton</code> value
	 * @param reset an <code>AbstractButton</code> value
	 * @param dBack an <code>AbstractButton</code> value
	 * @param back an <code>AbstractButton</code> value
	 * @param forward an <code>AbstractButton</code> value
	 * @param clear an <code>AbstractButton</code> value
	 */
	public synchronized void setButtons(AbstractButton home, AbstractButton reset, AbstractButton dBack,
			AbstractButton back, AbstractButton forward, AbstractButton clear) {
		if (this.home != home) {
			if (this.home != null) this.home.removeActionListener(this);
			this.home = home;
			if (home != null) home.addActionListener(this);
		}
		if (this.reset != reset) {
			if (this.reset != null) this.reset.removeActionListener(this);
			this.reset = reset;
			if (reset != null) reset.addActionListener(this);
		}
		if (this.dBack != dBack) {
			if (this.dBack != null) this.dBack.removeActionListener(this);
			this.dBack = dBack;
			if (dBack != null) dBack.addActionListener(this);
		}
		if (this.back != back) {
			if (this.back != null) this.back.removeActionListener(this);
			this.back = back;
			if (back != null) back.addActionListener(this);
		}
		if (this.forward != forward) {
			if (this.forward != null) this.forward.removeActionListener(this);
			this.forward = forward;
			if (forward != null) forward.addActionListener(this);
		}
		if (this.clear != clear) {
			if (this.clear != null) this.clear.removeActionListener(this);
			this.clear = clear;
			if (clear != null) clear.addActionListener(this);
		}
	}

	/**
	 * Method <code>setListener</code> sets the listener of this history control.
	 *
	 * @param listener a <code>HistoryListener</code> value
	 */
	public synchronized void setListener(HistoryListener listener) {
		this.listener = listener;
	}

	/**
	 * Method <code>add</code> adds an object to the history, setting the buttons to
	 * enabled or disabled based on if they are now applicable.
	 *
	 * @param obj an <code>Object</code> value
	 */
// mdb unused 8/21/07
//	public void add(Object obj) {
//		add(obj,false);
//	}

	/**
	 * Method <code>add</code> adds an object to the history setting
	 * the object to be a reset point if reset is true. Also sets the buttons
	 * to enabled or disabled base on if they are now applicable.
	 *
	 * @param obj an <code>Object</code> value
	 * @param reset a <code>boolean</code> value
	 */
	public void add(Object obj, boolean reset) {
		history.add(obj,reset);
		setButtons();
	}

	/**
	 * Method <code>setEnabled</code> can be used to enable or disable all of the
	 * history buttons. When enabling, only the history buttons that are applicable
	 * at the time are enabled.
	 *
	 * @param enable a <code>boolean</code> value
	 */
	public synchronized void setEnabled(boolean enable) {
		if (!enable) {
			if (home    != null) home.setEnabled(false);
			if (reset   != null) reset.setEnabled(false);
			if (dBack   != null) dBack.setEnabled(false);
			if (back    != null) back.setEnabled(false);
			if (forward != null) forward.setEnabled(false);
		}
		else {
			setButtons();
		}
	}

	/**
	 * Method <code>setButtons</code> enables the buttons that are applicable and disables the others.
	 */
	public synchronized void setButtons() {
		if (home    != null) home.setEnabled(history.isHome());
		if (reset   != null) reset.setEnabled(history.isReset());
		if (dBack   != null) dBack.setEnabled(history.isDBack());
		if (back    != null) back.setEnabled(history.isBack());
		if (forward != null) forward.setEnabled(history.isForward());
	}

	/**
	 * Method <code>actionPerformed</code> receives an action from a button and 
	 * calls HistoryListener.setHistory(Object) passing in the new current history.
	 *
	 * The buttons are updated to be enabled/disabled based on if they are applicable.
	 *
	 * @param e an <code>ActionEvent</code> value
	 */
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		Object obj = null;
		if (source == home) {
			obj = history.goHome();
		}
		else if (source == reset) {
			obj = history.goReset();
		}
		else if (source == dBack) {
			obj = history.goDBack();
		}
		else if (source == back) {
			obj = history.goBack();
		}
		else if (source == forward) {
			obj = history.goForward();
		}
		else if (source == clear) {
			history.clear();
			setButtons();
		}
		if (obj != null) {
			setButtons();
			listener.setHistory(obj);
		}
	}
}
