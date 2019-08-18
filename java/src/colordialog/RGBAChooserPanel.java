/*
 * This code was developed based of the DefaultRGBChooserPanel developed by Sun Microsystems.
 *
 * An alpha slider and spinner have been added.
 */

package colordialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Class <code>RGBAChooserPanel</code> is based of of the DefaultRGBChooserPanel with
 * an added slider and spinner for the alpha value (optional).
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see AbstractColorChooserPanel
 * @see ChangeListener
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class RGBAChooserPanel extends AbstractColorChooserPanel implements ChangeListener {
    private static final int MIN_VALUE = 0;
    private static final int MAX_VALUE = 255;

    private JSlider redSlider, greenSlider, blueSlider, alphaSlider;
    private JSpinner redSpinner, greenSpinner,  blueSpinner,  alphaSpinner;

    private boolean isLocked;
    private boolean doAlpha;

    /**
     * Creates a new <code>RGBAChooserPanel</code> instance in
     * which alpha is enabled.
     *
     */
    public RGBAChooserPanel() { 
	this(true);
    }

    /**
     * Creates a new <code>RGBAChooserPanel</code> instance.
     *
     * @param alphaEnabled a <code>boolean</code> value of true to allow for changing the alpha value
     */
    public RGBAChooserPanel(boolean alphaEnabled) {
	super();
	doAlpha = alphaEnabled;
	isLocked = false;
    }

    /**
     * Method <code>getDisplayName</code> returns "RGBA"
     *
     * @return a <code>String</code> value
     */
    public String getDisplayName() {
	return "RGBA";
    }

    /**
     * Method <code>getSmallDisplayIcon</code>
     *
     * @return an <code>Icon</code> value of null
     */
    public Icon getSmallDisplayIcon() {
        return null;
    }

    /**
     * Method <code>getLargeDisplayIcon</code>
     *
     * @return an <code>Icon</code> value of null
     */
    public Icon getLargeDisplayIcon() {
        return null;
    }

    /**
     * Method <code>buildChooser</code> sets up the view
     *
     */
    protected void buildChooser() {
        setLayout(new BorderLayout());
        Color color = getColorFromModel();

	GridBagLayout gridbag = new GridBagLayout();
        JPanel panel = new JPanel(gridbag);
	GridBagConstraints constraints = new GridBagConstraints();

	constraints.fill = GridBagConstraints.NONE;
	constraints.gridheight = 1;
	constraints.ipadx = 10;
	constraints.ipady = 10;

        add(panel, BorderLayout.CENTER);

	redSlider = createSlider(color.getRed());
	redSpinner = createSpinner(color.getRed());
	setRow(new JLabel("Red"),redSlider,redSpinner,panel,gridbag,constraints);

	greenSlider = createSlider(color.getGreen());
	greenSpinner = createSpinner(color.getGreen());
	setRow(new JLabel("Green"),greenSlider,greenSpinner,panel,gridbag,constraints);

	blueSlider = createSlider(color.getBlue());
	blueSpinner = createSpinner(color.getBlue());
	setRow(new JLabel("Blue"),blueSlider,blueSpinner,panel,gridbag,constraints);

	alphaSlider = createSlider(color.getAlpha());
	alphaSpinner = createSpinner(color.getAlpha());
	if (doAlpha) setRow(new JLabel("Alpha"),alphaSlider,alphaSpinner,panel,gridbag,constraints);
    }

    /**
     * Method <code>updateChooser</code> updates the sliders and spinners based
     * on the current color.
     *
     */
    public void updateChooser() {
        if (!isLocked) {
            isLocked = true;

	    Color color = getColorFromModel();
	    int red = color.getRed();
	    int blue = color.getBlue();
	    int green = color.getGreen();
	    int alpha = color.getAlpha();
	    
	    if (redSlider.getValue() != red)
		redSlider.setValue(red);
	    if (greenSlider.getValue() != green)
		greenSlider.setValue(green);
	    if (blueSlider.getValue() != blue)
		blueSlider.setValue(blue);
	    if (alphaSlider.getValue() != alpha)
		alphaSlider.setValue(alpha);
	    
	    if (((Integer)redSpinner.getValue()).intValue() != red)
		redSpinner.setValue(new Integer(red));
	    if (((Integer)greenSpinner.getValue()).intValue() != green)
		greenSpinner.setValue(new Integer(green));
	    if (((Integer)blueSpinner.getValue()).intValue() != blue)
		blueSpinner.setValue(new Integer(blue));
	    if (((Integer)alphaSpinner.getValue()).intValue() != alpha)
		alphaSpinner.setValue(new Integer(alpha));
	    
            isLocked = false;
        }
    }

    /**
     * Method <code>stateChanged</code> handles the changing of sliders and spinners
     *
     * @param e a <code>ChangeEvent</code> value
     */
    public void stateChanged(ChangeEvent e) {
	if (!isLocked) {
	    if (e.getSource() instanceof JSlider) {
		Color color = new Color(redSlider.getValue(),greenSlider.getValue(),blueSlider.getValue(),alphaSlider.getValue());
		getColorSelectionModel().setSelectedColor(color);
	    } 
	    else if (e.getSource() instanceof JSpinner) {
		int red = ((Integer)redSpinner.getValue()).intValue();
		int green = ((Integer)greenSpinner.getValue()).intValue();
		int blue = ((Integer)blueSpinner.getValue()).intValue();
		int alpha = ((Integer)alphaSpinner.getValue()).intValue();
		getColorSelectionModel().setSelectedColor(new Color(red,green,blue,alpha));
	    }
	}
    }
    
    private void setRow(JLabel label, JSlider slider, JSpinner spinner, JPanel panel, 
			GridBagLayout layout, GridBagConstraints constraints) {
	addToGrid(panel,layout,constraints,new JLabel(),1);
	constraints.anchor = GridBagConstraints.EAST;
	addToGrid(panel,layout,constraints,label,1);
	constraints.anchor = GridBagConstraints.CENTER;
	addToGrid(panel,layout,constraints,slider,3);
	constraints.anchor = GridBagConstraints.WEST;
	addToGrid(panel,layout,constraints,spinner,1);
	addToGrid(panel,layout,constraints,new JLabel(),GridBagConstraints.REMAINDER);
    }

    private void addToGrid(Container cp, GridBagLayout layout, GridBagConstraints constraints, Component comp, int width) {
	constraints.gridwidth = width;
	layout.setConstraints(comp, constraints);
	cp.add(comp);
    }

    private JSlider createSlider(int value) {
	JSlider slider = new JSlider(JSlider.HORIZONTAL,MIN_VALUE,MAX_VALUE,value);
        slider.setMajorTickSpacing(85);
        slider.setMinorTickSpacing(17);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
	slider.addChangeListener(this);
	return slider;
    }

    private JSpinner createSpinner(int value) {
	JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, MIN_VALUE, MAX_VALUE, 1));
	spinner.addChangeListener(this);
	return spinner;
    }
}
    
