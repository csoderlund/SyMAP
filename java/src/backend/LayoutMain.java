package backend;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.Vector;
import java.util.HashMap;
import java.util.Random;
import java.sql.ResultSet;
import java.sql.SQLException;

import symap.pool.DatabaseUser;
import util.Logger;
import util.Utilities;
import backend.Project;
import backend.UpdatePool;


// mdb added 10/15/09 #200
public class LayoutMain {
	private UpdatePool pool;
	private Logger log;
	
	public LayoutMain(UpdatePool pool, Logger log) {
		this.pool = pool;
		this.log = log;
	}
	
	// FIXME: This is prototype code, needs to be optimized.
	private void doLayout(Project p1, Project p2) throws SQLException 
	{
		// Load from database
		Vector<Block> blocks = loadBlocks(p1, p2);
		HashMap<Integer,Group> groups1 = loadGroups(p1);
		HashMap<Integer,Group> groups2 = loadGroups(p2);
		int numQGroups = groups1.size();
		
		// Remove contained blocks
		Vector<Block> containedBlocks = new Vector<Block>();
		for (int i = 0;  i < blocks.size()-1;  i++) {
			Block b1 = blocks.get(i);
			for (int j = i+1;  j < blocks.size();  j++) {
				Block b2 = blocks.get(j);
				if (b1.contains(b2) && !containedBlocks.contains(b2))
					containedBlocks.add(b2);
				else if (b2.contains(b1) && !containedBlocks.contains(b1))
					containedBlocks.add(b1);
			}
		}
		blocks.removeAll(containedBlocks);
		blocks.trimToSize();
		
		// Bin blocks based on target
		HashMap<Group,Vector<Group>> targetToQueryBin = new HashMap<Group,Vector<Group>>();
		ScoreMatrix qqScores = new ScoreMatrix(numQGroups);
		for (Block b : blocks) {
			Group g1 = groups1.get(b.grp1_idx);
			Group g2 = groups2.get(b.grp2_idx);
			
			b.score = b.score / g2.length;
			g1.blocks.add(b);
			g2.blocks.add(b);
			
			if (!targetToQueryBin.containsKey(g2))
				targetToQueryBin.put(g2, new Vector<Group>());
			Vector<Group> bin = targetToQueryBin.get(g2);
			if (!bin.contains(g1))
				bin.add(g1);
		}
		
		// Build query-to-query score matrix
		final int MAX_GAP_BRIDGE = 100000; // someday should be changed to relative value
		for (Group g2 : targetToQueryBin.keySet()) {
			Vector<Vector<Block>> orderBins = new Vector<Vector<Block>>();
			orderBins.add( new Vector<Block>() );
			orderBins.firstElement().add( g2.blocks.firstElement() );
			for (int i = 0, o = 0;  i < g2.blocks.size()-1;  i++) {
				// Note: blocks were loaded in order by start2
				Block b1 = g2.blocks.get(i);
				Block b2 = g2.blocks.get(i+1);
				
				if (Math.abs(b1.start2 - b2.start2) > MAX_GAP_BRIDGE)
					o++;
				
				if (orderBins.size() == o)
					orderBins.add( new Vector<Block>() );
				
				orderBins.get(o).add(b2);
			}
			
			for (int i = 0;  i < orderBins.size()-1;  i++) {
				Vector<Block> bin1 = orderBins.get(i);
				Vector<Block> bin2 = orderBins.get(i+1);
				for (Block b1 : bin1) {
					for (Block b2 : bin2) {
						int o1 = groups1.get(b1.grp1_idx).order;
						int o2 = groups1.get(b2.grp1_idx).order;
						qqScores.put(o1, o2, qqScores.get(o1, o2) + b1.score + b2.score);
					}
				}
			}
		}
		
		long seed = System.currentTimeMillis() % 10000;
		log.msg("seed:          " + seed);
		Random rand = new Random(seed);
		Permutation perm = new Permutation(numQGroups);
		//perm.shuffle(rand, numQGroups); // scramble initially for testing
		
		double minScore = calcScore(qqScores, perm, -1, -1);
		log.msg("initial score: " + minScore);
		if (minScore == 0) {
			log.msg("Error: zero initial score");
			return;
		}
		
		// Print score table
//		qqScores.print(perm);
		
		int numSwaps = 0;
		int noSwap = 0;
		int yesSwap = 0;
		final int MAX_NO_SWAP = 10000000;
		long startTime = System.currentTimeMillis();
		
		// SHUFFLE
		yesSwap = 0;
		noSwap = 0;
		while (noSwap < MAX_NO_SWAP/2) {
			Permutation perm2 = perm.clone();
			perm.shuffle(rand, numQGroups/2);
			double score = calcScore(qqScores, perm, -1, -1);
			if (score < minScore) {
				yesSwap++;
				minScore = score;
				noSwap = 0;
			}
			else {
				perm = perm2; // unswap
				noSwap++;
			}
			
			numSwaps++;
		}
		log.msg("shuffle swaps: " + yesSwap);
		
		// SWAP
		yesSwap = 0;
		noSwap = 0;
		while (noSwap < MAX_NO_SWAP) {
			int x = rand.nextInt(numQGroups);
			int y = rand.nextInt(numQGroups);
			if (x != y) {
				double score = calcScore(qqScores, perm, x, y);
				if (score < minScore) {
					yesSwap++;
					minScore = score;
					perm.swap(x, y);
					noSwap = 0;
				}
				else
					noSwap++;
				
				numSwaps++;
			}
		}
		log.msg("pair swaps:    " + yesSwap);
		
		log.msg("final score:   " + minScore);
		log.msg("no swap:       " + noSwap);
		log.msg("swap attempts: " + numSwaps);
		
		// Print score table and permutation
//		qqScores.print(perm);
//		perm.print();
		
		String newOrder = "";
		for (int i = 0;  i < numQGroups;  i++) {
			Group g = getGroupByOrder(groups1, perm.get(i));
			if (g.blocks.isEmpty())
				newOrder += "*";
			newOrder += g.name + " ";
		}
		log.msg(newOrder);
		
		updateSortOrder(groups1, perm);
		
		log.msg("Done:  " + Utilities.getDurationString(System.currentTimeMillis() - startTime) + "\n");
	}
	
