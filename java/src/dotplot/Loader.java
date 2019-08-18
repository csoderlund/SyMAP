package dotplot;

import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import finder.BlockFinder;
import util.DatabaseReader;
import util.StateObject;
import util.Queue;
import util.CheckoutQueue;
import util.Utilities;
import finder.FinderDBUser;
import symap.pool.ProjectProperties;
import symap.pool.ProjectPair;

public class Loader extends StateObject implements DotPlotConstants {
    public static final boolean DEFAULT_KEEP_ABOVE_ALT1 = true;

    private static final boolean DEBUG = false;
    private static final int TIME_BETWEEN_DOWNLOADERS = 0;
    private static final int[] DOWNLOADERS   = {4,10};
    private static final int[] D_CONNECTIONS = {4, 0};
    private static final int[] FINDERS       = {1, 0};
    private static final int[] F_CONNECTIONS = {1, 0};

    public static final int APPLICATION = 0;
    public static final int APPLET      = 1;

    private DotPlotDBUser db;
    
    private int numD, numDC, numF, numFC;

    private List<DatabaseReader> readers;
    private List<DownloadThread> downloaders;
    private List<Thread> finders;

    private Queue downloaderQueue, finderQueue;
    private CheckoutQueue downloaderDatabases, finderDatabases;
    private List<TileHolder> blockHolders;

    private List<Integer> altNumKeepers;
    private List<FoundBlock> foundBlocks;
    
    private boolean aborted = false; // mdb added 8/31/09

    private Loader() {
		readers         = new ArrayList<DatabaseReader>();
		downloaders     = new ArrayList<DownloadThread>();
		finders         = new ArrayList<Thread>();
		downloaderQueue = new Queue("Downloader Queue");
		finderQueue     = new Queue("Finder Queue");
		downloaderDatabases = finderDatabases = new CheckoutQueue();
		blockHolders    = new Vector<TileHolder>();
	
		altNumKeepers = new Vector<Integer>();
		foundBlocks   = new Vector<FoundBlock>();
	
		if (DEFAULT_KEEP_ABOVE_ALT1) {
		    for (int i = 2; i < DotPlot.FINDER_RUNS; i++)
			altNumKeepers.add(new Integer(i));
		}
    }

    public Loader(DotPlotDBUser db, int type) {
		this();
		//if (type < 0 || type > 1) throw new IllegalArgumentException("Type must be "+APPLICATION+" or "+APPLET); // mdb removed 1/29/10
		type = APPLET; // mdb added 1/29/10 - application mode is for legacy functionality
		this.db    = db;
		this.numD  = DOWNLOADERS[type];
		this.numDC = D_CONNECTIONS[type];
		this.numF  = FINDERS[type];
		this.numFC = F_CONNECTIONS[type];
    }

// mdb unused 1/29/10    
//    public Loader(DotPlotDBUser db, int numDownloaders, int numDConnections, int numFinders, int numFConnections) {
//		this();
//		this.db    = db;
//		this.numD  = numDownloaders;
//		this.numDC = numDConnections;
//		this.numF  = numFinders;
//		this.numFC = numFConnections;
//    }

    public void keepAltNum(int altNum) {
		if (!altNumKeepers.contains(new Integer(altNum)))
		    altNumKeepers.add(new Integer(altNum));
    }

    private boolean doKeepAltNum(int altNum) {
    	return altNumKeepers.contains(new Integer(altNum));
    }

    public void dontKeepAltNum(int altNum) {
		while (altNumKeepers.remove(new Integer(altNum)));
		clearFoundHistory(altNum);
    }

    public void clearAllFoundHistory() {
		synchronized (foundBlocks) {
		    foundBlocks.clear();
		}
    }

    public void clearFoundHistory(int altNum) {
		synchronized (foundBlocks) {
		    for (Iterator<FoundBlock> iter = foundBlocks.iterator(); iter.hasNext(); )
		    	if ((iter.next()).altNum == altNum) iter.remove();
		}
    }

    private void clearFoundHistory(Project[] projects, Finder[] finders) {
		synchronized (foundBlocks) {
		    for (Iterator<FoundBlock> iter = foundBlocks.iterator(); iter.hasNext(); )
			if (!(iter.next()).equals(projects,finders)) iter.remove();
		}
    }

