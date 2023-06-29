package history;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.AbstractButton;

/**
 * Class HistoryControl can be the control point for a History object.
 * It will update the buttons when needed and call HistoryListener.setHistory(Object) when
 * a button is pressed.
 *
 * A user can choose which buttons they want to be used by just passing null values in for
 * the undesired buttons.
 *
 * HistoryControl does not set the clear button given, if any, enabled or disabled.  It simply
 * calls history.clear() when it's been clicked and updates the other buttons (if any).
 *
 * @see ActionListener, History, HistoryListener
 */
public class HistoryControl implements ActionListener {

	private HistoryListener listener;
	private AbstractButton home, reset, dBack, back, forward, clear;
	private History history;

	public HistoryControl(History history) { // SyMAP2d
		this.history = history;
	}

	public boolean isEmpty() {return history.isEmpty();}

	// only home, back and forward are used
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

	public synchronized void setListener(HistoryListener listener) {
		this.listener = listener;
	}

	public void add(Object obj, boolean reset) {
		history.add(obj,reset);
		setButtons();
	}

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

	private synchronized void setButtons() {
		if (home    != null) home.setEnabled(history.isHome());
		if (reset   != null) reset.setEnabled(history.isReset());
		if (dBack   != null) dBack.setEnabled(history.isDBack());
		if (back    != null) back.setEnabled(history.isBack());
		if (forward != null) forward.setEnabled(history.isForward());
	}

	/**
	 * Receives an action from a button and calls HistoryListener.setHistory(Object) passing in the new current history.
	 * The buttons are updated to be enabled/disabled based on if they are applicable.
	 */
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		Object obj = null;
		if (source == home) 		{obj = history.goHome();}
		else if (source == reset) 	{obj = history.goReset();}
		else if (source == dBack) 	{obj = history.goDBack();}
		else if (source == back) 	{obj = history.goBack();}
		else if (source == forward) {obj = history.goForward();}
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
