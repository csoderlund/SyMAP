package symap.sequence;

import symap.track.TrackData;
import symap.track.Track;

/**
 * The data of a Sequence (i.e. Sequence name, show ruler or not, and sequence offset).  
 */
public class SequenceTrackData extends TrackData {
	private int group;
	private boolean showGene, showFrame;
	private boolean showGap, showCentromere;
	private boolean showRuler, showAnnot;
	private boolean showFullGene;
	private boolean showScoreLine, showScoreValue; 	
	private boolean flipped; 						

	protected SequenceTrackData(Track sequence) {
		super(sequence);

		Sequence seq = (Sequence)sequence;
		group          = seq.group;
		showRuler      = seq.showRuler;
		showGene       = seq.showGene;
		showFrame      = seq.showFrame;
		showAnnot      = seq.showAnnot;
		showGap        = seq.showGap;
		showCentromere = seq.showCentromere;
		showFullGene   = seq.showFullGene;
		showScoreLine  = seq.showScoreLine;		
		showScoreValue = seq.showScoreValue;	
		flipped        = seq.isFlipped();		
	}

	protected void setTrack(Track sequence) {
		super.setTrack(sequence);

		Sequence seq = (Sequence)sequence;
		seq.group          = group;
		seq.showRuler      = showRuler;
		seq.showGene       = showGene;
		seq.showFrame      = showFrame;
		seq.showAnnot      = showAnnot;
		seq.showGap        = showGap;
		seq.showCentromere = showCentromere;
		seq.showFullGene   = showFullGene;
		seq.showScoreLine  = showScoreLine; 	
		seq.showScoreValue = showScoreValue; 	
		seq.flip(flipped); 						
	}

	protected int getGroup() {
		return group;
	}
}
