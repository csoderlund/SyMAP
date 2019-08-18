package symap.marker;

import symap.track.TrackData;
import symap.track.Track;

public class MarkerTrackData extends TrackData {
    private String pattern;
    private boolean show;
    private int markerShow;
    private boolean flipped;

    protected MarkerTrackData(Track track) {
    	super(track);
	MarkerTrack mt = (MarkerTrack)track;
	pattern     = mt.pattern;
	show        = mt.show;
	markerShow  = mt.markerShow;
	flipped     = mt.flipped;
    }
    
    protected void setTrack(Track track) {
	super.setTrack(track);
	setMarkerTrackData((MarkerTrack)track);
    }

    protected void setMarkerTrackData(MarkerTrack mt) {
	mt.pattern     = pattern;
	mt.show        = show;
	mt.markerShow  = markerShow;
	mt.flipped     = flipped;
    }	

    public boolean getFlipped() {
	return flipped;
    }
}
