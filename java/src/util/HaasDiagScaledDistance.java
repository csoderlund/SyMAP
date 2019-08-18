package util;

/**
 * Class <code>HaasDiagScaledDistance</code>
 * Distance function implementation that favors the diagonal as found in DAGchainer.
 *
 * |x2 - x1|/sX + |y2 - y1|/sY + ||x2 - x1|/sX - |y2 - y1|/sY|
 * -----------------------------------------------------------
 *                            2
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
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
	//double x, y; // mdb removed 6/29/07 #118
	if (difX < 0) difX = -difX;
	if (difY < 0) difY = -difY;
	//x = difX / sX; ASD just commented out to remove errors. 
	//y = difY / sY; Austin says he never finished implementing this
	//return (x + y + Math.abs(x - y)) / 2.0;
	return 0; // ASD added to handle removal of above
    }
}
