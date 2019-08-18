package symap.contig;

import symap.marker.Marker;
import symap.marker.MarkerData;
import symap.marker.MarkerTrack;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Vector;
import java.util.Iterator;
import java.util.Map;
import number.GenomicsNumber;

public class MarkerCloneData extends MarkerData {

	private String[] cloneNames;

	public MarkerCloneData(String name, String type, long pos, Collection<String> cloneNames) {
		super(name,type,pos);
		if (cloneNames == null) this.cloneNames = new String[0];
		else {
			this.cloneNames = new String[cloneNames.size()];
			Iterator<String> iter = cloneNames.iterator();
			for (int i = 0; i < this.cloneNames.length; i++)
				this.cloneNames[i] = (iter.next());//.intern(); // mdb removed intern() 2/2/10 - can cause memory leaks in this case
		}
	}

	public MarkerCloneData(MarkerData md, Collection<String> cloneNames) {
		this(md.getName(),md.getType(),md.getPosition(),cloneNames);
	}

	/**
	 * Method <code>getMarker</code>
	 *
	 * @param track a <code>MarkerTrack</code> value
	 * @param condFilters a <code>Vector</code> value
	 * @param clones a <code>Map</code> value a map with the key being the clone name and the value being a Clone object
	 * @return a <code>Marker</code> value
	 */
	public Marker getMarker(MarkerTrack track, Vector<Object> condFilters, Map<String,Clone> clones) {
		List<Clone> mclones = new ArrayList<Clone>(cloneNames.length);
		if (clones != null) {
			for (int i = 0; i < cloneNames.length; i++)
				mclones.add(clones.get(cloneNames[i]));
		}
		return new Marker(track,condFilters,getName(),getType(),new GenomicsNumber(track,getPosition()),mclones);
	}

}
