package util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
//import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import symap.frame.HelpBar;
import symap.frame.HelpListener;

/***************************************************
 * CAS534 added for static interface components
 * CAS552 there was one abstract method to cover many possibilities; this is more explicit
 */
public class Jcomp {
	
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
	static public JPanel createGrayRowPanel() {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS)); // X_AXIS
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
	//////////////////////////////////////////
	public static JLabel createLabel(String text, int fontStyle, int fontSize) {
		JLabel label = new JLabel(text);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setFont( new Font(label.getFont().getName(), fontStyle, fontSize) );
		return label;
	}
	static public JLabel createLabel(String label) {
		JLabel tmp = new JLabel(label);
		tmp.setBackground(Color.white);
		tmp.setOpaque(true);  // allows the color to be changed on Mac
		tmp.setEnabled(true);
		return tmp;
	}
	static public JLabel createLabel(String label, String tip) {
		JLabel tmp = new JLabel(label);
		tmp.setToolTipText(tip);
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
	static public JLabel createMonoLabel(String label, String t) {
		//String html = "<html><b><i>" + label + "</i></b></html>"; // ignores blanks
		JLabel lab = new JLabel(label);
		lab.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13)); //lab.getFont().getName()
		lab.setToolTipText(t);
		lab.setBackground(Color.white);
		return lab;
	}
	//////////////////////////////////////////////////////////////
	public static JButton createButton(HelpListener parent, HelpBar bar, ActionListener listener,
			String label, String tip) {
		
		JButton button = new JButton(label);
		button.setBackground(Color.white);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		button.setMinimumSize(button.getPreferredSize());
		button.setMaximumSize(button.getPreferredSize());
		//button.setFont(new Font(button.getFont().getName(),Font.PLAIN,11));
		
		if (listener != null) button.addActionListener(listener);
		
		button.setToolTipText(tip);
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	}
	
	public static JButton createIconButton(HelpListener parent, 
			HelpBar bar, ActionListener listener, String path, String tip) {
		
		Icon icon = ImageViewer.getImageIcon(path);  
		JButton button = new JButton(icon);
		button.setMargin(new Insets(0,0,0,0));
		
		if (listener != null) button.addActionListener(listener);
		
		button.setToolTipText(tip);
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	}
	public static JButton createIconButton(String path, String tip) {
		
		Icon icon = ImageViewer.getImageIcon(path);  
		JButton button = new JButton(icon);
		button.setMargin(new Insets(0,0,0,0));
		
		button.setToolTipText(tip);
		button.setName(tip); 
		
		return button;
	}
	// if (isSelected) scaleToggle.setBackground(Color.white);
	// else            scaleToggle.setBackground(Color.gray);
	public static JButton createBorderIcon(HelpListener parent, 
			HelpBar bar, ActionListener listener, String path, String tip) {
		
		Icon icon = ImageViewer.getImageIcon(path);  // new ImageIcon(path); works in zTest but not here
		JButton button = new JButton(icon);
		button.setBorder(BorderFactory.createRaisedBevelBorder());
		
		if (listener != null) button.addActionListener(listener);
		
		button.setToolTipText(tip);
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	}
	// This is to be used as toggle with getBorderColor! 
	// This makes smaller button, but the only difference is the Border
	public static JButton createBorderText(HelpListener parent, 
			HelpBar bar, ActionListener listener, String label, String tip) {
		
			JButton button = new JButton(label);
			button.setBackground(Color.white);
			button.setAlignmentX(Component.LEFT_ALIGNMENT);
			button.setFont(new Font(button.getFont().getName(),Font.PLAIN,11));
			button.setBorder(BorderFactory.createRaisedBevelBorder());
			
			button.setMinimumSize(button.getPreferredSize());
			button.setMaximumSize(button.getPreferredSize());
			
			if (listener != null) button.addActionListener(listener);

			button.setToolTipText(tip);
			button.setName(tip); 
			if (bar != null) bar.addHelpListener(button,parent);

			return button;
	}
	public static Color getBorderColor(boolean isSelect) {
		if (isSelect) return Color.gray;
		else return Color.white;
	}
	public static JCheckBox createCheckBox(HelpListener parent, 
			HelpBar bar, ActionListener listener, String label, String tip) {
		
		JCheckBox box = new JCheckBox(label);
		box.setBackground(Color.white);
		box.setMinimumSize(box.getPreferredSize());
		box.setMaximumSize(box.getPreferredSize());
		
		if (listener != null) box.addActionListener(listener);
		
		box.setToolTipText(tip);
		box.setName(tip); 
		if (bar != null) bar.addHelpListener(box,parent);
		
		return box;
	}
	public static JRadioButton createRadio(HelpListener parent, 
			HelpBar bar, ActionListener listener, String label, String tip) {
		
		JRadioButton button = new JRadioButton(label); button.setBackground(Color.white);
		button.setMargin(new Insets(0,0,0,0));
		
		if (listener != null) button.addActionListener(listener);
		
		button.setToolTipText(tip);
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	}
	///////////////////////////////////////////////////////////////////////
	// Not used: this is not distinquishable from other buttons until selected; other problems
	// with text, this makes BIG button. If border, it is small, but then does not toggle color
	public static JToggleButton createToggleIcon(HelpListener parent, 
			HelpBar bar, ActionListener listener, String path, String tip) {
		
		Icon icon = ImageViewer.getImageIcon(path);  // new ImageIcon(path); works in zTest but not here
		JToggleButton button = new JToggleButton(icon);
		
		if (listener != null) button.addActionListener(listener);
		
		button.setToolTipText(tip);
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	}
	static public JButton createPlainButton(String label, boolean enable) {
		JButton button = new JButton(label);
		button.setBackground(Color.white);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setFont(new Font(button.getFont().getName(),Font.PLAIN,10));
		button.setEnabled(enable);
		return button;
	}
	static public JButton createBoldButton(String label, String tip) { // CAS557 add for xToSymap
		JButton button = new JButton(label);
		button.setToolTipText(tip);
		button.setBackground(Color.white);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setFont(new Font(button.getFont().getName(),Font.BOLD,12));
		return button;
	}
	static public JButton createButton(String s, String t) {
		JButton jbutton = new JButton(s);
		jbutton.setMargin(new Insets(1,3,1,3));
		jbutton.setToolTipText(t);
		return jbutton;
	}
	// CAS552 added for 2D filters
	static public JButton createMonoButton(String s, String t) {
		JButton jbutton = new JButton(s);
		jbutton.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13)); //lab.getFont().getName()
		jbutton.setMargin(new Insets(1,3,1,3));
		jbutton.setToolTipText(t);
		jbutton.setBackground(Color.white);
		return jbutton;
	}
	static public JButton createMonoButtonSm(String s, String t) {
		JButton jbutton = new JButton(s);
		jbutton.setFont(new Font(Font.MONOSPACED,Font.PLAIN,11)); //lab.getFont().getName()
		jbutton.setMargin(new Insets(0,0,0,0));
		jbutton.setToolTipText(t);
		jbutton.setBackground(Color.white);
		return jbutton;
	}
	//////////////////////////////////////////////
	static public JCheckBox createCheckBox(String label, String tip, boolean def) {
		JCheckBox box = new JCheckBox(label, def);
		box.setToolTipText(tip);
		box.setBackground(Color.white);
		box.setMinimumSize(box.getPreferredSize());
		box.setMaximumSize(box.getPreferredSize());
		return box;
	}
	static public JCheckBox createCheckBox(String label, boolean def) {
		JCheckBox box = new JCheckBox(label, def);
		box.setBackground(Color.white);
		box.setMinimumSize(box.getPreferredSize());
		box.setMaximumSize(box.getPreferredSize());
		return box;
	}
	public static JRadioButton createRadio(String label, String tip) {
		JRadioButton button = new JRadioButton(label); button.setBackground(Color.white);
		button.setToolTipText(tip);
		button.setMargin(new Insets(0,0,0,0));
		button.setMinimumSize(button.getPreferredSize());
		button.setMaximumSize(button.getPreferredSize());
		return button;
	}
	public static JRadioButton createRadio(String label) {
		JRadioButton button = new JRadioButton(label); button.setBackground(Color.white);
		button.setMargin(new Insets(0,0,0,0));
		return button;
	}
	public static JTextField createTextField(String defVal, int size) {
		JTextField txt = new JTextField(defVal, size);
		txt.setBackground(Color.white);
		txt.setMinimumSize(txt.getPreferredSize());
		txt.setMaximumSize(txt.getPreferredSize());
		return txt;
	}
	public static JTextField createTextField(String defVal, String tip, int size) {
		JTextField txt = new JTextField(defVal, size);
		txt.setToolTipText(tip);
		txt.setBackground(Color.white);
		txt.setMinimumSize(txt.getPreferredSize());
		txt.setMaximumSize(txt.getPreferredSize());
		return txt;
	}
}
