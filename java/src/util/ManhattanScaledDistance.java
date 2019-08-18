package util;

/**
 * Class <code>ManhattanScaledDistance</code>
 * Manhattan Scaled Distance function implementation.
 *
 * |x2 - x1|/s1 + |y2 - y1|/s2 
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Distance2D
 */
public class ManhattanScaledDistance extends ScaledDistance2D {

    public ManhattanScaledDistance() {
	super();
    }

    public ManhattanScaledDistance(double sX, double sY) {
	super(sX,sY);
    }

    public String toString() {
	return "Manhattan Distance Scaled";
    }

    public String getFormula() {
	return "|x2 - x1|/sX + |y2 - y1|/sY";
    }

    public int integerDistance(int difX, int difY) {
	return (int)( Math.abs(difX/sX) + Math.abs(difY/sY) );
    }

    public double doubleDistance(int difX, int difY) {
	return Math.abs(difX/sX) + Math.abs(difY/sY);
    }
}
