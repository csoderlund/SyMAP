/*
 * This is based off of DefaultSwatchChooserPanel.java by Sun Microsystems.
 *
 * An alpha slider and spinner have been added.
 */

package colordialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.colorchooser.AbstractColorChooserPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Class <code>SwatchChooserPanel</code> is based off of the DefaultSwatchChooserPanel with
 * an added slider and spinner for Alpha values (optional).
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see AbstractColorChooserPanel
 * @see ChangeListener
 * @see MouseListener
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class SwatchChooserPanel extends AbstractColorChooserPanel implements ChangeListener, MouseListener {
	private MainSwatchPanel swatchPanel;
	private RecentSwatchPanel recentSwatchPanel;
	private JSlider alphaSlider;
	private JSpinner alphaSpinner;
	private boolean isLocked; // for stopping an endless loop of changing the slider and updating the colors
	private boolean doAlpha;  // not allowing alpha changes

	/**
	 * Creates a new <code>SwatchChooserPanel</code> instance in
	 * which alpha is enabled.
	 *
	 */
	public SwatchChooserPanel() { 
		this(true);
	}

	/**
	 * Creates a new <code>SwatchChooserPanel</code> instance.
	 *
	 * @param alphaEnabled a <code>boolean</code> value of true to allow for changing the alpha value
	 */
	public SwatchChooserPanel(boolean alphaEnabled) {
		super();
		doAlpha = alphaEnabled;
		isLocked = false;
	}

	/**
	 * Method <code>getDisplayName</code> returns "Swatches"
	 *
	 * @return a <code>String</code> value
	 */
	public String getDisplayName() {
		return "Swatches";
	}

	/**
	 * Method <code>getSmallDisplayIcon</code> returns null
	 *
	 * @return an <code>Icon</code> value of null
	 */
	public Icon getSmallDisplayIcon() {
		return null;
	}

	/**
	 * Method <code>getLargeDisplayIcon</code> returns null
	 *
	 * @return an <code>Icon</code> value of null
	 */
	public Icon getLargeDisplayIcon() {
		return null;
	}

	/**
	 * Method <code>buildChooser</code> sets up the view and the listeners
	 *
	 */
	protected void buildChooser() {
		int alpha = getColorFromModel().getAlpha();

		alphaSlider = new JSlider(JSlider.HORIZONTAL,0,255,alpha);
		alphaSlider.setMajorTickSpacing(85);
		alphaSlider.setMinorTickSpacing(17);
		alphaSlider.setPaintTicks(true);
		alphaSlider.setPaintLabels(true);
		alphaSlider.addChangeListener(this);

		alphaSpinner = new JSpinner(new SpinnerNumberModel(alpha,0,255,1));
		alphaSpinner.addChangeListener(this);

		JPanel alphaRow = new JPanel();
		alphaRow.add(new JLabel("Alpha"));
		alphaRow.add(alphaSlider);
		alphaRow.add(alphaSpinner);

		swatchPanel =  new MainSwatchPanel(alpha);
		recentSwatchPanel = new RecentSwatchPanel();

		swatchPanel.addMouseListener(this);
		recentSwatchPanel.addMouseListener(this);

		JPanel superHolder = new JPanel(new BorderLayout());
		JPanel mainHolder = new JPanel(new BorderLayout());
		Border border = new CompoundBorder(new LineBorder(Color.black),new LineBorder(Color.white));

		mainHolder.setBorder(border);
		mainHolder.add(swatchPanel,BorderLayout.CENTER);
		superHolder.add(mainHolder,BorderLayout.CENTER);

		JPanel recentHolder = new JPanel(new BorderLayout());
		recentHolder.setBorder(border);
		recentHolder.add(recentSwatchPanel, BorderLayout.CENTER);
		JPanel recentLabelHolder = new JPanel(new BorderLayout());
		recentLabelHolder.add(recentHolder, BorderLayout.CENTER);
		recentLabelHolder.add(new JLabel("Recent:"), BorderLayout.NORTH);
		JPanel recentHolderHolder = new JPanel(new BorderLayout());
		if (this.getComponentOrientation().isLeftToRight()) {
			recentHolderHolder.setBorder(new EmptyBorder(2,10,2,2));
		} 
		else {
			recentHolderHolder.setBorder(new EmptyBorder(2,2,2,10));
		}
		recentHolderHolder.add(recentLabelHolder,BorderLayout.CENTER);
		superHolder.add(recentHolderHolder,BorderLayout.AFTER_LINE_ENDS);	

		if (doAlpha) superHolder.add(alphaRow,BorderLayout.SOUTH);

		add(superHolder);
	}

	/**
	 * Method <code>stateChanged</code> handles the changing of the alpha slider and alpha spinner.
	 *
	 * @param e a <code>ChangeEvent</code> value
	 */
	public void stateChanged(ChangeEvent e) {
		if (!isLocked && e.getSource() == alphaSlider || e.getSource() == alphaSpinner) {
			Color c = getColorFromModel();
			int alpha;
			if (e.getSource() == alphaSlider) {
				alpha = alphaSlider.getValue();
			}
			else { // e.getSource() == alphaSpinner
				alpha = ((Integer)alphaSpinner.getValue()).intValue();
			}
			swatchPanel.changeAlpha(alpha);
			swatchPanel.repaint();
			getColorSelectionModel().setSelectedColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),alpha));
		}
	}

	/**
	 * Method <code>updateChooser</code> updates the alpha slider and alpha spinner
	 * to the current color from the color model.
	 *
	 */
	public void updateChooser() { 
		if (!isLocked) {
			isLocked = true;

			int alpha = getColorFromModel().getAlpha();
			alphaSlider.setValue(alpha);
			alphaSpinner.setValue(new Integer(alpha));

			isLocked = false;
		}
	}

	/**
	 * Method <code>mousePressed</code> handles the mouse clicking on
	 * spots in the main color grid and the recent color grid.
	 *
	 * @param e a <code>MouseEvent</code> value
	 */
	public void mousePressed(MouseEvent e) {
		if (e.getSource() == recentSwatchPanel) {
			Color color = recentSwatchPanel.getColorForLocation(e.getX(), e.getY());
			getColorSelectionModel().setSelectedColor(color);
		}
		else if (e.getSource() == swatchPanel) {
			Color color = swatchPanel.getColorForLocation(e.getX(), e.getY());
			getColorSelectionModel().setSelectedColor(color);
			recentSwatchPanel.setMostRecentColor(color);
		}
	}

	/**
	 * Method <code>mouseClicked</code> does nothing
	 *
	 * @param e a <code>MouseEvent</code> value
	 */
	public void mouseClicked(MouseEvent e) { }

	/**
	 * Method <code>mouseEntered</code> does nothing
	 *
	 * @param e a <code>MouseEvent</code> value
	 */
	public void mouseEntered(MouseEvent e) { }

	/**
	 * Method <code>mouseExited</code> does nothing
	 *
	 * @param e a <code>MouseEvent</code> value
	 */
	public void mouseExited(MouseEvent e) { }

	/**
	 * Method <code>mouseReleased</code> does nothing
	 *
	 * @param e a <code>MouseEvent</code> value
	 */
	public void mouseReleased(MouseEvent e) { }
}

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
abstract class SwatchPanel extends JPanel {
	protected Color[] colors;
	protected Dimension swatchSize, numSwatches, gap;

