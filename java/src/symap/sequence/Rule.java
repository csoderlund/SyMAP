package symap.sequence;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/**
 * Represents an individual ruler item which is a line followed by text. 
 */
public class Rule {
	private Color unitColor;
	private Font unitFont;
	private Line2D.Double line;
	private Point2D.Float point;
	private TextLayout layout;

	protected Rule(Color unitColor, Font unitFont) {
		this.unitColor = unitColor;
		this.unitFont = unitFont;
		line = new Line2D.Double();
		point = new Point2D.Float();
	}
	
	protected void setLine(double x1, double y1, double x2, double y2) {
		line.setLine(x1, y1, x2, y2);
	}

	protected void setText(TextLayout textLayout, double x, double y) {
		layout = textLayout;
		point.setLocation((float) x, (float) y);
	}

	protected void setOffset(double x, double y) {
		line.setLine(line.x1 - x, line.y1 - y, line.x2 - x, line.y2 - y);
		point.setLocation(point.x - x, point.y - y);
	}
	protected double getY() {return point.y;} // CAS554 for Yellow Anno box
	
	public void paintComponent(Graphics2D g2) {
		if (layout != null) {
			g2.setPaint(unitColor);
			g2.draw(line);
			g2.setFont(unitFont);
			layout.draw(g2, point.x, point.y);
		}
	}
}
