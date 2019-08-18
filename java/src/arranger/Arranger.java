package arranger;

import util.PropertiesReader;
import dotplot.*;
import finder.*;

public abstract class Arranger extends ParamHolder {

    protected Arranger(PropertiesReader props) {
	super(props);

	setDefaultParams();
    }

    public void arrange(ArrangerResults ar) {
	SubBlock[] blocks;
	ABlock ib;

	while (ar.hasMoreBlocks()) {
	    ib = ar.getNextABlock();
	    blocks = findSubBlocks(ib);
	    if (blocks == null) blocks = new SubBlock[0];

	    for (int i = 0; i < blocks.length; i++)
		blocks[i].setBlockHits();

	    if (debug)          printBlocks(ib,blocks);

	    ar.addResults(blocks);
	}
    }

    protected void printBlocks(ABlock ib, SubBlock[] blocks) {
	for (int i = 0; i < blocks.length; i++)
	    System.out.println(blocks[i]);
	System.out.println();
    }

    protected abstract SubBlock[] findSubBlocks(ABlock block);
}
