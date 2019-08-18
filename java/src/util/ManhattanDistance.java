package util;

/**
 * Class <code>ManhattanDistance</code> function implementation.
 *
 * |x2 - x1| + |y2 - y1|
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Distance2D
 */
public class ManhattanDistance extends Distance2D {

    public String toString() {
	return "Manhattan Distance";
    }

    public String getFormula() {
	return "|x2 - x1| + |y2 - y1|";
    }

    public int integerDistance(int difX, int difY) {
	return Math.abs(difX) + Math.abs(difY);
    }

    public double doubleDistance(int difX, int difY) {
	return integerDistance(difX,difY);
    }
}
