package symap.filter;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent; // mdb added 3/12/07 #104
import java.awt.event.WindowFocusListener; // mdb added 3/28/07 
import java.awt.event.WindowEvent; // mdb added 3/28/07 

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener; // mdb added 3/13/07 #104
import javax.swing.JPopupMenu; 				// mdb added 3/12/07 #104
import javax.swing.JMenuItem; 				// mdb added 3/20/07 #104

import symap.SyMAP;
import symap.SyMAPConstants;
import symap.drawingpanel.DrawingPanel;
import symap.frame.HelpBar;
import util.Utilities;

/**
 * The abstract class for the filter dialogs.
 * 
 * @author Austin Shoemaker
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public abstract class Filter extends JDialog implements ActionListener,
		ChangeListener, SyMAPConstants,
		PopupMenuListener // mdb added 3/13/07 #104
{
// mdb removed 1/29/09 #159 simplify properties
//	public static final String CONTIG_FILTER_ID, BLOCK_FILTER_ID, SEQUENCE_FILTER_ID, MAPPER_FILTER_ID;
//	public static final String CONTIG_FILTER_TITLE, BLOCK_FILTER_TITLE, SEQUENCE_FILTER_TITLE, MAPPER_FILTER_TITLE;
//	static {
//		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/filter.properties"));
//
//		CONTIG_FILTER_ID   = props.getString("contigFilterID");
//		BLOCK_FILTER_ID    = props.getString("blockfilterID");
//		SEQUENCE_FILTER_ID = props.getString("sequenceFilterID");
//		MAPPER_FILTER_ID   = props.getString("mapperFilterID");
//
//		CONTIG_FILTER_TITLE   = props.getString("contigFilterTitle");
//		BLOCK_FILTER_TITLE    = props.getString("blockfilterTitle");
//		SEQUENCE_FILTER_TITLE = props.getString("sequenceFilterTitle");
//		MAPPER_FILTER_TITLE   = props.getString("mapperFilterTitle");
//	}

	private JButton okButton, cancelButton, defaultButton;
	protected DrawingPanel drawingPanel;
	protected JPanel buttonPanel;
	protected HelpBar helpBar;
	protected JPopupMenu popup; // mdb added 3/12/07 #104
	protected JMenuItem popupTitle, showNavigationHelp, showTrackHelp; // mdb added 3/20/07 #104
	

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
		
		// mdb added 3/12/07 #104 -- BEGIN
		popup = new JPopupMenu(); 
		popup.addPopupMenuListener(this); 
		popupTitle = new JMenuItem();
		popupTitle.setEnabled(false);
		popup.add(popupTitle);
		popup.addSeparator();
// mdb removed 12/11/09 #162
//		showNavigationHelp = new JMenuItem("?  Show Navigation Help");
//		popup.add(showNavigationHelp);
//		showTrackHelp = new JMenuItem("?  Show Track Help");
//		popup.add(showTrackHelp);
//		popup.addSeparator();
		// mdb added 3/12/07 #104 -- END
		
		addWindowFocusListener(new WindowFocusListener() { // mdb added 3/28/07 #112
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
	
	public void showPopup(MouseEvent e) { // mdb added 3/12/07 #104
		popup.show(e.getComponent(), e.getX(), e.getY());
	}	
		
	public void popupMenuCanceled(PopupMenuEvent event) { } // mdb added 3/13/07 #104
	public void popupMenuWillBecomeVisible(PopupMenuEvent event) { } // mdb added 3/13/07 #104
	public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { } // mdb added 3/13/07 #104

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
			//javax.swing.SwingUtilities.invokeLater(new RunOk());
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
						//if (SyMAP.DEBUG) System.out.println("\t---- Calling setUpdateHistory in Filter ----");
						drawingPanel.setUpdateHistory();
					}
					if (!drawingPanel.smake()) superShow();
					else hide();
				} catch (Exception e) {
					//drawingPanel.displayError(e.getMessage()); 	// mdb removed 5/25/07 #119
					Utilities.showErrorMessage(e.getMessage(), -1);	// mdb added 5/25/07 #119
					
					superShow();
				}
				//drawingPanel.setFrameEnabled(true);
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

