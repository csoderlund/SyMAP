package symap.block;

import java.util.Vector;
import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;

import symap.marker.*;
import number.GenomicsNumber;

/**
 * Holds the data for an individual contig in block view.
 * 
 * @author Austin Shoemaker
 */
public class ContigData {
	private int id;
	private int project, project2;
	private int contig;
	private long size;
	private MarkerData[] markers;
	private String groupList; // mdb added 3/19/07 #105

	public ContigData(int project, int contig, int project2) {
		this.project = project;
		this.project2 = project2;
		this.contig = contig;
		id = -1;
		size = 0;
		markers = new MarkerData[0];
		groupList = null; // mdb added 3/19/07 #105
	}

	public String toString() {
		return "[ContigData: project="+project+" contig="+contig+" project2="+project2+"]";
	}

	public ContigData(int project, int contig, int project2, int id, long size, 
			String groupList) // mdb added groupList 3/19/07 #105
	{
		this(project,contig,project2);
		this.id = id;
		this.size = size;
		this.groupList = groupList; // mdb added 3/19/07 #105
	}

	public int getProject() { return project; }
	public int getContig() { return contig; }
	public int getProject2() { return project2; }
	public int getID() { return id; }
	public void setID(int id) { this.id = id; }
	public long getSize() { return size; }
	public void setSize(long size) { this.size = size; }
	public MarkerData[] getMarkerData() { return markers; }

	public void setMarkerData(MarkerData[] md) {
		if (md == null) markers = new MarkerData[0];
		else {
			markers = md;
			Arrays.sort(markers,new MarkerDataPositionComparator());
		}
	}

	public boolean equals(Object obj) {
		if (obj instanceof ContigData) {
			ContigData cd = (ContigData)obj;
			return cd.project == project && cd.contig == contig && cd.project2 == project2;
		}
		return false;
	}

	public Block.InnerBlock getInnerBlock(Block block) {
		return block.new InnerBlock(contig,new GenomicsNumber(block,size),groupList); // mdb added groupList 3/19/07 #105
	}

	public void setMarkers(Block block, Block.InnerBlock ib) {
		if (markers != null) {
			Marker m;
			MarkerList markerList = block.getMarkerList();
			Vector<Object> markerCondFilters = markerList.getCondFilters();
			Vector<Marker> ibMarkers = ib.getMarkers();
			for (int i = 0; i < markers.length; i++) {
				m = markers[i].getMarker(block,markerCondFilters);
				ibMarkers.add(m);
				markerList.add(m);
			}
		}
	}

	public static void setBlock(Block block, Collection<Integer> contigList, 
			Collection<Block.InnerBlock> innerBlocks, Collection<ContigData> blockPoolData, GenomicsNumber size) 
	{
		Collection<Block.InnerBlock> cachedInnerBlocks = new ArrayList<Block.InnerBlock>(innerBlocks);
		MarkerList markerList = block.getMarkerList();
		Integer cInt;
		Block.InnerBlock ib;
		innerBlocks.clear();
		Iterator<Integer> iter = contigList.iterator();
		long sum = 0;
		while (iter.hasNext()) {
			cInt = iter.next();
			ib = null;
			for (ContigData bpData : blockPoolData)
				if ( bpData.getContig() == cInt.intValue() ) {
					ib = bpData.getInnerBlock(block);
					bpData.setMarkers(block,ib);
					break;
				}
			if (ib == null) {
				for (Iterator<Block.InnerBlock> ii = cachedInnerBlocks.iterator(); ii.hasNext(); ib = null)
					if ( (ib = ii.next()).getContig() == cInt.intValue() ) {
						ib.setVisible(true);
						ib.addMarkersToMarkerList(markerList);
						break;
					}
			}
			if (ib == null) iter.remove();
			else {
				sum += ib.getLength();
				innerBlocks.add(ib);
			}
		}
		if (size != null) size.setValue(sum);
	}

	public static MarkerData getMarkerData(String name, String type, long pos) {
		return new MarkerData(name,type,pos);
	}
}