    private void addFoundBlock(TileHolder bh) {
		if (bh.finder != null && doKeepAltNum(bh.altNum)) {
		    synchronized (foundBlocks) {
		    	foundBlocks.add(new FoundBlock(bh));
		    }
		}
    }

    private boolean hasFoundBlock(TileHolder bh) {
		if (bh.finder == null || !doKeepAltNum(bh.altNum)) return false;
		synchronized (foundBlocks) {
		    return foundBlocks.contains(new FoundBlock(bh));
		}
    }

    public DotPlotDBUser getDB() {
    	return db;
    }

    private void init() {
		printDebug("Initializing...");
	
		String drName = db.getDatabaseReader().getName();
		String dDrName = drName+" "+super.toString()+" D";
		String fDrName = drName+" "+super.toString()+" F";
		
		if (!(db instanceof FinderDBUser) && numFC > 0) 
		    db = FinderDBUser.newInstance(drName,db);
	
		if (numDC == 0) 
		    downloaderDatabases.put(db);
		else {
		    for (int i = 0; i < numDC; i++) { // make FinderDBUser in case finder uses it
				FinderDBUser dbUser = FinderDBUser.newInstance(dDrName+i,db);
				readers.add(dbUser.getDatabaseReader());
				downloaderDatabases.put(dbUser);
		    }
		}
	
		if (numFC > 0) {
		    finderDatabases = new CheckoutQueue();
		    for (int i = 0; i < numFC; i++) {
				FinderDBUser dbUser = FinderDBUser.newInstance(fDrName+i,db);
				readers.add(dbUser.getDatabaseReader());
				finderDatabases.put(dbUser);
		    }
		}
		else 
			finderDatabases = downloaderDatabases;
		
		DownloadThread threads[] = new DownloadThread[numD];
		for (int i = 0; i < numD; i++) {
		    threads[i] = new DownloadThread(downloaderDatabases);
		    downloaders.add(threads[i]);
		}
		new StartDownloaders(threads).start();
		
		for (int i = 0; i < numF; i++) {
		    Thread thread = new FinderThread(finderDatabases);
		    finders.add(thread);
		    thread.start();
		}
    }

    public synchronized boolean loadHits(ProjectProperties pp, Project[] projects, Tile block, 
					 ScoreBounds sb, boolean filtered) 
    {
		if (!isDead()) {
		    TileHolder bh = (TileHolder)downloaderQueue.remove(new TileHolder(block));
		    if (bh == null) {
		    	bh = new TileHolder(pp,projects,block,sb,filtered,0);
		    	blockHolders.add(bh);
		    }
		    else bh.filtered = filtered;
		    downloaderQueue.putFirst(bh);
		    return true;
		}
		return false;
    }
    
    public boolean execute(ProjectProperties pp, Project[] projects, Tile[] tiles, 
			   ScoreBounds sb, boolean filtered) 
    {
    	return execute(pp,projects,tiles,sb,filtered,-1,null,null,0);
    }

    public boolean execute(ProjectProperties pp, Project[] projects, 
    			Tile[] tiles, ScoreBounds sb, 
    			boolean filtered, int rid, String finder, 
    			Object[] params, int altNum) 
    {
    	return execute(pp,projects,tiles,sb,filtered,
    			new Finder[] {new Finder(rid,finder,params,altNum)});
    }

    public synchronized boolean execute(ProjectProperties pp, Project[] projects, 
    			Tile[] tiles, ScoreBounds sb,
				boolean filtered, Finder[] finders) 
    {
		if (METHOD_TRACE) System.out.println("Entering Loader.execute(...)");
		printDebug("In execute...");
		if (!hasState()) init();
		if (setActive()) {
		    if (tiles.length > 0)
		    	clearFoundHistory(projects,finders);
		    
		    List<TileHolder> holders = toHolders(pp,projects,tiles,sb,filtered,finders);
		    printDebug("Adding "+holders.size()+" tiles...");
		    blockHolders.addAll(holders);
		    downloaderQueue.put(holders);
		    downloaderQueue.resume();
		}
		if (METHOD_TRACE) System.out.println("Exiting Loader.execute(...)");
		return true;
    }

    public boolean pauseAndWait() throws InterruptedException {
		if (setPausing()) {
		    downloaderQueue.pause();
		    finderQueue.pause();
		    downloaderDatabases.waitTillFree();
		    finderDatabases.waitTillFree();
		    return setPaused(true);
		}
		return false;
    }

