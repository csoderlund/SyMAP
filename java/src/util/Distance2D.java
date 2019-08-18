package util;

/**
 * Class <code>Distance2D</code>
 * Implementation of the distance function in 2 dimension space using euclidean distance.
 * To override with a new 2D distance function, only doubleDistance(int,int) if applicable or
 * doubleDistance(int,int,int,int) otherwise 
 * (and possibly integerDistance(int,int,int,int) and integerDistance(int,int) for performance) must be overridded.
 * distanceSquared is provided for convenience and may only be applicable for euclidean distance
 * and it's not quaranteed that integerDistance(int difX, int difY) and doubleDistance(int difX, int difY) will
 * be applicable for sub-classes.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see DistanceFunction
 */
public class Distance2D implements DistanceFunction {
    public static final int X_AXIS = 0;
    public static final int Y_AXIS = 1;
    
    public Distance2D() { }

    public Distance2D(double sx, double sy) {
		// TODO Auto-generated constructor stub
    	// ASD just added to get rid of error messages
	}

	public String toString() {
	return "Euclidean Distance";
    }

    public String getFormula() {
	return "Sqrt[(x2-x1)^2 + (y2-y1)^2]";
    }

    public int integerDistance(Coordinate c1, Coordinate c2) {
	return integerDistance(c1.getCoord(0),c1.getCoord(1),c2.getCoord(0),c2.getCoord(1));
    }

    public double doubleDistance(Coordinate c1, Coordinate c2) {
	return doubleDistance(c1.getCoord(0),c1.getCoord(1),c2.getCoord(0),c2.getCoord(1));	
    }

    public int integerDistance(int x1, int y1, int x2, int y2) {
	return integerDistance(x2-x1,y2-y1);
    }

    public double doubleDistance(int x1, int y1, int x2, int y2) {
	return doubleDistance(x1-x2,y1-y2);
    }

    public int integerDistance(int difX, int difY) {
	return (int)doubleDistance(difX,difY);
    }

    public double doubleDistance(int difX, int difY) {
	return Math.sqrt(difX * difX + difY * difY);
    }
}
