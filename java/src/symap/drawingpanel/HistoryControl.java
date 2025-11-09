package symap.drawingpanel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Vector;

import javax.swing.JButton;

/**
 * Create and control History Events. Created in SyMAP2d
 */
public class HistoryControl implements ActionListener {
	private HistoryListener listener;
	private JButton home, back, forward;
	private History history;
	
	public HistoryControl() { this.history = new History(); }// SyMAP2d
	
	public synchronized void setListener(HistoryListener listener) { // SyMAP2d
		this.listener = listener;
	}
	
	protected synchronized void setButtons(JButton home, JButton back, JButton forward) { // Control panel
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

	protected void replace(Object obj) { 
		history.replace(obj);
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
	protected void clear() {history.clear();} // called from SyMAP2d did not need History object
	protected int nBack() {return history.index;}
	protected int nForward() {
		return history.historyVec.size()-history.index-1;}
	/**
	 * Class History stores a history list with a current pointer.
	 * If it goes Home or back, then a new one is added, all remaining are lost but not freed.
	 */
	private class History {
		private Vector<Object> historyVec = new Vector <Object> (50);
		private int index = -1;
		private int size = 50; 

		private History() { } 
			
		private synchronized void add(Object obj) {
			historyVec.setSize(++index);
			historyVec.add(obj);
			if (historyVec.size() > size) {
				historyVec.remove(0);
				index--;
			}
		}
		private synchronized void replace(Object obj) {
			historyVec.set(index, obj);
		}
		private synchronized Object goHome() { // HistoryControl.actionPerformed
			if (index > 0) {
				index = 0;
				return historyVec.get(0);
			}
			return null;
		}
		private synchronized Object goBack() { // HistoryControl.actionPerformed
			if (index > 0) {
				--index;
				return historyVec.get(index);
			}
			return null;
		}
		private synchronized Object goForward() {// HistoryControl.actionPerformed
			if (historyVec.size() > index+1) {
				++index;
				return historyVec.get(index);
			}
			return null;
		}
		
		protected synchronized boolean isHome() {return index > 0;} 
		protected synchronized boolean isBack() {return index > 0;} 
		protected synchronized boolean isForward() {return historyVec.size() > index+1;}

		private synchronized void clear() { // SyMAP2.HistoryControl.clear, HistoryControl.actionPerformed
			index = -1;
			historyVec.clear();
		}
	}
}
