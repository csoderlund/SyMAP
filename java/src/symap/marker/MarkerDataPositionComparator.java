package symap.marker;

import java.util.Comparator;

/**
 * Class <code>MarkerDataPositionComparator</code> is a MarkerData comparator
 * that orders by position.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Comparator
 */
public class MarkerDataPositionComparator implements Comparator<MarkerData> {

	/**
	 * Creates a new <code>MarkerDataPositionComparator</code> instance.
	 *
	 */
	public MarkerDataPositionComparator() { }

	/**
	 * Method <code>compare</code> returns a negative number of the position of o1 is less than o2, zero
	 * if the positions are equal, and a postivie number if the position of o1 is greater than o2.
	 *
	 * @param o1 an <code>Object</code> value of a MarkerData
	 * @param o2 an <code>Object</code> value of a MarkerData
	 * @return an <code>int</code> value of ((MarkerData)o1).getPosition() - ((MarkerData)o2).getPosition()
	 */
	public int compare(MarkerData m1, MarkerData m2) {
		return (int)(m1.getPosition() - m2.getPosition());
	}

}
