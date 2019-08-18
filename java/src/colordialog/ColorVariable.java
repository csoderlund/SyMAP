package colordialog;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.colorchooser.AbstractColorChooserPanel;

public class ColorVariable implements ActionListener {
	protected String className;
	protected String variableName;
	protected JLabel label;
	protected JButton button;
	private ColorIcon icon;
	private Color defaultColor, prevColor;
	private boolean alphaEnabled;
	private JColorChooser cc;
	private JDialog dialog;

	protected ColorVariable(String className, String variableName, String display_name,
			String description, Dimension dim, boolean alphaEnabled) {
		defaultColor = ColorDialog.getColor(className,variableName);
		prevColor = defaultColor;

		this.className = className;
		this.variableName = variableName;
		this.alphaEnabled = alphaEnabled;
		label = new JLabel(display_name);
		icon = new ColorIcon(defaultColor,null,dim.width,dim.height);
		button = new JButton(icon);
		button.setMargin(new Insets(0,0,0,0));
		button.addActionListener(this);
		button.setToolTipText("Click here to edit the color");
		label.setToolTipText(description);
	}

	public ColorVariable(String className, String variableName, Color color) {
		this.className = className;
		this.variableName = variableName;
		this.defaultColor = color;
		this.prevColor = color;
	}

	public boolean equals(Object obj) {
		if (obj instanceof ColorVariable) {
			return ( className.equals(((ColorVariable)obj).className) && 
					variableName.equals(((ColorVariable)obj).variableName) );
		}
		return false;
	}

	public String toString() {
		Color c = icon.getColor();
		return className+"."+variableName+"="+c.getRed()+","+c.getGreen()+","+c.getBlue()+","+c.getAlpha();
	}

	public boolean isDefault() {
		return (defaultColor != null && defaultColor.equals(icon.getColor()));
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == button) {
			cc = new JColorChooser();
			cc.setColor(icon.getColor());
			cc.setChooserPanels(new AbstractColorChooserPanel[] { new SwatchChooserPanel(alphaEnabled), 
					new RGBAChooserPanel(alphaEnabled) } );
			dialog = JColorChooser.createDialog(button,label.getText(),true,cc,this,null);
			//dialog.show(); // mdb removed 7/2/07 #118
			dialog.setVisible(true); // mdb added 7/2/07 #118
		}
		else { // ok button on color chooser
			setIconColor(cc.getColor());
		}
	}

	public void setDefaultColor() {
		setIconColor(defaultColor);
	}

	public void commitColorChange() {
		if (prevColor == null || !prevColor.equals(icon.getColor())) {
			prevColor = icon.getColor();
			ColorDialog.setColor(className,variableName,prevColor);
		}
	}

	public void commitColorChange(Color newColor) {
		setIconColor(newColor);
		if (cc != null) cc.setColor(newColor);
		prevColor = newColor;
		ColorDialog.setColor(className,variableName,newColor);
	}

	public void commitColorChange(ColorVariable cv) {
		commitColorChange(cv.prevColor);
	}

	public void cancelColorChange() {
		setIconColor(prevColor);
	}

	private void setIconColor(Color color) {
		icon.setColor(color);
		button.repaint();
	}
}
