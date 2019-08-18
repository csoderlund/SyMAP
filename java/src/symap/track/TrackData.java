package symap.track;

import java.awt.Point;

/**
 * The data for the Track (i.e. project name, display name, orientation, etc...)
 * 
 * @author Austin Shoemaker
 */
public abstract class TrackData {
	private Class trackClass;
	private int project, otherProject;
	private int orient;
	private Point moveOffset;
	private double defaultBpPerPixel;
	private double bpPerPixel;
	private long start, end, size;
	private double height, width;

	protected TrackData(Track track) {
		trackClass        = track.getClass();
		project           = track.project;
		start             = track.start.getValue();
		end               = track.end.getValue();
		size              = track.size.getValue();
		orient            = track.orient;
		moveOffset        = new Point(track.moveOffset);
		defaultBpPerPixel = track.defaultBpPerPixel;
		bpPerPixel        = track.bpPerPixel;
		height            = track.height;
		width             = track.width;
		otherProject      = track.otherProject;
	}

	protected void setTrack(Track track) {
		track.start.setValue(start);
		track.end.setValue(end);
		track.size.setValue(size);
		track.project           = project;
		track.orient            = orient;
		track.moveOffset.setLocation(moveOffset);
		track.defaultBpPerPixel = defaultBpPerPixel;
		track.bpPerPixel        = bpPerPixel;
		track.height            = height;
		track.width             = width;
		track.otherProject      = otherProject;
		track.clearAllBuild();
	}

	public int getProject() {
		return project;
	}

	public int getOtherProject() {
		return otherProject;
	}

	public Class getTrackClass() {
		return trackClass;
	}
}
