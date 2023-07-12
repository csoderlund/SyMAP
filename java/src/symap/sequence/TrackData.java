package symap.sequence;

import java.awt.Point;

/**
 * The data for the Track (i.e. project name, display name, orientation, etc...)
 */
public  class TrackData {
	private Class <?> trackClass;
	private int project, otherProject;
	private int orient;
	private Point moveOffset;
	private double defaultBpPerPixel;
	private double bpPerPixel;
	private long start, end, size;
	private double height, width;
	
	private int group;
	
	private boolean showGene, showGeneLine, showFullGene;
	private boolean showGap, showCentromere;
	private boolean showRuler, showAnnot;
	private boolean showHitLen, showScoreLine, showScoreValue, showHitNum; 	
	private boolean flipped; 

	protected TrackData(Track track) {
		trackClass        = track.getClass();
		project           = track.projIdx;
		start             = track.start.getValue();
		end               = track.end.getValue();
		size              = track.size.getValue();
		orient            = track.orient;
		moveOffset        = new Point(track.moveOffset);
		defaultBpPerPixel = track.defaultBpPerPixel;
		bpPerPixel        = track.bpPerPixel;
		height            = track.height;
		width             = track.width;
		otherProject      = track.otherProjIdx;
		
		Sequence seq = (Sequence) track;
		group          = seq.group;
		showRuler      = seq.bShowRuler;
		showGene       = seq.bShowGene;
		showGeneLine  = seq.bShowGeneLine;
		showAnnot      = seq.bShowAnnot;
		showGap        = seq.bShowGap;
		showCentromere = seq.bShowCentromere;
		showFullGene   = seq.bShowFullGene;
		showScoreLine  = seq.bShowScoreLine;	
		showHitLen 		= seq.bShowHitLen;	
		showScoreValue = seq.bShowScoreText;	
		showHitNum 		= seq.bShowHitNumText;	
		flipped        = seq.isFlipped();		
	}

	protected void setTrack(Track track) {
		track.start.setValue(start);
		track.end.setValue(end);
		track.size.setValue(size);
		track.projIdx           = project;
		track.orient            = orient;
		track.moveOffset.setLocation(moveOffset);
		track.defaultBpPerPixel = defaultBpPerPixel;
		track.bpPerPixel        = bpPerPixel;
		track.height            = height;
		track.width             = width;
		track.otherProjIdx      = otherProject;
		track.clearAllBuild();
		
		Sequence seq = (Sequence)track;
		seq.group          = group;
		seq.bShowRuler      = showRuler;
		seq.bShowGene       = showGene;
		seq.bShowGeneLine   = showGeneLine;
		seq.bShowAnnot      = showAnnot;
		seq.bShowGap        = showGap;
		seq.bShowCentromere = showCentromere;
		seq.bShowFullGene   = showFullGene;
		seq.bShowScoreLine  = showScoreLine; 
		seq.bShowHitLen 		= showHitLen;	
		seq.bShowScoreText = showScoreValue; 
		seq.bShowHitNumText = showHitNum; 
		seq.flipSeq(flipped); 	
	}


	public int getProject() {
		return project;
	}

	public int getOtherProject() {
		return otherProject;
	}

	public Class <?> getTrackClass() {
		return trackClass;
	}
	protected int getGroup() {
		return group;
	}
}
