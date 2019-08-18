package util;

/**
 * Class <code>ScaledDistance2D</code>
 *
 * Euclidean distance with each axis scaled.
 *
 * Sqrt[ ((x2-x1)/sX)^2 + ((y2-y1)/sY)^2 ]
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Distance2D
 */
public class ScaledDistance2D extends Distance2D {

    protected double sX, sY;

    public ScaledDistance2D() {
	this(1,1);
    }

    public ScaledDistance2D(double sX, double sY) {
	super();
	setScale(sX,sY);
    }

    public String toString() {
	return "Scaled Euclidean Distance";
    }

    public String getFormula() {
	return "Sqrt[((x2-x1)/sX)^2 + ((y2-y1)/sY)^2]";
    }

    public void setScale(double sX, double sY) {
	this.sX = sX;
	this.sY = sY;
    }

    public void setScaleByAxis(int axis, double s) {
	if (axis == X_AXIS) sX = s;
	else                sY= s;
    }

    public double getScale(int axis) {
	return axis == X_AXIS ? sX : sY;
    }

    public double doubleDistance(int difX, int difY) {
	difX /= sX;
	difY /= sY;
	return Math.sqrt(difX * difX + difY * difY);
    }
}