	private void updateSortOrder(HashMap<Integer,Group> groups, Permutation p) throws SQLException {
		for (int i = 0;  i < groups.size();  i++) {
			Group g = getGroupByOrder(groups, p.get(i));
			if (i != g.order) {
				String strQ = "UPDATE groups SET sort_order=" + (i+1) + " WHERE idx=" + g.idx;
				pool.executeUpdate(strQ);
			}
		}
	}
	
	private Group getGroupByOrder(HashMap<Integer,Group> groups, int order) {
		for (Group g : groups.values()) {
			if (g.order == order)
				return g;
		}
		return null;
	}
	
	private double calcScore(ScoreMatrix scores, Permutation perm, int x, int y) 
	{
		double score = 0;
		
		int i2, j2;
		for (int i = 0;  i < perm.size()-1;  i++) {
			if (i == x)      i2 = perm.get(y);
			else if (i == y) i2 = perm.get(x);
			else             i2 = perm.get(i);
			
			for (int j = i+1;  j < perm.size();  j++) {
				if (j == x)      j2 = perm.get(y);
				else if (j == y) j2 = perm.get(x);
				else             j2 = perm.get(j);
				
				double Wij = scores.get(i2, j2);
				score += Math.abs(i-j) * Wij;
			}
		}
		
		return score;
	}
	
//	private int calcScoreDiff(int numHits, ScoreMatrix scores, Permutation perm, int x, int y)
//	{
//		int delta = 0;
//		
//		for (int i = 0;  i < numHits;  i++) {
//			int k;
//			if (i == x)      k = perm.get(y);
//			else if (i == y) k = perm.get(x);
//			else             k = perm.get(i);
//			
//			double Wyi = scores.get(y,k);
//			double Wxi = scores.get(x,k);
//			delta += Math.abs(x-i) - Math.abs(y-i) * Wyi - Wxi; // from SAM paper, but doesn't work
//		}
//
//		return delta;
//	}
	