    public synchronized boolean pause() {
		if (setPausing()) {
		    downloaderQueue.pause();
		    finderQueue.pause();
		    new Thread() {
				public void run() {
				    try {
					downloaderDatabases.waitTillFree();
					finderDatabases.waitTillFree();
				    } catch (InterruptedException e) { }
				    setPaused(true);
				}
		    }.start();
		    return true;
		}
		return false;
    }

    public synchronized boolean resume() {
		if (setPaused(false)) {
		    downloaderQueue.resume();
		    finderQueue.resume();
		    return true;
		}
		return false;
    }

    public boolean stopAndWait() throws InterruptedException {
		if (stop()) {
		    waitTillStopped();
		    return true;
		}
		return false;
    }

    public synchronized boolean stop() {
		if (setStopping()) {
		    blockHolders.removeAll(downloaderQueue.clear());
		    if (blockHolders.isEmpty()) setStopped();
		    return true;
		}
		return false;
    }

    public synchronized boolean kill() {
		if (setDead()) {
		    downloaderDatabases.stop();
		    finderDatabases.stop();
		    downloaderQueue.stop();
		    finderQueue.stop();
		    new Thread() {
				public void run() {
				    synchronized (downloaders) {
						while (!downloaders.isEmpty()) {
						    printDebug(downloaders.size()+" Downloaders still working. Waiting...");
						    try {
						    	downloaders.wait(60000);
						    } catch (InterruptedException e) { }
						}
				    }
				    synchronized (finders) {
						while (!finders.isEmpty()) {
						    printDebug(finders.size()+" Finders still working. Waiting...");
						    try {
						    	finders.wait(60000);
						    } catch (InterruptedException e) { }
						}
				    }
				    if (isDead()) {
				    	for (DatabaseReader r : readers)
				    		r.close();
						readers.clear();
						downloaders.clear();
						finders.clear();
						downloaderQueue.clear();
						finderQueue.clear();
						downloaderDatabases.clear();
						finderDatabases.clear();
						blockHolders.clear();
				    }
				}
		    }.start();
		    return true;
		}
		return false;
    }

    public boolean isLoading() {
    	return hasState() && !isStopped() && !isDead();
    }

    private void setDone(TileHolder bh) {
		blockHolders.remove(bh);
		if (!blockHolders.contains(bh))
		    fireBlockDone(bh.getTile());
		if (blockHolders.isEmpty()) setDone();
    }

    private void downloaderDone(DownloadThread t) {
		printDebug("In downloaderDone("+t+")");
		synchronized (downloaders) {
		    printDebug("Downloader "+t+" is done.");
		    downloaders.remove(t);
		    if (downloaders.isEmpty()) {
				printDebug("Downloaders are all done");
				downloaders.notifyAll();
		    }
		}
    }
    
    private void finderDone(FinderThread t) {
		synchronized (finders) {
		    printDebug("Finder "+t+" is done.");
		    finders.remove(t);
		    if (finders.isEmpty()) {
				printDebug("Finders are all done");
				finders.notifyAll();
		    }
		}
    }

    protected void fireStateChange() {
		printDebug("Firing State Change...");
		super.fireStateChange();
    }

    protected void fireBlockDone(Tile block) {
		printDebug("Firing Block Done "+block);
		super.fireStateChange();
    }

    protected void fireBlockChange(Tile block) {
		printDebug("Firing Block Change "+block);
		super.fireStateChange();
    }

    private void sqlError(SQLException e, String message) {
		if (e != null) e.printStackTrace();
		if (message != null) System.err.println("SQL Error: "+message);
    }

    // mdb added 8/31/09 - handle out of memory error
    private synchronized void abort() { 
    	if (!aborted) {
	    	kill();
	    	Utilities.showOutOfMemoryMessage();
	    	aborted = true;
    	}
    }

    class DownloadThread extends Thread {
		private CheckoutQueue connections;
	
		public DownloadThread(CheckoutQueue databases) {
		    this.connections = databases;
		}
	
