package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

public class UserPrompt {
	public static final Color PROMPT = new Color(0xEEFFEE);

	private static final int COL_WIDTH = 200;
	
	public static void displayInfo(JFrame parentFrame, String title, String [] headers, String [] messages, boolean isModal) {
		JPanel message = new JPanel();
		message.setAlignmentX(Component.LEFT_ALIGNMENT);
		message.setLayout(new BoxLayout(message, BoxLayout.PAGE_AXIS));
		
		for(int x=0; x<headers.length; x++) {
			JPanel row = new JPanel();
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS));
			JLabel header = new JLabel(headers[x]);
			
			row.add(header);
			if(header.getPreferredSize().width < COL_WIDTH) {
				row.add(Box.createHorizontalStrut(COL_WIDTH - header.getPreferredSize().width));
			}
			row.add(new JLabel(messages[x]));
			message.add(row);
			if(x<headers.length-1 && headers[x+1].length() >0)
				message.add(Box.createVerticalStrut(5));
		}
		JOptionPane pane = new JOptionPane(message);
		JDialog helpDiag = pane.createDialog(parentFrame, title);
		helpDiag.setModal(isModal);
		helpDiag.setFont(new Font("monospaced", Font.ITALIC, 28));
		helpDiag.setVisible(true);
	}

	public static void displayInfo(JFrame parentFrame, String title, String [] message, boolean isModal) {
//		txtArea.setFont(new Font("courier", Font.PLAIN, 14));
		JOptionPane pane = new JOptionPane();
		pane.setFont(new Font("serif", Font.PLAIN, 20));
		pane.setMessage(message);
		pane.setMessageType(JOptionPane.PLAIN_MESSAGE);
		JDialog helpDiag = pane.createDialog(parentFrame, title);
		helpDiag.setFont(new Font("serif", Font.PLAIN, 20));
		helpDiag.setModal(isModal);
		helpDiag.setVisible(true);
	//	JOptionPane.showMessageDialog(parentFrame, txtArea);
	}
	
	public static void displayInfo(String title, String [] message, boolean isModal) {
		displayInfo(null, title, message, isModal);
	}
	
	public static void displayInfo(String title, String [] message) {
		displayInfo(null, title, message, false);
	}
}
