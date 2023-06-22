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
	protected String className;    // e.g. Mapper
	protected String variableName; // e.g. mapper (symap.Mapper.mapper)
	protected String displayName;
	protected JLabel label;
	protected JButton button;
	private ColorIcon icon;
	private Color defaultColor, prevColor;
	private boolean alphaEnabled=true;
	private int order; // CAS520
	private JColorChooser colorChoose;
	private JDialog dialog;

	protected ColorVariable(String className, String variableName, String display_name,
			String description, Dimension dim, int nOrder) {
		
		defaultColor = ColorDialog.getDefault(variableName); // CAS532 ColorDialog.getColor(className, variableName);
		prevColor = defaultColor;

		this.className = className;
		this.variableName = variableName;
		this.displayName = display_name;
		label = new JLabel(display_name);
		this.order = nOrder;
		
		icon = new ColorIcon(defaultColor, null, dim.width, dim.height);
		button = new JButton(icon);
		button.setMargin(new Insets(0,0,0,0));
		button.addActionListener(this);
		button.setToolTipText("Click here to edit the color. Hover over label for definition.");
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
	public int getOrder() { return order; }
	
	// do not change; used to store changed color in cookie
	public String toString() {
		Color c = icon.getColor();
		return className+"."+variableName+"="+c.getRed()+","+c.getGreen()+","+c.getBlue()+","+c.getAlpha();
	}
	
	public boolean isDefault() { // writing cookie's
		return (defaultColor != null && defaultColor.equals(icon.getColor()));
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == button) {
			colorChoose = new JColorChooser();
			colorChoose.setColor(icon.getColor());
			colorChoose.setChooserPanels(new AbstractColorChooserPanel[] { new SwatchChooserPanel(alphaEnabled), 
					new RGBAChooserPanel(alphaEnabled) } );
			dialog = JColorChooser.createDialog(button,label.getText(),true,colorChoose,this,null);
			dialog.setVisible(true); 
		}
		else { // ok button on color chooser
			setIconColor(colorChoose.getColor());
		}
	}
	public void cancelColorChange() {
		setIconColor(prevColor);
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

	public void commitColorChange(ColorVariable cv) { // cv is temp obj from reading cookies
		Color newColor = cv.prevColor;
		setIconColor(newColor);
		if (colorChoose != null) colorChoose.setColor(newColor);
		prevColor = newColor;
		ColorDialog.setColor(className, variableName, newColor);
	}
	
	private void setIconColor(Color color) {
		icon.setColor(color);
		button.repaint();
	}
}
