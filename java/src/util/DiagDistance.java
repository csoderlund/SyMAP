package util;

/**
 * Class <code>DiagDistance</code>
 * Distance function implementation that favors the diagonal as found in ADHoRe.
 *
 * 2 * max(|y2 - y1|,|x2 - x1|) - min(|y2 - y1|,|x2 - x1|)
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Distance2D
 */
public class DiagDistance extends Distance2D {

    public String toString() {
	return "ADHoRe Pseudo Distance";
    }

    public String getFormula() {
	return "2 * max(|y2 - y1|,|x2 - x1|) - min(|y2 - y1|,|x2 - x1|)";
    }

    public int integerDistance(int difX, int difY) {
	if (difX < 0) difX = -difX;
	if (difY < 0) difY = -difY;
	return 2 * (difX > difY ? difX - difY : difY - difX);
    }

    public double doubleDistance(int difX, int difY) {
	return integerDistance(difX,difY);
    }
}
