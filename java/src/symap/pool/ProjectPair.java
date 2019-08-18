package symap.pool;

import java.util.List;
import dotplot.Project;
import symap.mapper.Mapper; // for map type

public class ProjectPair {
	private int p1, p2;
	private static final int max_mrk_hits = 1000; // mdb changed 1/7/09 - made constant
	private int min_mrk_clones_hit = -1;
	private int mapType = -1;
	private double p1Scale = 1;
	private int pid;

	private ProjectPair(int pid, int p1, int p2) {
		this.pid = pid;
		this.p1  = p1;
		this.p2  = p2;
	}

	public ProjectPair(int pid, int p1, int p2, /*int maxMrkHits, int minMrkClonesHit,*/ int mapType, double p1Scale) {
		this(pid,p1,p2);
		//this.max_mrk_hits = maxMrkHits; // mdb unused 1/7/09
		//this.min_mrk_clones_hit = minMrkClonesHit; // mdb unused 1/7/09
		this.mapType = mapType;
		this.p1Scale = p1Scale;
	}

	/**
	 * Creates a new <code>ProjectPair</code> instance. This instance doesn't 
	 * have the pair ids set, so should only be used to locate valid 
	 * ProjectPairs since the comparison methods (e.g., equals()) only 
	 * consider the project ids. 
	 *
	 * @param p1 an <code>int</code> value
	 * @param p2 an <code>int</code> value
	 */
	protected ProjectPair(int p1, int p2) {
		this(0,p1,p2);
	}

	public int getPair() { return pid; }
	public int getP1() { return p1; }
	public int getP2() { return p2; }

	public int getMaxMarkerHits() {
		if (max_mrk_hits < 0)
			System.err.println("Max Marker Hits for "+this+" is not set!!!!");
		return max_mrk_hits;
	}

	public int getMinMrkClonesHit() {
		if (min_mrk_clones_hit < 0)
			System.err.println("Min Marker Clones Hit for "+this+" is not set!!!!");
		return min_mrk_clones_hit;
	}

	public int getMapType() { return mapType; }
	public double getScale() { return p1Scale; }
	public boolean isPseudoPseudo() { return mapType == Mapper.PSEUDO2PSEUDO; } // mdb added 7/11/07 #121
	public boolean isFPCPseudo() { return mapType == Mapper.FPC2PSEUDO; }
	public boolean isFPCFPC() { return mapType == Mapper.FPC2FPC; }
	public String toString() { return "[ProjectPair: "+pid+" ("+p1+","+p2+")]"; }
	public int hashCode() { return (p1 * p2); }

	public boolean equals(Object obj) {
		return obj instanceof ProjectPair && equal(this,((ProjectPair)obj));
	}

	public static boolean equal(Project[] projects, ProjectPair p2) {
		if (projects == null) return p2 == null;
		if (projects[0] == null || projects[1] == null) return false;
		return new ProjectPair(projects[0].getID(),projects[1].getID()).equals(p2);
	}

	public static boolean equal(ProjectPair pp1, ProjectPair pp2) {
		if (pp1 == null || pp2 == null) return (pp1 == null && pp2 == null);
		return (pp1.p1 == pp2.p1 && pp1.p2 == pp2.p2) || (pp1.p1 == pp2.p2 && pp1.p2 == pp2.p1);
	}

	public static ProjectPair getProjectPair(List<ProjectPair> projectPairs, int p1, int p2) {
// mdb removed 7/1/09		
//		ProjectPair pp = new ProjectPair(p1,p2);
//		int idx = projectPairs.indexOf(pp); // ignores order of p1/p2
//		if (idx < 0) pp = null;
//		else pp = (ProjectPair)projectPairs.get(idx);
//		return pp;
		
		// mdb added 7/1/09
		for (ProjectPair pp : projectPairs) {
			if ((pp.getP1() == p1 && pp.getP2() == p2)
					|| (pp.getP1() == p2 && pp.getP2() == p1))
				return pp;
		}
		return null;
	}

	// mdb added 4/1/09 #160
	public static boolean hasProjectPair(List<ProjectPair> projectPairs, int p1, int p2) {
		for (ProjectPair pp : projectPairs) {
			if (pp.p1 == p1 && pp.p2 == p2)
				return true;
		}
		return false;
	}
}
