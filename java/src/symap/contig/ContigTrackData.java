package symap.contig;

import java.util.Vector;

import symap.marker.MarkerTrackData;
import symap.track.Track;

/**
 * The data of a Contig (i.e. Contig number). 
 * 
 * @author Austin Shoemaker
 */
public class ContigTrackData extends MarkerTrackData {
	private int contig;
	private Vector<Integer> fromBlockList;
	private boolean showCloneNames;
	private String showBothBESFilter;
	private double baseWidth;
	private String cloneFilterPattern;
	private int cloneFilterShow;
	private int[] selectedRemarkIDs;
	private int cloneRemarkShow;

	protected ContigTrackData(Track contigTrack) {
		super(contigTrack);

		Contig contig = (Contig)contigTrack;
		this.contig        = contig.contig;
		fromBlockList      = new Vector<Integer>(contig.fromBlockList);
		showCloneNames     = contig.showCloneNames;
		showBothBESFilter  = contig.showBothBESFilter;
		baseWidth          = contig.baseWidth;
		cloneFilterPattern = contig.cloneFilterPattern == null ? "" : contig.cloneFilterPattern;
		cloneFilterShow    = contig.cloneFilterShow;
		selectedRemarkIDs  = contig.selectedRemarkIDs;
		cloneRemarkShow    = contig.cloneRemarkShow;
	}

	protected void setTrack(Track contigTrack) {
		super.setTrack(contigTrack);
		Contig contig = (Contig)contigTrack;
		contig.contig             = this.contig;
		contig.fromBlockList.clear();
		contig.fromBlockList.addAll(fromBlockList);
		contig.fromBlockList.trimToSize(); // mdb added 2/3/10
		contig.showCloneNames     = showCloneNames;
		contig.showBothBESFilter  = showBothBESFilter;
		contig.baseWidth          = baseWidth;
		contig.cloneFilterPattern = (cloneFilterPattern == null ? "" : cloneFilterPattern);
		contig.cloneFilterShow    = cloneFilterShow;
		contig.selectedRemarkIDs  = selectedRemarkIDs;
		contig.cloneRemarkShow    = cloneRemarkShow;
		contig.cloneCondFilters.clear();
		contig.cloneCondFilters.trimToSize(); // mdb added 2/3/10
		if (showBothBESFilter != Contig.NO_BOTH_BES_FILTER) contig.cloneCondFilters.add(showBothBESFilter);
		if (selectedRemarkIDs != null && selectedRemarkIDs.length > 0 && cloneRemarkShow == Contig.CLONE_SHOW) 
			contig.cloneCondFilters.add(Clone.CLONE_REMARK_COND_FILTERED);
	}

	protected int getContig() {
		return contig;
	}

	protected String getCloneFilterPattern() {
		return cloneFilterPattern;
	}

	protected int getCloneFilterShow() {
		return cloneFilterShow;
	}

	protected int[] getSelectedRemarkIDs() {
		return selectedRemarkIDs;
	}

	protected int getCloneRemarkShow() {
		return cloneRemarkShow;
	}
}
