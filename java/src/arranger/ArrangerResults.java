package arranger;

import dotplot.*;

public class ArrangerResults {

    private ABlock[] ibs;
    private SubBlock[][] sbs;
    private int num;

    public ArrangerResults(ABlock ib) {
	this(new ABlock[] {ib});
    }

    public ArrangerResults(ABlock[] ibs) {
	this.ibs = ibs == null ? new ABlock[0] : ibs;	
	this.num = 0;
	this.sbs = new SubBlock[ibs.length][];
	for (int i = 0; i < sbs.length; i++)
	    sbs[i] = null;
    }

    public ArrangerResults(Tile block, int altRun) {
	ibs = block.getABlocks(altRun);
	sbs = new SubBlock[ibs.length][];
	for (int i = 0; i < ibs.length; i++)
	    sbs[i] = null;
	num = 0;
    }

    public boolean hasMoreBlocks() {
	return num < ibs.length;
    }

    public ABlock getNextABlock() {
	if (num < ibs.length)
	    return ibs[num++];
	return null;
    }

    public void addResults(SubBlock[] sblocks) {
	sbs[num-1] = sblocks;
    }
}
