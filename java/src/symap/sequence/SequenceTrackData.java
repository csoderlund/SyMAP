package symap.sequence;

import symap.track.TrackData;
import symap.track.Track;

/**
 * The data of a Sequence (i.e. Sequence name, show ruler or not, and sequence offset).  
 * 
 * @author Austin Shoemaker
 */
public class SequenceTrackData extends TrackData {
	private int group;
	private boolean showGene, showFrame;
	private boolean showGap, showCentromere;
	private boolean showRuler, showAnnot;
	private boolean showFullGene;
	private boolean showScoreLine, showScoreValue; 	// mdb added 2/5/10
	private boolean flipped; 						// mdb added 2/5/10

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
		showScoreLine  = seq.showScoreLine;		// mdb added 2/5/10
		showScoreValue = seq.showScoreValue;	// mdb added 2/5/10
		flipped        = seq.isFlipped();		// mdb added 2/5/10
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
		seq.showScoreLine  = showScoreLine; 	// mdb added 2/5/10
		seq.showScoreValue = showScoreValue; 	// mdb added 2/5/10
		seq.flip(flipped); 						// mdb added 2/5/10
	}

	protected int getGroup() {
		return group;
	}
}
