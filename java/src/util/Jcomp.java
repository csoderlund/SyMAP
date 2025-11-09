package util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import symap.frame.HelpBar;
import symap.frame.HelpListener;

/***************************************************
 * Common sets of interface commands
 * 1 Page and Row
 * 2 Label
 * 3 Button
 * 4 CheckBox and Radio
 * 5 TextField and TextArea
 * 6 ComboBox
 * 7 Width
 * 8 Screen
 */
public class Jcomp {
	public static final String ok = "OK", close = "Close", cancel="Cancel"; // for buttons
	 
	// XXX Page and Row
	static public JPanel createPagePanel() {
		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.PAGE_AXIS)); // Y_AXIS
		page.setBackground(Color.white);
		page.setAlignmentX(Component.LEFT_ALIGNMENT);
		page.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 0));

		return page;
	}
	static public JPanel createPagePanelNB() { // The border adds extra space, so do not want it in Query forms
		JPanel page = new JPanel();
		page.setLayout(new BoxLayout(page, BoxLayout.PAGE_AXIS)); // Y_AXIS
		page.setBackground(Color.white);
		page.setAlignmentX(Component.LEFT_ALIGNMENT);
		return page;
	}
	static public JPanel createRowPanel() {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS)); // X_AXIS
		row.setBackground(Color.white);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		return row;
	}
	
	static public JPanel createRowPanelGray() {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.LINE_AXIS)); // X_AXIS
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		return row;
	}
	static public JPanel createPlainPanel() { // this leaves more room above/below than Line_axis
		JPanel row = new JPanel();
		row.setBackground(Color.white);
		return row;
	}
	// Manager Frame to layout a row
	static public Component createHorizPanel( Component[] comps, int gapWidth, int extra ) {
		JPanel panel = new JPanel();
		panel.setLayout( new BoxLayout ( panel, BoxLayout.X_AXIS ) );
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		for (Component c : comps) {
			if (c != null) {
				panel.add( c );
				if (gapWidth > 0)panel.add( Box.createHorizontalStrut(gapWidth) );
			}
			else if (extra > 0)panel.add( Box.createHorizontalStrut(extra) ); 
		}
		panel.add( Box.createHorizontalGlue() );
		
		return panel;
	}
	/* This centers the row: buttonPanel.setAlignmentX(Component.CENTER_ALIGNMENT); or
	  getContentPane().setLayout(new BorderLayout());
	  getContentPane().add(sPane,BorderLayout.CENTER);
	  getContentPane().add(buttonPanel,BorderLayout.SOUTH);
	  pack();
	 */
	
	// XXX Label
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
	static public JLabel createLabelGray(String label, String tip) {
		JLabel tmp = new JLabel(label);
		tmp.setToolTipText(tip);
		tmp.setEnabled(true);
		return tmp;
	}
	static public JLabel createLabel(String label, String tip, boolean enable) {
		JLabel tmp = new JLabel(label);
		tmp.setToolTipText(tip);
		tmp.setBackground(Color.white);
		tmp.setOpaque(true);  // allows the color to be changed on Mac
		tmp.setEnabled(enable);
		return tmp;
	}
	static public JLabel createVentanaLabel(String label, int sz) { 
		JLabel lab = new JLabel(label);
		lab.setFont(new Font("Verdana",0,sz));
		lab.setBackground(Color.white);
		return lab;
	}
	static public JLabel createMonoLabel(String label, int sz) { 
		JLabel lab = new JLabel(label);
		lab.setFont(new Font(Font.MONOSPACED,Font.PLAIN, sz)); 
		lab.setBackground(Color.white);
		return lab;
	}
	static public JLabel createMonoLabelGray(String label, String t) {
		JLabel lab = new JLabel(label);
		lab.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13)); //lab.getFont().getName()
		lab.setToolTipText(t);
		return lab;
	}
	static public JLabel createHtmlLabel(String text) {
		String html = "<html><b><i>" + text + "</i></b></html>";
		JLabel l = new JLabel(html);
		return l;
	}
	static public JLabel createHtmlLabel(String text, String t) {
		String html = "<html><u>" + text + "</u></html>";
		JLabel l = new JLabel(html);
		l.setToolTipText(t);
		return l;
	}
	static public JLabel createBoldLabel(String label, int sz) {// for Instructions
		JLabel lab = new JLabel(label);
		lab.setFont(new Font(Font.MONOSPACED,Font.BOLD, sz)); 
		lab.setBackground(Color.white);
		return lab;
	}
	static public JLabel createItalicsLabel(String label, int sz) {// for Instructions
		JLabel lab = new JLabel(label);
		lab.setFont(new Font(Font.MONOSPACED,Font.ITALIC, sz)); 
		lab.setBackground(Color.white);
		return lab;
	}
	static public JLabel createItalicsBoldLabel(String label, int sz) {// for Instructions
		JLabel lab = new JLabel(label);
		lab.setFont(new Font(Font.MONOSPACED,Font.ITALIC | Font.BOLD, sz)); 
		lab.setBackground(Color.white);
		return lab;
	}
	// XXX Button
	public static JButton createIconButton(HelpListener parent, HelpBar bar, ActionListener l, String path, String tip) {
		Icon icon = ImageViewer.getImageIcon(path);  
		JButton button = new JButton(icon);
		button.setMargin(new Insets(0,0,0,0));
		
		if (l != null) button.addActionListener(l);
		
		button.setToolTipText(tip);
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	}
	public static JButton createBorderIconButton(String path, String tip) {
		Icon icon = ImageViewer.getImageIcon(path);  
		JButton button = new JButton(icon);
		button.setMargin(new Insets(0,0,0,0));
		
		button.setToolTipText(tip);
		button.setName(tip); 
		
		return button;
	}
	public static JButton createIconButton(String path, String tip) {
		Icon icon = ImageViewer.getImageIcon(path);  
		JButton button = new JButton(icon);
		button.setBorder(null);		// necessary or it gets a border
		
		button.setToolTipText(tip);
		button.setName(tip); 
		
		return button;
	}
	// if (isSelected) scaleToggle.setBackground(Color.white); else  scaleToggle.setBackground(Color.gray);
	public static JButton createBorderIconButton(HelpListener parent, HelpBar bar, ActionListener l, String path, String tip) {
		Icon icon = ImageViewer.getImageIcon(path);  // new ImageIcon(path); works in zTest but not here
		JButton button = new JButton(icon);
		button.setBorder(BorderFactory.createRaisedBevelBorder());
		
		if (l != null) button.addActionListener(l);
		
		button.setToolTipText(tip);
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	}
	// This is to be used as toggle with getBorderColor! Has border and smaller
	public static JButton createBorderButton(HelpListener parent, HelpBar bar, ActionListener l, String label, String tip) {
			JButton button = new JButton(label);
			button.setBackground(Color.white);
			button.setAlignmentX(Component.LEFT_ALIGNMENT);
			button.setFont(new Font(button.getFont().getName(),Font.PLAIN,11));
			button.setBorder(BorderFactory.createRaisedBevelBorder());
			
			button.setMinimumSize(button.getPreferredSize());
			button.setMaximumSize(button.getPreferredSize());
			
			if (l != null) button.addActionListener(l);

			button.setToolTipText(tip);
			button.setName(tip); 
			if (bar != null) bar.addHelpListener(button,parent);

			return button;
	}
	public static Color getBorderColor(boolean isSelect) {
		if (isSelect) return Color.gray;
		else return Color.white;
	}
	public static JButton createButtonGray(HelpListener parent, HelpBar bar, ActionListener l, String label, String tip) { 
		JButton button = new JButton(label);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		button.setMinimumSize(button.getPreferredSize());
		button.setMaximumSize(button.getPreferredSize());
		
		if (l != null) button.addActionListener(l);
		
		button.setToolTipText(tip);
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	}
	
	static public JButton createButtonGray(String s, String t) {
		JButton jbutton = new JButton(s);
		jbutton.setMargin(new Insets(1,3,1,3));
		jbutton.setToolTipText(t);
		return jbutton;
	}
	static public JButton createButton(String s, String t) {
		JButton jbutton = new JButton(s);
		jbutton.setBackground(Color.white); 
		jbutton.setMargin(new Insets(1,3,1,3));
		jbutton.setToolTipText(t);
		return jbutton;
	}
	static public JButton createButton(String s, boolean b) {
		JButton jbutton = new JButton(s);
		jbutton.setBackground(Color.white); 
		jbutton.setMargin(new Insets(1,3,1,3));
		jbutton.setEnabled(b);
		return jbutton;
	}
	static public JButton createPlainButton(String label, String tip) { 
		JButton button = new JButton(label);
		button.setBackground(Color.white);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setFont(new Font(button.getFont().getName(),Font.PLAIN,10));
		button.setToolTipText(tip);
		return button;
	}
	static public JButton createBoldButton(String label, String tip) { // for xToSymap
		JButton button = new JButton(label);
		button.setToolTipText(tip);
		button.setBackground(Color.white);
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setMargin(new Insets(0, 0, 0, 0));
		button.setFont(new Font(button.getFont().getName(),Font.BOLD,12));
		return button;
	}
	static public JButton createMonoButton(String s, String t) {// for 2D filters
		JButton jbutton = new JButton(s);
		jbutton.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13)); 
		jbutton.setMargin(new Insets(1,3,1,3));
		jbutton.setToolTipText(t);
		jbutton.setBackground(Color.white);
		return jbutton;
	}
	static public JButton createMonoButtonSmGray(String s, String t) {
		JButton jbutton = new JButton(s);
		jbutton.setFont(new Font(Font.MONOSPACED,Font.PLAIN,11)); 
		jbutton.setMargin(new Insets(0,0,0,0));
		jbutton.setToolTipText(t);
		return jbutton;
	}
	// XXX CheckBox and Radio
	public static JCheckBox createCheckBox(HelpListener parent, HelpBar bar, ActionListener l, String label, String tip) {
		JCheckBox box = new JCheckBox(label);
		box.setBackground(Color.white);
		box.setMinimumSize(box.getPreferredSize());
		box.setMaximumSize(box.getPreferredSize());
		
		if (l != null) box.addActionListener(l);
		
		box.setToolTipText(tip);
		box.setName(tip); 
		if (bar != null) bar.addHelpListener(box,parent);
		
		return box;
	}
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
	static public JCheckBox createCheckBoxGray(String label, String tip) { 
		JCheckBox box = new JCheckBox(label);
		box.setToolTipText(tip);
		box.setMinimumSize(box.getPreferredSize());
		box.setMaximumSize(box.getPreferredSize());
		return box;
	}
	static public JRadioButton createRadio(HelpListener parent, HelpBar bar, ActionListener l, String label, String tip) {
		JRadioButton button = new JRadioButton(label); button.setBackground(Color.white);
		button.setMargin(new Insets(0,0,0,0));
		
		if (l != null) button.addActionListener(l);
		
		button.setToolTipText(tip);
		button.setName(tip); 
		if (bar != null) bar.addHelpListener(button,parent);
		
		return button;
	}
	public static JRadioButton createRadio(String label, String tip) {
		JRadioButton button = new JRadioButton(label); button.setBackground(Color.white);
		button.setToolTipText(tip);
		button.setMargin(new Insets(0,0,0,0));
		button.setMinimumSize(button.getPreferredSize());
		button.setMaximumSize(button.getPreferredSize());
		return button;
	}
	public static JRadioButton createRadioGray(String label, String tip) { // filters use gray background 
		JRadioButton button = new JRadioButton(label); 
		button.setToolTipText(tip);
		button.setMargin(new Insets(0,0,0,0));
		button.setMinimumSize(button.getPreferredSize());
		button.setMaximumSize(button.getPreferredSize());
		return button;
	}
	
	// XXX TextField and TextArea
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
	public static JTextField createTextField(String defVal, String tip, int size, boolean def) {
		JTextField txt = new JTextField(defVal, size);
		txt.setToolTipText(tip);
		txt.setEnabled(def);
		txt.setBackground(Color.white);
		txt.setMinimumSize(txt.getPreferredSize());
		txt.setMaximumSize(txt.getPreferredSize());
		return txt;
	}
	public static JTextArea createTextArea(String text, Color bg, boolean bWrap) {
		JTextArea textArea = new JTextArea(text);
		
		textArea.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (bg==null) 	textArea.setBackground(Color.white);	// for query instructions
		else 			textArea.setBackground(bg);
		textArea.setEditable(false);
		textArea.setLineWrap(bWrap);
		textArea.setWrapStyleWord(true);
		
		return textArea;
	}
	// XXX ComboBox
	public static JComboBox <String> createComboBox(String [] options, String tip,  int def) {
		JComboBox <String> comboBox = new JComboBox <String> (options);
		comboBox.setToolTipText(tip);
		comboBox.setSelectedIndex(def);
		comboBox.setBackground(Color.WHITE);
		comboBox.setMinimumSize(comboBox.getPreferredSize());
		comboBox.setMaximumSize(comboBox.getPreferredSize());
		return comboBox;
	}
	// XXX Width
	public static boolean isWidth(int width, JLabel lab) {
		return (width > lab.getPreferredSize().width);
	}
	public static int getWidth(int width, JLabel lab) {// use with Box.createHorizontalStrut for spacing
		return width - lab.getPreferredSize().width;
	}
	public static boolean isWidth(int width, JComponent lab) {
		return (width > lab.getPreferredSize().width);
	}
	public static int getWidth(int width, JComponent lab) {// use with Box.createHorizontalStrut for spacing
		return width - lab.getPreferredSize().width;
	}
	// XXX Screen
	// attempts to find the screen bounds from the screen insets.  
	public static Rectangle getScreenBounds(Window window) {
		GraphicsConfiguration config = window.getGraphicsConfiguration();
		Rectangle b = config.getBounds();
		Insets inset = Toolkit.getDefaultToolkit().getScreenInsets(config);
		if (inset.left != 0 || inset.right != 0 || inset.top != 0 || inset.bottom != 0)
			b.setBounds(b.x+inset.left,b.y+inset.top,b.width-inset.left-inset.right,b.height-inset.top-inset.bottom);
		
		return b;
	}
	 // sets the window to the full size of the available screen or it's preferred
	 // size, whichever is smaller, using the other methods in this class.
	public static void setFullSize(Window window, Container view, int max) {
		window.pack();

		Dimension pref = window.getPreferredSize();
		Rectangle b = getScreenBounds(window);

		pref.width = Math.min(pref.width,b.width); 
		pref.width = Math.min(pref.width, max);
		pref.height = Math.min(pref.height,b.height);

		setWinSize(window,b,pref,view);
	}
	private static void setWinSize(Window window, Rectangle screenBounds, Dimension pref, Container view) {
		Point loc = window.getLocation();
		if (pref.width+loc.x > screenBounds.width) loc.x = screenBounds.x;
		if (pref.height+loc.y > screenBounds.height) loc.y = screenBounds.y;

		window.setLocation(loc);
		window.setSize(pref);

		if (view != null) view.invalidate();
		window.validate();
	}
	public static void setCursorBusy(Component c, boolean busy) {
		if (busy) c.setCursor( Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ) );
		else      c.setCursor( Cursor.getDefaultCursor() );
	}

}