	private class ScoreMatrix {
		private double[][] scores; // inefficient for symmetric matrix
		private int size;
		
		public ScoreMatrix(int size) {
			this.size = size;
			scores = new double[size][size];
			for (int i = 0;  i < size;  i++)
				for (int j = 0;  j < size;  j++)
					scores[i][j] = 0;
		}
		
		public double get(int x, int y) {
			if (x > y) {
				int temp = x;
				x = y;
				y = temp;
			}
			return scores[x][y];
		}
		
		public void put(int x, int y, double val) {
			if (x > y) {
				int temp = x;
				x = y;
				y = temp;
			}
			scores[x][y] = val;
		}
		
		public void print(Permutation p) {
			System.out.println();
			System.out.print("   ");
			for (int i = 0;  i < size;  i++)
				System.out.print( Utilities.pad(""+p.get(i), 5));
			System.out.println();
			for (int i = 0;  i < size;  i++) {
				int x = p.get(i);
				System.out.print(Utilities.pad(""+x, 3));
				for (int j = 0;  j < size;  j++) {
					int y = p.get(j);
					System.out.print(Utilities.pad((x == y ? "" : ""+get(x,y)), 5));
				}
				System.out.println();
			}
		}
	}
	
	private class Permutation {
		private int[] map;
		
		public Permutation(int size) {
			map = new int[size];
			for (int i = 0;  i < size;  i++)
				map[i] = i;
		}
		
		public Permutation(Permutation p) {
			map = new int[p.map.length];
			for (int i = 0;  i < p.map.length;  i++)
				map[i] = p.map[i];
		}
		
		public int get(int x) {
			return map[x];
		}
		
		public int size() { 
			return map.length;
		}
		
		public void swap(int x, int y) {
			int temp = map[x];
			map[x] = map[y];
			map[y] = temp;
		}
		
		public Permutation clone() {
			return new Permutation(this);
		}
		
		public void shuffle(Random rand, int num) {
			while (num > 0) {
				int x = rand.nextInt(map.length);
				int y = rand.nextInt(map.length);
				if (x != y) {
					swap(x, y);
					num--;
				}
			}
		}
		
		public void print() {
			System.out.println();
			System.out.println(toString());
		}
		
		public String toString() {
			String s = "";
			for (int i = 0;  i < map.length;  i++)
				s += get(i) + " ";
			return s;
		}
	}
	
	private class Group {
		int idx;
		int order;
		String name;
		int length;
		Vector<Block> blocks;
		
		public Group(int idx, int order, String name, int length) {
			this.idx   = idx;
			this.order = order;
			this.name  = name;
			this.length = length;
			this.blocks = new Vector<Block>();
		}
	}
	
	private HashMap<Integer,Group> loadGroups(Project p) throws SQLException {
		HashMap<Integer,Group> groups = new HashMap<Integer,Group>();
		
		String strQ = "SELECT idx,sort_order,name,length " +
			"FROM groups AS g " +
			"JOIN pseudos AS p ON (g.idx=p.grp_idx) " +
			"WHERE proj_idx=" + p.getIdx() + " " +
			"ORDER BY sort_order";
		ResultSet rs = pool.executeQuery(strQ);
		
		while (rs.next()) {
			int idx     = rs.getInt("idx");
			int order   = rs.getInt("sort_order") - 1;
			String name = rs.getString("name");
			int length  = rs.getInt("length");
			
			groups.put(idx, new Group(idx, order, name, length) );
		}
		rs.close();
		
		return groups;
	}
	
	private class Block {
		int idx;
		int grp1_idx, grp2_idx;
		int start1, end1;
		int start2, end2;
		double score;
		
		public Block(int idx, int grp1_idx, int grp2_idx, int start1, int end1,
				int start2, int end2, int score) 
		{
			this.idx      = idx;
			this.grp1_idx = grp1_idx;
			this.grp2_idx = grp2_idx;
			this.start1   = (start1 < end1 ? start1 : end1);
			this.end1     = (start1 < end1 ? end1   : start1);
			this.start2   = (start2 < end2 ? start2 : end2);
			this.end2     = (start2 < end2 ? end2   : start2);
			this.score    = score;
		}
		
