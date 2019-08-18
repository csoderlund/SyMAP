package symap.contig;

import java.util.Vector;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import symap.marker.MarkerList;
import symap.marker.MarkerData;
import symap.block.ContigData;
import number.GenomicsNumber;

public class ContigCloneData extends ContigData {
	private CloneData[] clones;

	public ContigCloneData(int project, int contig, int project2) {
		super(project,contig,project2);
		clones = new CloneData[0];
	}

	public ContigCloneData(int project, int contig, int project2, int id, long size) {
		super(project,contig,project2,id,size,"");
		clones = new CloneData[0];
	}

	public ContigCloneData(ContigData cd) {
		this(cd.getProject(),cd.getContig(),cd.getProject2(),cd.getID(),cd.getSize());
	}

	public void setMarkerData(Collection<MarkerData> markerCloneData) {
		setMarkerData((MarkerData[])markerCloneData.toArray(new MarkerCloneData[markerCloneData.size()]));
	}

	public void setCloneData(Collection<CloneData> cloneData) {
		this.clones = (CloneData[])cloneData.toArray(new CloneData[cloneData.size()]);
	}

	public void setContig(Contig c, GenomicsNumber size, 
			List<Clone> cloneList, Vector<Object> cloneCondFilters, 
			MarkerList markerList) 
	{
		size.setValue(getSize());

		Clone clone;
		Vector<Object> markerCondFilters = markerList.getCondFilters();
		Map<String,Clone> cloneMap = new HashMap<String,Clone>();

		MarkerCloneData[] markerData = (MarkerCloneData[])getMarkerData();

		cloneList.clear();
		markerList.clear();

		for (int i = 0; i < clones.length; i++) {
			clone = clones[i].getClone(c,cloneCondFilters);
			cloneList.add(clone);
			cloneMap.put(clones[i].getName(),clone);
		}

		for (int i = 0; i < markerData.length; i++)
			markerList.add(markerData[i].getMarker(c,markerCondFilters,cloneMap));
	}

	public static CloneData getCloneData(String name, long cb1, long cb2, byte bes1, byte bes2, 
			boolean bes1HasHit, boolean bes1InBlock, boolean bes1Repetitive,
			boolean bes2HasHit, boolean bes2InBlock, boolean bes2Repetitive, String remarks) {
		return new CloneData(name,cb1,cb2,bes1,bes2,
				Clone.getBESFilter(bes1HasHit,bes1InBlock,bes1Repetitive),
				Clone.getBESFilter(bes2HasHit,bes2InBlock,bes2Repetitive),remarks);
	}

	public static MarkerCloneData getMarkerData(MarkerData data, Collection<String> cloneNames) {
		return new MarkerCloneData(data,cloneNames);
	}

	public static MarkerCloneData getMarkerData(String name, String type, long pos, Collection<String> cloneNames) {
		return new MarkerCloneData(name,type,pos,cloneNames);
	}

}
