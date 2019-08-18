package util;

/**
 * Class <code>DiagScaledDistance</code>
 * Distance function implementation that favors the diagonal as found in ADHoRe.
 *
 * 2 * max(|y2 - y1|/sY,|x2 - x1|/sX) - min(|y2 - y1|/sY,|x2 - x1|/sX)
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Distance2D
 */
public class DiagScaledDistance extends ScaledDistance2D {

    public DiagScaledDistance() {
	super();
    }

    public DiagScaledDistance(double sX, double sY) {
	super(sX,sY);
    }

    public String toString() {
	return "ADHoRe Pseudo Distance Scaled";
    }

    public String getFormula() {
	return "2 * max(|y2 - y1|/sY,|x2 - x1|/sX) - min(|y2 - y1|/sY,|x2 - x1|/sX)";
    }

    public double doubleDistance(int difX, int difY) {
	double x, y;
	if (difX < 0) difX = -difX;
	if (difY < 0) difY = -difY;
	x = difX / sX;
	y = difY / sY;
	return 2 * (x > y ? x - y : x - y);
    }
}