		public String toString() {
			return "idx=" + idx + " grp1=" + grp1_idx + " grp2=" + grp2_idx +
					" score=" + score + " start1=" + start1 + " end1=" + end1 +
					" end2=" + end2;
		}
		
		public boolean contains(Block b) {
			return (b.grp2_idx == this.grp2_idx && b.start2 >= this.start2 && b.end2 <= this.end2);
		}
	}
	
	private Vector<Block> loadBlocks(Project p1, Project p2) throws SQLException {
		Vector<Block> blocks = new Vector<Block>();
		
		String subQ = "(SELECT SUM(ABS(ph.end2-ph.start2)) " +
			"FROM pseudo_block_hits AS pbh " +
			"JOIN pseudo_hits AS ph ON (pbh.hit_idx=ph.idx) " +
			"WHERE pbh.block_idx=b.idx) AS coverage ";
		
		String strQ = "SELECT idx,grp1_idx,grp2_idx,start1,end1,start2,end2, " +
			subQ +
			"FROM blocks AS b " +
			"WHERE proj1_idx=" + p1.getIdx() + " AND proj2_idx=" + p2.getIdx() + " " +
			"ORDER BY start2";
		ResultSet rs = pool.executeQuery(strQ);
		
		while (rs.next()) {
			int idx      = rs.getInt("idx");
			int grp1_idx = rs.getInt("grp1_idx");
			int grp2_idx = rs.getInt("grp2_idx");
			int start1   = rs.getInt("start1");
			int end1     = rs.getInt("end1");
			int start2   = rs.getInt("start2");
			int end2     = rs.getInt("end2");
			int coverage = rs.getInt("coverage");
			
			blocks.add( new Block(idx, grp1_idx, grp2_idx, start1, end1, start2, end2, coverage) );
		}
		rs.close();
		
		return blocks;
	}
	
	public boolean run(String proj1Name, String proj2Name) throws Exception
	{
		log.msg("Laying-out " + proj1Name + " against " + proj2Name);
		
		SyProps props1 = new SyProps(log, new File("data/pseudo/" + proj1Name + "/params"));
		SyProps props2 = new SyProps(log, new File("data/pseudo/" + proj2Name + "/params"));
		
		
		String pairName = proj1Name + "_to_" + proj2Name;
		String pairDir = "data/pseudo_pseudo/" + pairName; // default to pseudo_pseudo
		if ( !Utilities.pathExists(pairDir) ) {
			log.msg("Can't find pair directory " + pairDir);
			return false;
		}
		
		SyProps pairProps = new SyProps(log, new File(pairDir + "/params"));
		
		Project p1 = new Project(pool, log, pairProps, proj1Name, ProjType.pseudo, QueryType.Query);
		Project p2 = new Project(pool, log, pairProps, proj2Name, ProjType.pseudo, QueryType.Target);
		
		doLayout(p1, p2);
		
		return true;
	}
	
	public static void main(String[] args) 
	{
		if (args.length < 2) {
			System.out.println("Usage:  layout <project1> <project2>\n");
			System.exit(-1);
		}
		
		try {
			Properties mDBProps = new Properties();
			mDBProps.load(new FileInputStream("params"));
	
			String dbstr = DatabaseUser.getDatabaseURL(
					mDBProps.getProperty("db_server"), 
					mDBProps.getProperty("db_name"));
			UpdatePool pool = new UpdatePool(dbstr,
								mDBProps.getProperty("db_adminuser"),
								mDBProps.getProperty("db_adminpasswd"));
	
			LayoutMain layout = new LayoutMain(pool, new Log("symap.log"));
			layout.run(args[0], args[1]);
			DatabaseUser.shutdown();
		}
		catch (Exception e) {
			e.printStackTrace();
			DatabaseUser.shutdown();
			System.exit(-1);
		}
	}
}