	public SwatchPanel() {
		initValues();
		initColors();
		setToolTipText(""); // register for events
		setOpaque(true);
		setBackground(Color.white);
		setRequestFocusEnabled(false);
	}

	protected abstract void initValues();
	protected abstract void initColors();

	public boolean isFocusTraversable() {
		return false;
	}

	public void paintComponent(Graphics g) {
		g.setColor(getBackground());
		g.fillRect(0,0,getWidth(), getHeight());
		for (int row = 0; row < numSwatches.height; row++) {
			for (int column = 0; column < numSwatches.width; column++) {
				g.setColor( getColorForCell(column, row) ); 
				int x;
				if ((!this.getComponentOrientation().isLeftToRight()) &&
						(this instanceof RecentSwatchPanel)) {
					x = (numSwatches.width - column - 1) * (swatchSize.width + gap.width);
				} 
				else {
					x = column * (swatchSize.width + gap.width);
				}
				int y = row * (swatchSize.height + gap.height);
				g.fillRect(x, y, swatchSize.width, swatchSize.height);
				g.setColor(Color.black);
				g.drawLine(x+swatchSize.width-1, y, x+swatchSize.width-1, y+swatchSize.height-1);
				g.drawLine(x, y+swatchSize.height-1, x+swatchSize.width-1, y+swatchSize.height-1);
			}
		}
	}

	public Dimension getPreferredSize() {
		int x = numSwatches.width * (swatchSize.width + gap.width) - 1;
		int y = numSwatches.height * (swatchSize.height + gap.height) - 1;
		return new Dimension(x,y);
	}

	public String getToolTipText(MouseEvent e) {
		Color color = getColorForLocation(e.getX(), e.getY());
		return color.getRed()+", "+ color.getGreen() + ", " + color.getBlue();
	}

