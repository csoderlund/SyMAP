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
 * Class <code>BlockFinder</code> is the base abstract class for block finding algorithms
 * to be used in Sytry.
 *
 * Extending class must implement <code>findBlocks()</code>.  If configurable paramaters
 * are being added, <code>setDefaultParams()</code>, <code>setParam(FinderParam param, Object value)</code>,
 * <code>numberOfParams()</code>, and <code>getParam(int i)</code> should be overrided.  The super method should
 * also be invoked as BlockFinder has it's own configurable paramater, <code>debug</code>.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
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
     * Creates a new <code>BlockFinder</code> instance.
     *
     * @param pp a <code>ProjectProperties</code> value
     * @param db a <code>FinderDBUser</code> value
     * @param block a <code>Block</code> value
     * @param rid an <code>int</code> value
     * @param props a <code>PropertiesReader</code> value of properties file specific to the algorithms for setting default values.
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
     * Method <code>getGenomeLength</code> gets the length of the genome on axis <code>axis</code> 
     *
     * @param axis an <code>int</code> value of DotPlotConstants.X or DotPlotConstants.Y
     * @param factor an <code>boolean</code> if true, the return value is first multiplied by getFactor(axis)
     * @return an <code>int</code> value of the length of the genome (bp for pseudo and cb for FPC)
     * @exception SQLException if an error occurs
     */
    protected int getGenomeLength(int axis, boolean factor) throws SQLException {
	if (factor) return (int)(db.getGenomeLength(pp,block.getProjID(axis)) * getFactor(axis));
	return db.getGenomeLength(pp,block.getProjID(axis));
    }

    /**
     * Method <code>getGenomeAnchors</code> gets the number of hits between the genomes.  See
     * FinderDBUser for the query details.
     *
     * @return an <code>int</code> value
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
     * Method <code>setBlocks</code> executes the findBlocks(Block) method
     * and sets the blocks alternate blocks (i.e. <code>Block.setABlocks(ABlock[])</code>).
     *
     * @param altNum an <code>int</code> value
     * @exception SQLException if an error occurs
     */
    public void setBlocks(int altNum) throws SQLException {
	FBlock[] blocks = findBlocks();
	if (blocks == null) blocks = new FBlock[0];
	if (debug) printBlocks(blocks);
	block.setAltBlocksRun(altNum,new AltBlocksRun(rid,blocks));
	for (int i = 0; i < blocks.length; i++)
	    blocks[i].setBlockHits(altNum);
	if (db != null) writeBlocks(blocks);
    }

    protected void writeBlocks(FBlock[] ablocks) throws SQLException {
	db.writeFBlocks(rid,block,ablocks);
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
     * Method <code>findBlocks</code> must implement the block finding algorithm returning
     * an array of the blocks found.
     *
     * @return a <code>FBlock[]</code> value
     * @exception SQLException if an error occurs
     */
    protected abstract FBlock[] findBlocks() throws SQLException;

    /**
     * Method <code>getBlockFinder</code> returns a BlockFinder with the given class
     * name.
     *
     * @param pp a <code>ProjectProperties</code> value
     * @param db a <code>FinderDBUser</code> value
     * @param className a <code>String</code> value
     * @param block a <code>Block</code> value
     * @param rid a <code>int</code> of the desired run id for the block
     * @return a <code>BlockFinder</code> value or null on error or if className is null
     */
    public static BlockFinder getBlockFinder(ProjectProperties pp, FinderDBUser db, String className, Tile block, int rid) {
	BlockFinder bf = null;
	if (className != null)
	    try {
		Class bfClass = Class.forName(className);
		Class[] paramClasses = new Class[] {ProjectProperties.class,FinderDBUser.class,Tile.class,Integer.TYPE};
		Object[] paramArgs = new Object[] {pp,db,block,new Integer(rid)};
		Constructor bfCon = bfClass.getConstructor(paramClasses);
		bf = (BlockFinder)bfCon.newInstance(paramArgs);
	    }
	    catch (Exception e) { e.printStackTrace(); }
	return bf;
    }

    /**
     * Method <code>getParams</code> returns the params for a block finder with class name <code>className</code>
     * by instansiating a new one through <code>getBlockFinder(ProjectProperties,FinderDBUser,String,Block,int)</code>
     * and calling <code>BlockFinder.getParams()</code> on the returned object.
     *
     * @param className a <code>String</code> value
     * @return a <code>FinderParam[]</code> value
     */
    public static FinderParam[] getParams(String className) {
	BlockFinder bf = getBlockFinder(null,null,className,null,-1);
	return bf.getParams();
    }

    /**
     * Method <code>printDebug</code> prints the debug message message if the
     * BlockFinder is set to have debug on.
     *
     * Prints: <code>DEBUG "+block.toString()+": "+message</code>
     *
     * @param message a <code>String</code> value
     */
    protected void printDebug(String message) {
	if (debug)
	    System.out.println("DEBUG "+block+": "+message);
    }
}
