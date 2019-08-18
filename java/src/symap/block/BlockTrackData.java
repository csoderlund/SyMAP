package symap.block;

import java.util.Vector;

import symap.marker.MarkerTrackData;
import symap.track.Track;

/**
 * Holds the data for a block (i.e. the contig list and the default contig list)
 * 
 * @author Austin Shoemaker
 */
public class BlockTrackData extends MarkerTrackData {
	private String block;
	private String contigSetText, defaultContigSetText;
	private Vector<Integer> contigList, defaultContigList;

	protected BlockTrackData(Track blockTrack) {
		super(blockTrack);

		Block block = (Block)blockTrack;
		contigSetText = block.contigSetText;
		contigList = new Vector<Integer>(block.contigList);
		defaultContigSetText = block.defaultContigSetText;
		defaultContigList = new Vector<Integer>(block.defaultContigList);
		this.block = block.block;
	}

	protected void setTrack(Track blockTrack) {
		super.setTrack(blockTrack);

		Block block = (Block)blockTrack;
		block.contigSetText = contigSetText;
		block.defaultContigSetText = defaultContigSetText;
		block.contigList.clear();
		block.contigList.addAll(contigList);
		block.defaultContigList.clear();
		block.defaultContigList.addAll(defaultContigList);
		block.block = this.block;
	}

	protected Vector<Integer> getContigList() {
		return contigList;
	}
}
