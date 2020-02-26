package util;

/**
 * Class <code>HaasDiagScaledDistance</code>
 * Distance function implementation that favors the diagonal as found in DAGchainer.
 *
 * |x2 - x1|/sX + |y2 - y1|/sY + ||x2 - x1|/sX - |y2 - y1|/sY|
 * -----------------------------------------------------------
 *                            2
 * @see Distance2D
 */
public class HaasDiagScaledDistance extends Distance2D {

    public HaasDiagScaledDistance() {
	super();
    }

    public HaasDiagScaledDistance(double sX, double sY) {
	super(sX,sY);
    }


    public String toString() {
	return "[|x2 - x1|/sX + |y2 - y1|/sY + ||x2 - x1|/sX - |y2 - y1|/sY|]/2";
    }

    public double doubleDistance(int difX, int difY) {
	if (difX < 0) difX = -difX;
	if (difY < 0) difY = -difY;
	return 0; 
    }
}
