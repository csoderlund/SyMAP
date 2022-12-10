package dotplot;

import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Vector;

import util.DatabaseReader;
import util.StateObject;
import util.Queue;
import util.CheckoutQueue;
import util.Utilities;

import symap.pool.ProjectProperties;
import symap.SyMAP;
import symap.pool.ProjectPair;

public class Loader extends StateObject implements DotPlotConstants {
    private static final boolean DEBUG = SyMAP.DEBUG;
    private static final int TIME_BETWEEN_DOWNLOADERS = 0;
    
    private DotPlotDBUser db;
    
    private int numD=10;

    private List<DatabaseReader> readers;
    private List<DownloadThread> downloaders;
    private List<Thread> finders;

    private Queue downloaderQueue, finderQueue;
    private CheckoutQueue downloaderDatabases, finderDatabases;
    private List<TileHolder> blockHolders;

    private List<FoundBlock> foundBlocks;
    
    private boolean aborted = false; 

    // called by Data
    public Loader(DotPlotDBUser db) {
		this();
		this.db  = db;
    }
    
    private Loader() {
		readers         = new ArrayList<DatabaseReader>();
		downloaders     = new ArrayList<DownloadThread>();
		finders         = new ArrayList<Thread>();
		downloaderQueue = new Queue("Downloader Queue");
		finderQueue     = new Queue("Finder Queue");
		downloaderDatabases = finderDatabases = new CheckoutQueue();
		blockHolders    = new Vector<TileHolder>();
	
		foundBlocks   = new Vector<FoundBlock>();
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
    public DotPlotDBUser getDB() {
    	return db;
    }

    private void init() {
		printDebug("Initializing...");
	
		downloaderDatabases.put(db);
		finderDatabases = downloaderDatabases;
		
		DownloadThread threads[] = new DownloadThread[numD];
		for (int i = 0; i < numD; i++) {
		    threads[i] = new DownloadThread(downloaderDatabases);
		    downloaders.add(threads[i]);
		}
		new StartDownloaders(threads).start();
    }
    // Data
    public boolean execute(ProjectProperties pp, Project[] projects, Tile[] tiles, 
			   FilterData fd, boolean filtered) 
    {
    	return execute(pp,projects,tiles,fd,filtered,-1,null,null,0);
    }

    private boolean execute(ProjectProperties pp, Project[] projects, 
    			Tile[] tiles, FilterData fd, 
    			boolean filtered, int rid, String finder, 
    			Object[] params, int altNum) 
    {
    	return execute(pp,projects,tiles,fd,filtered,
    			new Finder[] {new Finder(rid,finder,params,altNum)});
    }

    private synchronized boolean execute(ProjectProperties pp, Project[] projects, 
    			Tile[] tiles, FilterData fd,
				boolean filtered, Finder[] finders) 
    {
		if (METHOD_TRACE) System.out.println("Entering Loader.execute(...)");
		printDebug("In execute...");
		if (!hasState()) init();
		
		if (setActive()) {
		    if (tiles.length > 0)
		    	clearFoundHistory(projects,finders);
		    
		    List<TileHolder> holders = toHolders(pp,projects,tiles,fd,filtered,finders);
		    printDebug("Adding "+holders.size()+" tiles...");
		    blockHolders.addAll(holders);
		    downloaderQueue.put(holders);
		    downloaderQueue.resume();
		}
		if (METHOD_TRACE) System.out.println("Exiting Loader.execute(...)");
		return true;
    }
    // Data.clear
    public synchronized boolean stop() {
		if (setStopping()) {
		    blockHolders.removeAll(downloaderQueue.clear());
		    if (blockHolders.isEmpty()) setStopped();
		    return true;
		}
		return false;
    }
    // Data.kill, abort()
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
    // Data
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
								tile, th.isFiltered(), th.getFilterData(),
								th.isSwapped());
				    }
				    fireBlockChange(tile);
				    if (th.getFinder() == null) {
						setDone(th);
				    }
				    else 
				    	finderQueue.put(th);
				} 
				catch (SQLException e) { if (!isDead()) sqlError(e, null); }
				catch (NullPointerException n) { if (!isDead()) n.printStackTrace(); }
				catch (OutOfMemoryError e) { abort(); } 
				connections.put(dbUser);
		    }
		    downloaderDone(this);
		}
    }

    private static void printDebug(String message) {
    	if (DEBUG) System.out.println("Loader: "+message);
    }

    private List<TileHolder> toHolders(ProjectProperties pp, Project[] projects, 
    		Tile[] tiles, FilterData fd, boolean filtered, Finder[] finders) 
    {
		if (tiles == null) return null;
	
		if (finders == null || finders.length == 0)
		    finders = new Finder[] { new Finder(-1,null,null,0) };
	
		List<TileHolder> ret = new ArrayList<TileHolder>(tiles.length);
		for (Tile t : tiles)
			for (Finder f : finders)
		    	ret.add(f.getTileHolder(pp,projects,t,fd,filtered));
		
		return ret;
    }

    private static class Finder {
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
	
		private TileHolder getTileHolder(ProjectProperties pp, 
				Project[] projects, Tile tile, FilterData fd, boolean filtered) 
		{
			Project[] projPair = new Project[2];
			for (Project p : projects) {
				if (tile.getProjID(X) == p.getID())
					projPair[X] = p;
				if (tile.getProjID(Y) == p.getID())
					projPair[Y] = p;
			}
			
		    return new TileHolder(pp,/*projects*/projPair,tile,fd,filtered,rid,finder,params,altNum);
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
		private FilterData fd;
		private boolean filtered;
		private int rid;
		private int altNum;
		private String finder;
		
		public TileHolder(ProjectProperties pp, Project[] projects, 
					Tile tile, FilterData fd, boolean filtered, 
					int rid, String finder, Object[] params, int altNum) 
		{
		    this.pp = pp;
		    this.projects = projects;
		    this.tile = tile;
		    this.fd = fd;
		    this.filtered = filtered;
		    this.rid = rid;
		    this.finder = finder;
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
		
		public Project[] getProjects() { return projects; }
		public Tile getTile() { return tile; }
		public FilterData getFilterData() { return fd; }
		public boolean isFiltered() { return filtered; }
		public String getFinder() { return finder; }
		public String toString() { return tile.toString(); }
    }
}