	public Color getColorForLocation(int x, int y) {
		int column;
		if ((!this.getComponentOrientation().isLeftToRight()) &&
				(this instanceof RecentSwatchPanel)) {
			column = numSwatches.width - x / (swatchSize.width + gap.width) - 1;
		} else {
			column = x / (swatchSize.width + gap.width);
		}
		int row = y / (swatchSize.height + gap.height);
		return getColorForCell(column, row);
	}

	private Color getColorForCell( int column, int row) {
		return colors[ (row * numSwatches.width) + column ];
	}
}

class RecentSwatchPanel extends SwatchPanel {
	protected void initValues() {
		swatchSize = UIManager.getDimension("ColorChooser.swatchesRecentSwatchSize");
		numSwatches = new Dimension(5,7);
		gap = new Dimension(1,1);
	}

	protected void initColors() {
		Color defaultRecentColor = UIManager.getColor("ColorChooser.swatchesDefaultRecentColor");
		int numColors = numSwatches.width * numSwatches.height;

		colors = new Color[numColors];
		for (int i = 0; i < numColors ; i++) {
			colors[i] = defaultRecentColor;
		}
	}

	public void setMostRecentColor(Color c) {
		System.arraycopy(colors, 0, colors, 1, colors.length-1);
		colors[0] = c;
		repaint();
	}

}

class MainSwatchPanel extends SwatchPanel {
	public MainSwatchPanel(int alpha) {
		super();
		changeAlpha(alpha);
	}

	public void changeAlpha(int alpha) {
		Color old;
		for (int i = 0; i < colors.length; i++) {
			old = colors[i];
			if (old != null)
				colors[i] = new Color(old.getRed(),old.getGreen(),old.getBlue(),alpha);
		}
	}	

	protected void initValues() {
		swatchSize = UIManager.getDimension("ColorChooser.swatchesSwatchSize");
		numSwatches = new Dimension(31,9);
		gap = new Dimension(1,1);
	}

	protected void initColors() {
		int numColors = rawValues.length / 3;

		colors = new Color[numColors];
		for (int i = 0; i < numColors ; i++) {
			colors[i] = new Color(rawValues[(i*3)], rawValues[(i*3)+1], rawValues[(i*3)+2]);
		}
	}

