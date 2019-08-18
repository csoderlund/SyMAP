package symap.mapper;

import java.util.Arrays;
import java.util.Collection;

public class RepetitiveMarkerFilterData {
	private int p1, p2;
	private String[] data;

	public RepetitiveMarkerFilterData(int p1, int p2) {
		this.p1 = p1;
		this.p2 = p2;
		data = null;
	}

	public RepetitiveMarkerFilterData(int p1, int p2, Collection<String> repetitiveMarkers) {
		this.p1 = p1;
		this.p2 = p2;
		if (repetitiveMarkers == null) data = new String[0];
		else                           data = (String[])repetitiveMarkers.toArray(new String[0]);
		Arrays.sort(data);
	}

	/**
	 * Method <code>isRepetitive</code>
	 *
	 * @param marker a <code>String</code> value of marker name
	 * @return a <code>boolean</code> value of true if the marker is in the list
	 */
	public boolean isRepetitive(String marker) {
		return Arrays.binarySearch(data,marker) >= 0;
	}

	public boolean equals(Object obj) {
		if (obj instanceof RepetitiveMarkerFilterData) {
			RepetitiveMarkerFilterData d = (RepetitiveMarkerFilterData)obj;
			return d.p1 == p1 && d.p2 == p2;
		}
		return false;
	}
}
