package util;

/**
 * Interface <code>DistanceFunction</code> provides an interface
 * for implementing distance functions.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Coordinate
 */
public interface DistanceFunction {

    public int integerDistance(Coordinate c1, Coordinate c2);
    public double doubleDistance(Coordinate c1, Coordinate c2);

    public String getFormula();
}