		public void run() {
		    TileHolder th;
		    Tile tile;
		    while ( (th = (TileHolder)downloaderQueue.get()) != null ) {
				DotPlotDBUser dbUser = (DotPlotDBUser)connections.get();
				if (dbUser == null || state() != ACTIVE) {
				    if (dbUser != null) connections.put(dbUser);
				    setDone(th);
				    break;
				}
				tile = th.getTile();
				printDebug(this+" has "+tile);
				try {
				    if (!tile.isSomeLoaded() || (!th.isFiltered() && !tile.isLoaded())) {
						if (!tile.isSomeLoaded()) {
						    dbUser.setIBlocks(tile, th.getProjects(), th.isSwapped());
						    fireBlockChange(tile);
						}
						dbUser.setHits(th.getProjects(), th.getProjectPair(),
								tile, th.isFiltered(), th.getScoreBounds(),
								th.isSwapped());
				    }
				    fireBlockChange(tile);
				    if (th.getFinder() == null) {
						if (th.getRID() > 0) {
						    if (tile.getAltRID(th.getAltNum()) != th.getRID())
						    	dbUser.setFBlocks(th.getRID(),tile,th.getAltNum());
						} // 0 rid used to indicate not to clear alt block runs
						else if (th.getRID() < 0) tile.setAltBlocksRun(th.getAltNum(),null);
							setDone(th);
				    }
				    else 
				    	finderQueue.put(th);
				} 
				catch (SQLException e) { if (!isDead()) sqlError(e, null); }
				catch (NullPointerException n) { if (!isDead()) n.printStackTrace(); }
				catch (OutOfMemoryError e) { abort(); } // mdb added 8/31/09
				connections.put(dbUser);
		    }
		    downloaderDone(this);
		}
    }

    class FinderThread extends Thread {
		private CheckoutQueue connections;
	
		public FinderThread(CheckoutQueue databases) {
		    this.connections = databases;
		}
	
		public void run() {
		    TileHolder bh;
		    while ( (bh = (TileHolder)finderQueue.get()) != null ) {
				if (hasFoundBlock(bh)) {
				    setDone(bh);
				    continue;
				}
				    
				FinderDBUser dbUser = (FinderDBUser)connections.get();
				BlockFinder bf = BlockFinder.getBlockFinder(bh.getProjectProperties(),dbUser,bh.getFinder(),bh.getTile(),bh.getRID());
				addChangeListener(bf);
				if (state() == ACTIVE) {
				    if (bh.hasParams()) bf.setParams(bh.getParams());
				    try {
				    	bf.setBlocks(bh.getAltNum());
				    } 
				    catch (SQLException e) { if (!isDead()) sqlError(e,null); }
				    catch (NullPointerException n) { if (!isDead()) n.printStackTrace(); }
		
				    if (state() == ACTIVE) addFoundBlock(bh);
				}
				removeChangeListener(bf);
				connections.put(dbUser);
				setDone(bh);
		    }
		    finderDone(this);
		}
    }

    private static void printDebug(String message) {
    	if (DEBUG) System.out.println("Loader: "+message);
    }

    private List<TileHolder> toHolders(ProjectProperties pp, Project[] projects, 
    		Tile[] tiles, ScoreBounds sb, boolean filtered, Finder[] finders) 
    {
		if (tiles == null) return null;
	
		if (finders == null || finders.length == 0)
		    finders = new Finder[] { new Finder(-1,null,null,0) };
	
		List<TileHolder> ret = new ArrayList<TileHolder>(tiles.length);
		for (Tile t : tiles)
			for (Finder f : finders)
		    	ret.add(f.getTileHolder(pp,projects,t,sb,filtered));
		
		return ret;
    }

    public static class Finder {
		private int rid;
		private String finder;
		private Object[] params;
		private int altNum;
	
		public Finder(int rid, String finder, Object[] params, int altNum) {
		    this.rid = rid;
		    this.finder = finder;
		    this.params = params;
		    this.altNum = altNum;
		}
	
		public int      getRID()    { return rid;    }
		public String   getFinder() { return finder; }
		public Object[] getParams() { return params; }
		public int      getAltNum() { return altNum; }
	
		private TileHolder getTileHolder(ProjectProperties pp, 
				Project[] projects, Tile tile, ScoreBounds sb, boolean filtered) 
		{
			// mdb added 12/16/09 #205 - kludge
			Project[] projPair = new Project[2];
			for (Project p : projects) {
				if (tile.getProjID(X) == p.getID())
					projPair[X] = p;
				if (tile.getProjID(Y) == p.getID())
					projPair[Y] = p;
			}
			
		    return new TileHolder(pp,/*projects*/projPair,tile,sb,filtered,rid,finder,params,altNum);
		}
    }

