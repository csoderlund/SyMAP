package symap.pool;

public class NamedProjectPair {
    private ProjectPair pp;
    private String n1, n2;

    public NamedProjectPair(ProjectPair pp, String p1Name, String p2Name) {
	this.pp = pp;
	this.n1 = p1Name;
	this.n2 = p2Name;
    }

    public String toString() {
	return n1+" to "+n2;
    }

    public String getP1Name() {
	return n1;
    }

    public String getP2Name() {
	return n2;
    }

    public boolean equals(Object obj) {
	if (obj instanceof NamedProjectPair) {
	    return pp.equals(((NamedProjectPair)obj).pp);
	}
	return false;
    }

    public ProjectPair getPP() {
	return pp;
    }

    public int getP1() {
	return pp.getP1();
    }

    public int getP2() {
	return pp.getP2();
    }

    public int getMaxMarkerHits() {
	return pp.getMaxMarkerHits();
    }
}

