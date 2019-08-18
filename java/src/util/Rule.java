package util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/**
 * Represents an individual rule which is a line followed by text. 
 * 
 * @author Austin Shoemaker
 */
public class Rule {
	private Color unitColor;
	private Font unitFont;
	private Line2D.Double line;
	private Point2D.Float point;
	private TextLayout layout;
	private boolean drawLine;

	/**
	 * Creates a Rule instance. 
	 * 
	 * @param unitColor
	 * @param unitFont
	 */
	public Rule(Color unitColor, Font unitFont) {
		this.unitColor = unitColor;
		this.unitFont = unitFont;
		line = new Line2D.Double();
		point = new Point2D.Float();
		drawLine = true;
	}

	/**
	 * @param show show the line or not
	 */
	public void showLine(boolean show) {
		drawLine = show;
	}

	/**
	 * Sets the Rule's line points.
	 * 
	 * @param x1
	 * @param y1
	 * @param x2
	 * @param y2
	 */
	public void setLine(double x1, double y1, double x2, double y2) {
		line.setLine(x1, y1, x2, y2);
	}

	/**
	 * Sets the TextLayout and the point at which it should be drawn.
	 * 
	 * @param textLayout
	 * @param x
	 * @param y
	 */
	public void setText(TextLayout textLayout, double x, double y) {
		layout = textLayout;
		point.setLocation((float) x, (float) y);
	}

	/**
	 * 
	 * Offset the whole rule.
	 * 
	 * @param x amount to subtract from the x coordinates
	 * @param y amount to subtract from the y coordinates
	 */
	public void setOffset(double x, double y) {
		line.setLine(line.x1 - x, line.y1 - y, line.x2 - x, line.y2 - y);
		point.setLocation(point.x - x, point.y - y);
	}

	/**
	 * Draws this object onto g.
	 * 
	 * @param g2 Graphics2D object onto which this object is painted to.
	 */
	public void paintComponent(Graphics2D g2) {
		if (layout != null) {
			g2.setPaint(unitColor);
			if (drawLine)
				g2.draw(line);
			g2.setFont(unitFont);
			layout.draw(g2, point.x, point.y);
		}
	}
}
