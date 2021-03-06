package symap.filter;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent; 
import java.awt.event.WindowFocusListener; 
import java.awt.event.WindowEvent; 

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener; 
import javax.swing.JPopupMenu; 				
import javax.swing.JMenuItem; 				

import symap.SyMAP;
import symap.SyMAPConstants;
import symap.drawingpanel.DrawingPanel;
import symap.frame.HelpBar;
import util.Utilities;

/**
 * The abstract class for the filter dialogs.
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public abstract class Filter extends JDialog implements ActionListener,
		ChangeListener, SyMAPConstants,
		PopupMenuListener 
{
	private JButton okButton, cancelButton, defaultButton;
	protected DrawingPanel drawingPanel;
	protected JPanel buttonPanel;
	protected HelpBar helpBar;
	protected JPopupMenu popup; 
	protected JMenuItem popupTitle, showNavigationHelp, showTrackHelp; 
	

	protected Filter(Frame owner, DrawingPanel dp, String title, AbstractButton helpButton) {
		super(owner,title,true);
		this.drawingPanel = dp;

		okButton = new JButton("Apply");
		cancelButton = new JButton("Cancel");
		defaultButton = new JButton("Defaults");

		okButton.addActionListener(this);
		cancelButton.addActionListener(this);
		defaultButton.addActionListener(this);

		buttonPanel = new JPanel(new BorderLayout());
		buttonPanel.add(new JSeparator(),BorderLayout.NORTH);
		JPanel innerPanel = new JPanel();
		innerPanel.add(okButton);
		innerPanel.add(cancelButton);
		innerPanel.add(defaultButton);
		if (helpButton != null) innerPanel.add(helpButton);
		buttonPanel.add(innerPanel,BorderLayout.CENTER);
		
		popup = new JPopupMenu(); 
		popup.addPopupMenuListener(this); 
		popupTitle = new JMenuItem();
		popupTitle.setEnabled(false);
		popup.add(popupTitle);
		popup.addSeparator();
		
		addWindowFocusListener(new WindowFocusListener() { 
			public void windowGainedFocus(WindowEvent e) { }
			public void windowLostFocus(WindowEvent e) { 
				// Force filter dialog to be "always on top".  Note that toString() 
				// returns null if window is not a child of SyMAP.
				if (e.getOppositeWindow() != null && 
					e.getOppositeWindow().toString().startsWith("symap.frame.SyMAPFrame"))
				{
					toFront();
				}
			}
		});
	}

	public void setHelpBar(HelpBar helpBar) {
		this.helpBar = helpBar;
	}

	protected boolean refresh() {
		boolean ret = true;
		if (isShowing()) { 
			if (SyMAP.DEBUG) System.out.println("Refreshing from Filter");
			if (drawingPanel != null) ret = drawingPanel.smake();
		}
		return ret;
	}

	/**
	 * Method <code>closeFilter</code> checks if the filter dialog is showing.  If it is, cancelAction()
	 * is called and the filter is hidden.
	 * @see #hide()
	 */
	public void closeFilter() {
		if (isShowing()) {
			cancelAction();
			hide();
		}
	}

	/**
	 * Method <code>show</code> sets the help bar to be paused before showing, and not paused after
	 * showing if the help bar is set.
	 */
	public void show() {
		if (helpBar != null) helpBar.setPaused(true,this);
		super.show();
		if (helpBar != null) helpBar.setPaused(false,this);
	}	
	
	public void showPopup(MouseEvent e) { 
		popup.show(e.getComponent(), e.getX(), e.getY());
	}	
		
	public void popupMenuCanceled(PopupMenuEvent event) { } 
	public void popupMenuWillBecomeVisible(PopupMenuEvent event) { } 
	public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { } 

	/**
	 * Method <code>hide</code> hides the dialog setting the help bar to not be paused on this object.
	 */
	public void hide() {
		if (helpBar != null) helpBar.setPaused(false,this);
		super.hide();
	}

	/**
	 * Method <code>stateChanged</code> does nothing
	 *
	 * @param event a <code>ChangeEvent</code> value
	 */
	public void stateChanged(ChangeEvent event) { }

	/**
	 * Hides the dialog and starts a new thread running run() if the source is the okButton.
	 * Sets the defaults if the source is the defaultButton.  Calls cancelAction()
	 * then hide() then enables the track drawingPanel if the source is the cancelButton.
	 * 
	 * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
	 */
	public void actionPerformed(ActionEvent event) {
		if (event.getSource() == okButton) { 
			if (drawingPanel != null) drawingPanel.setFrameEnabled(false);
			super.hide();
			new Thread(new RunOk()).start();
		}
		else if (event.getSource() == defaultButton) setDefault();
		else if (event.getSource() == cancelButton) {
			cancelAction();
			hide();
			if (drawingPanel != null) drawingPanel.setFrameEnabled(true);
		}
	}

	private void superShow() {
		super.show();
	}

	private class RunOk implements Runnable {

		public RunOk() { }

		public void run() {
			if (drawingPanel != null) {
				try {
					if (okAction()) {
						drawingPanel.setUpdateHistory();
					}
					if (!drawingPanel.smake()) superShow();
					else hide();
				} catch (Exception e) {
					Utilities.showErrorMessage(e.getMessage(), -1);		
					superShow();
				}
				
			}
		}
	}

	protected void addToGrid(Container cp, GridBagLayout layout,
			GridBagConstraints constraints, Component comp, int width) {
		constraints.gridwidth = width;
		layout.setConstraints(comp, constraints);
		cp.add(comp);
	}

	/**
	 * Method <code>getHelpID</code> should return the help id that corrisponds to the particular filter.
	 *
	 * @return a <code>String</code> value
	 */
	public abstract String getHelpID();

	/**
	 * Sets the default values
	 */
	protected abstract void setDefault();

	/**
	 * @return true if the dialog is in a state it can be shown
	 */
	public abstract boolean canShow();

	/**
	 * Sets all the values back to the way they where on the last ok or default.
	 */
	protected abstract void cancelAction();

	/**
	 * Method <code>okAction</code> should perform necessary operations for when
	 * the ok button is pressed.
	 *
	 * @return an <code>boolean</code> value of whether or not the history should be updated.
	 * @exception Exception if there is an error in the user's input. The message of the exception will be displayed.
	 */
	protected abstract boolean okAction() throws Exception;
}

