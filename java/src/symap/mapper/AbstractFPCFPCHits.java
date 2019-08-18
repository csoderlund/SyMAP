package symap.mapper;

// mdb excluded from build 7/22/07 #134
public abstract class AbstractFPCFPCHits extends AbstractHitData implements Comparable {
	private int c1, c2;

	public AbstractFPCFPCHits(int p1, int c1, int p2,  int c2) {
		//super(p1,p2); // mdb removed 7/25/07 #134
		super(p1,c1,p2,c2,Mapper.FPC2FPC,false); // mdb added 7/25/07 #134
		this.c1 = c1;
		this.c2 = c2;
	}

	public boolean equals(Object obj) {
		if (obj instanceof AbstractFPCFPCHits && super.equals(obj)) {
			AbstractFPCFPCHits d = (AbstractFPCFPCHits)obj;
			return c1 == d.c1 && c2 == d.c2;
		}
		return false;
	}

	public String toString() {
		return "[FPCFPCHit: p1="+getProject1()+" c1="+c1+" p2="+getProject2()+" c2="+c2+"]";
	}

	public int compareTo(Object obj) {
		AbstractFPCFPCHits d = (AbstractFPCFPCHits)obj;
		//if (getProject1() != d.getProject1()) return getProject1()-d.getProject1();
		//if (getProject2() != d.getProject2()) return getProject2()-d.getProject2();
		if (c1 != d.c1) return c1-d.c1;
		return c2-d.c2;
	}

	public int getContig1() {
		return c1;
	}

	public int getContig2() {
		return c2;
	}
}