    private static class StartDownloaders extends Thread {
		private DownloadThread[] threads;
	
		public StartDownloaders(DownloadThread[] threads) {
		    super("Downloader Starter");
		    this.threads = threads;
		}
	
		public void start() throws IllegalThreadStateException {
		    if (TIME_BETWEEN_DOWNLOADERS > 0) super.start();
		    else run();
		}
	
		public void run() {
		    for (int i = 0; i < threads.length; i++) {
				printDebug("Starting Downloader...");
				threads[i].start();
				if (i+1 < threads.length && TIME_BETWEEN_DOWNLOADERS > 0) {
					try { sleep(TIME_BETWEEN_DOWNLOADERS); } 
					catch (InterruptedException e) { }
				}
		    }
		}
    }

    private static class FoundBlock {
		private int[] projects;
		private int[] groups;
		private int rid;
		private int altNum;
		private String finder;
	
		private FoundBlock(TileHolder bh) {
		    projects = new int[2];
		    groups   = new int[2];
		    for (int i = 0; i < 2; i++) {
				projects[i] = bh.projects[i].getID();
				groups[i]   = bh.tile.getGroup(i).getID();
		    }
		    rid    = bh.rid;
		    finder = bh.finder;
		    altNum = bh.altNum;
		}
	
		public boolean equals(Project[] projects, Finder[] finders) {
		    if (projects[0].getID() != this.projects[0] || projects[1].getID() != this.projects[1]) return false;
	
		    for (int i = 0; i < finders.length; i++) {
			if (altNum == finders[i].altNum)
			    return finders[i].rid == rid && finders[i].finder != null && finders[i].finder.equals(finder);
			    
		    }
	
		    return false;
		}
		
		public boolean equals(Object obj) {
		    if (obj instanceof FoundBlock) {
			FoundBlock fb = (FoundBlock)obj;
			if (fb.rid != rid || fb.altNum != altNum || (finder == null ? fb.finder != null : !finder.equals(fb.finder))) return false;
			for (int i = 0; i < 2; i++)
			    if (projects[i] != fb.projects[i] || groups[i] != fb.groups[i]) return false;
			return true;
		    }
		    if (obj instanceof TileHolder) return equals(new FoundBlock((TileHolder)obj));
		    return false;
		}
    }

    private static class TileHolder {
		private ProjectProperties pp;
		private Project[] projects;
		private Tile tile;
		private ScoreBounds sb;
		private boolean filtered;
		private int rid;
		private int altNum;
		private String finder;
		private Object[] params;
	
		private TileHolder(Tile t) {
		    tile = t;
		}
	
		public TileHolder(ProjectProperties pp, Project[] projects, 
				Tile tile, ScoreBounds sb, boolean filtered, int altNum) 
		{
		    this(pp,projects,tile,sb,filtered,0,null,null,altNum); //Sets rid to 0, should indicate not to clear the alt hits
		}
	
		public TileHolder(ProjectProperties pp, Project[] projects, 
					Tile tile, ScoreBounds sb, boolean filtered, 
					int rid, String finder, Object[] params, int altNum) 
		{
		    this.pp = pp;
		    this.projects = projects;
		    this.tile = tile;
		    this.sb = sb;
		    this.filtered = filtered;
		    this.rid = rid;
		    this.finder = finder;
		    this.params = params;
		    this.altNum = altNum;
		}
	
		public boolean equals(Object obj) {
		    if (obj instanceof TileHolder) return tile.equals(((TileHolder)obj).tile);
		    if (obj instanceof FoundBlock)  return new FoundBlock(this).equals(obj);
		    return false;
		}
		
		public ProjectPair getProjectPair() {
		    return pp.getProjectPair(projects[0].getID(),projects[1].getID());
		}
		
		public boolean isSwapped() { return pp.isSwapped(projects[X].getID(), projects[Y].getID()); }
		public ProjectProperties getProjectProperties() { return pp; }
		public Project[] getProjects() { return projects; }
		public Tile getTile() { return tile; }
		public ScoreBounds getScoreBounds() { return sb; }
		public boolean isFiltered() { return filtered; }
		public int getRID() { return rid; }
		public String getFinder() { return finder; }
		public Object[] getParams() { return params; }
		public boolean hasParams() { return params != null && params.length > 0; }
		public int getAltNum() { return altNum; }
		public String toString() { return tile.toString(); }
    }
}
