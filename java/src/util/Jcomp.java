package util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionListener;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import symap.frame.HelpBar;
import symap.frame.HelpListener;

/***************************************************
 * CAS534 added for static interface components
 */
public class Jcomp {
	public static JLabel createLabel(String text, int fontStyle, int fontSize) {
		JLabel label = new JLabel(text);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setFont( new Font(label.getFont().getName(), fontStyle, fontSize) );
		return label;
	}
	public static JTextArea createTextArea(String text, Color bg, boolean bWrap) {
		JTextArea textArea = new JTextArea(text);
		
		textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
		textArea.setBackground(bg);
		textArea.setEditable(false);
		textArea.setLineWrap(bWrap);
		textArea.setWrapStyleWord(true);
		
		return textArea;
	}
	static public JPanel createPagePanel() {
		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.PAGE_AXIS)); // Y_AXIS
		page.setBackground(Color.white);
		page.setAlignmentX(Component.LEFT_ALIGNMENT);
		page.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));

		return page;
	}
	static public JPanel createRowPanel() {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS)); // X_AXIS
		row.setBackground(Color.white);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		return row;
	}
	static public JPanel createRowCenterPanel() {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS)); // X_AXIS
		row.setBackground(Color.white);
		row.setAlignmentX(Component.CENTER_ALIGNMENT);
		row.setAlignmentY(Component.TOP_ALIGNMENT);
		return row;
	}
	static public JLabel createLabel(String label) {
		JLabel tmp = new JLabel(label);
		tmp.setBackground(Color.white);
		tmp.setOpaque(true);  // allows the color to be changed on Mac
		tmp.setEnabled(true);
		return tmp;
	}
	static public JLabel createHtmlLabel(String text) {
		String html = "<html><b><i>" + text + "</i></b></html";
		JLabel l = new JLabel(html);
		return l;
	}
	public static AbstractButton createButton(HelpListener parent, String path, String tip, HelpBar bar, 
			 ActionListener listener, boolean isCheckbox, boolean bSelected) {
		AbstractButton button;
		
		Icon icon = ImageViewer.getImageIcon(path); 
		if (icon != null) {
		    if (isCheckbox)
		    	button = new JCheckBox(icon);
		    else
		    	button = new JButton(icon);
		    	button.setMargin(new Insets(0,0,0,0));
		}
		else {
		    if (isCheckbox)
		    	button = new JCheckBox(path);
		    else
		    	button = new JButton(path);
		    	button.setMargin(new Insets(1,3,1,3));
		}
		if (isCheckbox) button.setSelected(bSelected); // CAS521 add
		
		if (listener != null) 
		    button.addActionListener(listener);

		button.setToolTipText(tip);
		
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	}
}
