package symap.drawingpanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.AbstractButton;

/**
 * Class HistoryControl can be the control point for a History object.
 * It will update the buttons when needed and call HistoryListener.setHistory(Object) when
 * a button is pressed.
 * @see ActionListener, History, HistoryListener
 * 
 * CAS550 remove unused buttons for reset, double back, clear
 * 		  moved History to here. Moved to symap.drawingpanel
 */
public class HistoryControl implements ActionListener {

	private HistoryListener listener;
	private AbstractButton home, back, forward;
	private History history;

	public HistoryControl() { this.history = new History(); }// SyMAP2d
	public synchronized void setListener(HistoryListener listener) { // SyMAP2d
		this.listener = listener;
	}
	
	protected synchronized void setButtons(AbstractButton home, AbstractButton back, AbstractButton forward) {
		if (this.home != home) {
			if (this.home != null) this.home.removeActionListener(this);
			this.home = home;
			home.addActionListener(this);
		}
		if (this.back != back) {
			if (this.back != null) this.back.removeActionListener(this);
			this.back = back;
			back.addActionListener(this);
		}
		if (this.forward != forward) {
			if (this.forward != null) this.forward.removeActionListener(this);
			this.forward = forward;
			forward.addActionListener(this);
		}
	}

	protected void add(Object obj) {// the object is DrawingPanelData class within DrawingPanel
		history.add(obj);
		setButtons();
	}

	protected synchronized void setEnabled(boolean enable) { // ControlPanel
		if (!enable) {
			home.setEnabled(false);
			back.setEnabled(false);
			forward.setEnabled(false);
		}
		else {
			setButtons();
		}
	}

	private synchronized void setButtons() {
		home.setEnabled(history.isHome());
		back.setEnabled(history.isBack());
		forward.setEnabled(history.isForward());
	}

	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		Object obj = null;
		if (source == home) 		{obj = history.goHome();}
		else if (source == back) 	{obj = history.goBack();}
		else if (source == forward) {obj = history.goForward();}
		if (obj != null) {
			setButtons();
			listener.setHistory(obj);
		}
	}
	protected void clear() {history.clear();} // CAS550 added so SyMAP2d did not need History object
	/**
	 * Class History stores a history list with a current pointer.
	 * If it goes Home or back, then a new one is added, all remaining are lost but not freed.
	 * CAS550 Removed HistoryObject class because removed reset (only reason for it)
	 *        HistoryControl create history object instead of SyMAP2d  
	 *        Move this file to HistoryControl     
	 */
	private class History {
		private Vector<Object> history = new Vector <Object> (50);
		private int index = -1;
		private int size = 50; // CAS542 was 10, moved constant from SyMAP2d

		private History() { } 
			
		protected synchronized void add(Object obj) {
			history.setSize(++index);
			history.add(obj);
			if (history.size() > size) {
				history.remove(0);
				index--;
			}
		}
		protected synchronized Object goHome() { // HistoryControl.actionPerformed
			if (index > 0) {
				index = 0;
				return history.get(index);
			}
			return null;
		}
		protected synchronized Object goBack() { // HistoryControl.actionPerformed
			if (index > 0) {
				--index;
				return history.get(index);
			}
			return null;
		}
		private synchronized Object goForward() {// HistoryControl.actionPerformed
			if (history.size() > index+1) {
				++index;
				return history.get(index);
			}
			return null;
		}
		
		protected synchronized boolean isHome() {return index > 0;}
		protected synchronized boolean isBack() {return index > 0;}
		protected synchronized boolean isForward() {return history.size() > index+1;}

		protected synchronized void clear() { // SyMAP2.HistoryControl.clear, HistoryControl.actionPerformed
			index = -1;
			history.clear();
		}
	}

}
