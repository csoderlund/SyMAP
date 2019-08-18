package util;

import java.util.Comparator;

/**
 * Class <code>Coordinate2D</code> abstract class for a point in 2 dimensional 
 * space in which dimension 0 is the x dimension and dimension 1 is the y 
 * dimension.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Coordinate
 */
public abstract class Coordinate2D implements Coordinate, Comparable<Coordinate2D> {

	protected Coordinate2D() { }
	public int dimensions() { return 2; }
	public abstract int getCoord(int i);
	public int getX() { return getCoord(0); }
	public int getY() { return getCoord(1); }
	public String toString() { return "[Coordinate2D (" + getX() + "," + getY() + ")]"; }

	public boolean equals(Object obj) {
		if (obj instanceof Coordinate2D) {
			Coordinate2D c = (Coordinate2D) obj;
			return c.getX() == getX() && c.getY() == getY();
		}
		return false;
	}

	/**
	 * Method <code>compareTo</code> first considers the x position and then the y.
	 * So if the x positions are not equal, the return value is the difference of the x values (getX - ojb.getX).
	 * Otherwise, the difference of the y values is returned.
	 *
	 * @param obj an <code>Object</code> value
	 * @return an <code>int</code> value
	 */
	public int compareTo(Coordinate2D c) {
		if (getX() == c.getX())
			return getY() - c.getY();
		return getX() - c.getX();
	}

	/**
	 * Method <code>getComparator</code> returns the comparator using dimension d
	 * as the primary dimension. (dimension 0 is the x dimension).
	 *
	 * @param d an <code>int</code> value
	 * @return a <code>Comparator</code> value
	 */
	public static Comparator<Coordinate2D> getComparator(int d) {
		switch (d) {
		case 0:
			return new Comparator<Coordinate2D>() {
				public int compare(Coordinate2D c1, Coordinate2D c2) {
					if (c1.getX() == c2.getX())
						return c1.getY() - c2.getY();
					return c1.getX() - c2.getX();
				}
			};
		default:
			return new Comparator<Coordinate2D>() {
				public int compare(Coordinate2D c1, Coordinate2D c2) {
					if (c1.getY() == c2.getY())
						return c1.getX() - c2.getX();
					return c1.getY() - c2.getY();
				}
			};
		}
	}

	public static Comparator<Coordinate2D> getYComparator() {
		return getComparator(1);
	}
}
