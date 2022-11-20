package finder;

import java.sql.SQLException;
import java.lang.reflect.Constructor;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;

import dotplot.*;
import util.PropertiesReader;
import symap.pool.ProjectProperties;
import util.State;

/**
 * Class BlockFinder is the base abstract class for block finding algorithms
 * to be used in Sytry.
 *
 * Extending class must implement findBlocks().  If configurable paramaters
 * are being added, setDefaultParams(), setParam(FinderParam param, Object value),
 * numberOfParams(), and getParam(int i) should be overrided.  The super method should
 * also be invoked as BlockFinder has it's own configurable paramater, debug.
 *
 * @see ParamHolder
 * @see DotPlotConstants
 */
public abstract class BlockFinder extends ParamHolder implements DotPlotConstants, ChangeListener {
    protected final Tile block;
    protected final FinderDBUser db;
    protected final ProjectProperties pp;
    protected final int rid;

    protected boolean stop;

    /**
     * Creates a new BlockFinder instance.
     *
     * @param pp a ProjectProperties value
     * @param db a FinderDBUser value
     * @param block a Block value
     * @param rid an int value
     * @param props a PropertiesReader value of properties file specific to the algorithms for setting default values.
     */
    protected BlockFinder(ProjectProperties pp, FinderDBUser db, Tile block, int rid, PropertiesReader props) {
		super(props);
		this.pp = pp;
		this.db = db;
		this.block = block;
		this.rid = rid;
		stop = false;
		setDefaultParams();
    }

    public void stateChanged(ChangeEvent evt) {
    		int s = ((State)evt.getSource()).state();
    		if (s == State.STOPPING || s == State.STOPPED) stop();
    }

    public void stop() {
    	stop = true;
    }

    public boolean isStop() {
	return stop;
    }

    /**
     * Method getGenomeLength gets the length of the genome on axis axis 
     *
     * @param axis an int value of DotPlotConstants.X or DotPlotConstants.Y
     * @param factor an boolean if true, the return value is first multiplied by getFactor(axis)
     * @return an int value of the length of the genome (bp for pseudo and cb for FPC)
     * @exception SQLException if an error occurs
     */
    protected int getGenomeLength(int axis, boolean factor) throws SQLException {
	if (factor) return (int)(db.getGenomeLength(pp,block.getProjID(axis)) * getFactor(axis));
	return db.getGenomeLength(pp,block.getProjID(axis));
    }

    /**
     * Method getGenomeAnchors gets the number of hits between the genomes.  See
     * FinderDBUser for the query details.
     *
     * @return an int value
     * @exception SQLException if an error occurs
     */
    protected int getGenomeAnchors() throws SQLException {
	return db.getGenomeAnchors(pp.getProjectPair(block.getProjID(X),block.getProjID(Y)));
    }

    protected double getFactor(int axis) {
	if (pp.isPseudo(block.getGroup(axis).getProjID())) return 1;
	else return pp.getIntProperty(block.getGroup(axis).getProjID(),"cbsize",1);	
    }

    /**
     * Method setBlocks executes the findBlocks(Block) method
     * and sets the blocks alternate blocks (i.e. Block.setABlocks(ABlock[])).
     *
     * @param altNum an int value
     * @exception SQLException if an error occurs
     */
    public void setBlocks(int altNum) throws SQLException {
		FBlock[] blocks = findBlocks();
		if (blocks == null) blocks = new FBlock[0];
		if (debug) printBlocks(blocks);
		block.setAltBlocksRun(altNum,new AltBlocksRun(rid,blocks));
		for (int i = 0; i < blocks.length; i++)
		    blocks[i].setBlockHits(altNum);
    }

    protected void printBlocks(FBlock[] ablocks) {
	if (ablocks == null || ablocks.length == 0) {
	    System.out.println("========================= "+block+" Found no Blocks!");
	}
	else {
	    StringBuffer tot = new StringBuffer("========================= "+block+" =========================\n");
	    for (int i = 0; i < ablocks.length; i++) {
		tot.append(ablocks[i].getSummary());
	    }
	    System.out.println(tot.toString());
	}
    }

    /**
     * Method findBlocks must implement the block finding algorithm returning
     * an array of the blocks found.
     *
     * @return a FBlock[] value
     * @exception SQLException if an error occurs
     */
    protected abstract FBlock[] findBlocks() throws SQLException;

    /**
     * Method getBlockFinder returns a BlockFinder with the given class
     * name.
     *
     * @param pp a ProjectProperties value
     * @param db a FinderDBUser value
     * @param className a String value
     * @param block a Block value
     * @param rid a int of the desired run id for the block
     * @return a BlockFinder value or null on error or if className is null
     */
    public static BlockFinder getBlockFinder(ProjectProperties pp, FinderDBUser db, String className, Tile block, int rid) {
	BlockFinder bf = null;
	if (className != null)
	    try {
			Class bfClass = Class.forName(className);
			Class[] paramClasses = new Class[] {ProjectProperties.class,FinderDBUser.class,Tile.class,Integer.TYPE};
			Object[] paramArgs = new Object[] {pp,db,block,rid}; // CAS520 new Integer
			Constructor bfCon = bfClass.getConstructor(paramClasses);
			bf = (BlockFinder)bfCon.newInstance(paramArgs);
	    }
	    catch (Exception e) { e.printStackTrace(); }
	return bf;
    }

    /**
     * Method getParams returns the params for a block finder with class name className
     * by instansiating a new one through getBlockFinder(ProjectProperties,FinderDBUser,String,Block,int)
     * and calling BlockFinder.getParams() on the returned object.
     *
     * @param className a String value
     * @return a FinderParam[] value
     */
    public static FinderParam[] getParams(String className) {
		BlockFinder bf = getBlockFinder(null,null,className,null,-1);
		return bf.getParams();
    }

    /**
     * Method printDebug prints the debug message message if the
     * BlockFinder is set to have debug on.
     *
     * Prints: DEBUG "+block.toString()+": "+message
     *
     * @param message a String value
     */
    protected void printDebug(String message) {
		if (debug)
		    System.out.println("DEBUG "+block+": "+message);
    }
}