	private static int[] rawValues = {     
		255, 255, 255, // first row.
		204, 255, 255,
		204, 204, 255,
		204, 204, 255,
		204, 204, 255,
		204, 204, 255,
		204, 204, 255,
		204, 204, 255,
		204, 204, 255,
		204, 204, 255,
		204, 204, 255,
		255, 204, 255,
		255, 204, 204,
		255, 204, 204,
		255, 204, 204,
		255, 204, 204,
		255, 204, 204,
		255, 204, 204,
		255, 204, 204,
		255, 204, 204,
		255, 204, 204,
		255, 255, 204,
		204, 255, 204,
		204, 255, 204,
		204, 255, 204,
		204, 255, 204,
		204, 255, 204,
		204, 255, 204,
		204, 255, 204,
		204, 255, 204,
		204, 255, 204,
		204, 204, 204,  // second row.
		153, 255, 255,
		153, 204, 255,
		153, 153, 255,
		153, 153, 255,
		153, 153, 255,
		153, 153, 255,
		153, 153, 255,
		153, 153, 255,
		153, 153, 255,
		204, 153, 255,
		255, 153, 255,
		255, 153, 204,
		255, 153, 153,
		255, 153, 153,
		255, 153, 153,
		255, 153, 153,
		255, 153, 153,
		255, 153, 153,
		255, 153, 153,
		255, 204, 153,
		255, 255, 153,
		204, 255, 153,
		153, 255, 153,
		153, 255, 153,
		153, 255, 153,
		153, 255, 153,
		153, 255, 153,
		153, 255, 153,
		153, 255, 153,
		153, 255, 204,
		204, 204, 204,  // third row
		102, 255, 255,
		102, 204, 255,
		102, 153, 255,
		102, 102, 255,
		102, 102, 255,
		102, 102, 255,
		102, 102, 255,
		102, 102, 255,
		153, 102, 255,
		204, 102, 255,
		255, 102, 255,
		255, 102, 204,
		255, 102, 153,
		255, 102, 102,
		255, 102, 102,
		255, 102, 102,
		255, 102, 102,
		255, 102, 102,
		255, 153, 102,
		255, 204, 102,
		255, 255, 102,
		204, 255, 102,
		153, 255, 102,
		102, 255, 102,
		102, 255, 102,
		102, 255, 102,
		102, 255, 102,
		102, 255, 102,
		102, 255, 153,
		102, 255, 204,
		153, 153, 153, // fourth row
		51, 255, 255,
		51, 204, 255,
		51, 153, 255,
		51, 102, 255,
		51, 51, 255,
		51, 51, 255,
		51, 51, 255,
		102, 51, 255,
		153, 51, 255,
		204, 51, 255,
		255, 51, 255,
		255, 51, 204,
		255, 51, 153,
		255, 51, 102,
		255, 51, 51,
		255, 51, 51,
		255, 51, 51,
		255, 102, 51,
		255, 153, 51,
		255, 204, 51,
		255, 255, 51,
		204, 255, 51,
		153, 244, 51,
		102, 255, 51,
		51, 255, 51,
		51, 255, 51,
		51, 255, 51,
		51, 255, 102,
		51, 255, 153,
		51, 255, 204,
		153, 153, 153, // Fifth row
		0, 255, 255,
		0, 204, 255,
		0, 153, 255,
		0, 102, 255,
		0, 51, 255,
		0, 0, 255,
		51, 0, 255,
		102, 0, 255,
		153, 0, 255,
		204, 0, 255,
		255, 0, 255,
		255, 0, 204,
		255, 0, 153,
		255, 0, 102,
		255, 0, 51,
		255, 0 , 0,
		255, 51, 0,
		255, 102, 0,
		255, 153, 0,
		255, 204, 0,
		255, 255, 0,
		204, 255, 0,
		153, 255, 0,
		102, 255, 0,
		51, 255, 0,
		0, 255, 0,
		0, 255, 51,
		0, 255, 102,
		0, 255, 153,
		0, 255, 204,
		102, 102, 102, // sixth row
		0, 204, 204,
		0, 204, 204,
		0, 153, 204,
		0, 102, 204,
		0, 51, 204,
		0, 0, 204,
		51, 0, 204,
		102, 0, 204,
		153, 0, 204,
		204, 0, 204,
		204, 0, 204,
		204, 0, 204,
		204, 0, 153,
		204, 0, 102,
		204, 0, 51,
		204, 0, 0,
		204, 51, 0,
		204, 102, 0,
		204, 153, 0,
		204, 204, 0,
		204, 204, 0,
		204, 204, 0,
		153, 204, 0,
		102, 204, 0,
		51, 204, 0,
		0, 204, 0,
		0, 204, 51,
		0, 204, 102,
		0, 204, 153,
		0, 204, 204, 
		102, 102, 102, // seventh row
		0, 153, 153,
		0, 153, 153,
		0, 153, 153,
		0, 102, 153,
		0, 51, 153,
		0, 0, 153,
		51, 0, 153,
		102, 0, 153,
		153, 0, 153,
		153, 0, 153,
		153, 0, 153,
		153, 0, 153,
		153, 0, 153,
		153, 0, 102,
		153, 0, 51,
		153, 0, 0,
		153, 51, 0,
		153, 102, 0,
		153, 153, 0,
		153, 153, 0,
		153, 153, 0,
		153, 153, 0,
		153, 153, 0,
		102, 153, 0,
		51, 153, 0,
		0, 153, 0,
		0, 153, 51,
		0, 153, 102,
		0, 153, 153,
		0, 153, 153,
		51, 51, 51, // eigth row
		0, 102, 102,
		0, 102, 102,
		0, 102, 102,
		0, 102, 102,
		0, 51, 102,
		0, 0, 102,
		51, 0, 102,
		102, 0, 102,
		102, 0, 102,
		102, 0, 102,
		102, 0, 102,
		102, 0, 102,
		102, 0, 102,
		102, 0, 102,
		102, 0, 51,
		102, 0, 0,
		102, 51, 0,
		102, 102, 0,
		102, 102, 0,
		102, 102, 0,
		102, 102, 0,
		102, 102, 0,
		102, 102, 0,
		102, 102, 0,
		51, 102, 0,
		0, 102, 0,
		0, 102, 51,
		0, 102, 102,
		0, 102, 102,
		0, 102, 102,
		0, 0, 0, // ninth row
		0, 51, 51,
		0, 51, 51,
		0, 51, 51,
		0, 51, 51,
		0, 51, 51,
		0, 0, 51,
		51, 0, 51,
		51, 0, 51,
		51, 0, 51,
		51, 0, 51,
		51, 0, 51,
		51, 0, 51,
		51, 0, 51,
		51, 0, 51,
		51, 0, 51,
		51, 0, 0,
		51, 51, 0,
		51, 51, 0,
		51, 51, 0,
		51, 51, 0,
		51, 51, 0,
		51, 51, 0,
		51, 51, 0,
		51, 51, 0,
		0, 51, 0,
		0, 51, 51,
		0, 51, 51,
		0, 51, 51,
		0, 51, 51,
		51, 51, 51 };
}
