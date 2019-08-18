package util;

/**
 * Interface <code>Coordinate</code> provides an interface for storing a point
 * on an n-dimensional space with integer coordinate values.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public interface Coordinate {

    /**
     * Method <code>dimensions</code> returns the number of dimension
     * this coordinate has (e.g., 2 for two dimensional space).
     *
     * @return an <code>int</code> value
     */
    public int dimensions();

    /**
     * Method <code>dimension</code> returns the i'th dimension as an integer
     * starting at 0 (e.g. getDimension(getDimensions()-1) returns the
     * value for the last dimension).
     *
     * @param i an <code>int</code> value
     * @return an <code>int</code> value
     */
    public int getCoord(int i);
}
