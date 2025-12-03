package util;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import symap.sequence.Annotation;

public class Popup {
	/**************************************************************
	 * XXX Popups
	 */
	// isModal=true means that everything is frozen until the window is closed
	// these allow formatted messages, but only have OK at the bottom of window
	public static void showMonoWarning(Component parent, String message) {
		displayInfoMonoSpace(parent, "Warning", message, true);
	}
	public static void showMonoError(Component parent, String message) {
		displayInfoMonoSpace(parent, "Error", message, true);
	}
	public static void showMonoError(Component parent, String title, String message) {
		displayInfoMonoSpace(parent, title, message, true);
	}
	public static void displayInfoMonoSpace(Component parentFrame, String title, 
			String theMessage, boolean isModal) {
		JOptionPane pane = new JOptionPane();
		
		JTextArea messageArea = new JTextArea(theMessage);

		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		pane.setMessage(sPane);
		pane.setMessageType(JOptionPane.PLAIN_MESSAGE);

		JDialog helpDiag = pane.createDialog(parentFrame, title);
		helpDiag.setModal(isModal);
		helpDiag.setResizable(true);
		
		helpDiag.setVisible(true);		
	}
	// not used
	public static JOptionPane displayInfoMonoSpace(Component parentFrame, String title, 
			String theMessage, Dimension d, Annotation aObj) 
	{ 	
		// scrollable selectable message area
		JTextArea messageArea = new JTextArea(theMessage);
		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		// put scrollable pane in option pane
		JOptionPane optionPane = new JOptionPane(sPane, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
		
		// add dialog, which has title
		JDialog helpDiag = optionPane.createDialog(parentFrame, title);
		helpDiag.setModal(false); // true - freeze other windows
		helpDiag.setResizable(true);
		if (helpDiag.getWidth() >= d.width || helpDiag.getHeight() >= d.height) helpDiag.setSize(d);
		helpDiag.setVisible(true);	
		helpDiag.setAlwaysOnTop(true);
		
		helpDiag.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); 
		helpDiag.addWindowListener(new WindowAdapter() {
			public void windowClosed(WindowEvent e) {
				aObj.setIsPopup(false);
			}
		});
		
		 optionPane.addPropertyChangeListener(
    		new PropertyChangeListener() {
        		public void propertyChange(PropertyChangeEvent e) {
        			aObj.setIsPopup(false);
        		}
    		}
    	);
		 
		return optionPane;
	}
	
	public static void showInfoMsg(String title, String msg) {
		JOptionPane.showMessageDialog(null, msg, title, JOptionPane.WARNING_MESSAGE);
	}
	public static void showWarning(String msg) { 
		JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE);
	}
	
	public static void showInfoMessage(String title, String msg) {
		System.out.println(msg);
		JOptionPane.showMessageDialog(null, msg, title, JOptionPane.WARNING_MESSAGE);
	}
	
	public static void showWarningMessage(String msg) {
		System.out.println(msg);
		JOptionPane.showMessageDialog(null, msg, "Warning", JOptionPane.WARNING_MESSAGE);
	}
	
	public static void showErrorMsg(String msg) {
		JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
	}
	
	public static void showErrorMessage(String msg) {
		System.err.println(msg);
		JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
	}
	
	public static void showErrorMessage(String msg, int exitStatus) {
		showErrorMessage(msg);
		System.out.println("Exiting SyMAP");
		System.exit(exitStatus);
	}
	static public boolean showYesNo (String title, String msg) { 
		String [] options = {"No", "Yes"};
		int ret = JOptionPane.showOptionDialog(null, 
				msg,
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
		if (ret == 0) return false;
		return true;
	}
	static public boolean showContinue (String title, String msg) { 
		String [] options = {"Cancel", "Continue"};
		int ret = JOptionPane.showOptionDialog(null, 
				msg + "\nContinue?",
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
		if (ret == 0) return false;
		return true;
	}
	
	// Cancel is false
	public static boolean showConfirm2(String title, String msg) {
		String [] options = {"Cancel", "Confirm"};
		int ret = JOptionPane.showOptionDialog(null, 
				msg, 
				title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
		if (ret == 0) return false;
		else return true;
	}
	
	// three possible actions; used to (1) remove from database (2) and disk
	public static int showConfirm3(String title, String msg) {
		String [] options = {"Cancel", "Only", "All"};
		return JOptionPane.showOptionDialog(null, 
				msg, title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[1]); 
	}
	public static int showConfirmFile(String filename) {
		String [] options = {"Cancel", "Overwrite", "Append"};
		String title = "File exists";
		String msg = "File '" + filename + "' exists.\nDo you want to overwrite it or append to it?";
		return JOptionPane.showOptionDialog(null, 
				msg, title, JOptionPane.YES_NO_OPTION, 
				JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
	}
	public static void showOutOfMemoryMessage(Component parent) {
		System.err.println("Not enough memory.");
		JOptionPane optionPane = new JOptionPane("Not enough memory - increase 'mem' in symap script.", JOptionPane.ERROR_MESSAGE);
		
		LinkLabel label = new LinkLabel("Click to open the Troubleshooting Guide.", Jhtml.TROUBLE_GUIDE_URL); 
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		optionPane.add(new JLabel(" "), 1);
		optionPane.add(label, 2);
		optionPane.add(new JLabel(" "), 3);
		
		JDialog dialog = optionPane.createDialog(parent, "Error");
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setVisible(true);
		optionPane.getValue(); // Wait on user input
	}
	
	public static void showOutOfMemoryMessage() { showOutOfMemoryMessage(null); }
	
}
