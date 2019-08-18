package util;

/**
 * Class <code>HaasDiagDistance</code>
 * Distance function implementation that favors the diagonal as found in DAGchainer.
 *
 * |x2 - x1| + |y2 - y1| + ||x2 - x1| - |y2 - y1||
 * -----------------------------------------------
 *                    2
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Distance2D
 */
public class HaasDiagDistance extends Distance2D {

    public String toString() {
	return "[|x2 - x1| + |y2 - y1| + ||x2 - x1| - |y2 - y1||]/2";
    }

    public double doubleDistance(int difX, int difY) {
	if (difX < 0) difX = -difX;
	if (difY < 0) difY = -difY;
	return (difX + difY + Math.abs(difX - difY))/2.0;
    }

    public int integerDistance(int difX, int difY) {
	if (difX < 0) difX = -difX;
	if (difY < 0) difY = -difY;
	return (difX + difY + Math.abs(difX - difY)) >> 1;
    }
}
