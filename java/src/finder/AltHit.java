package finder;

import java.util.Comparator;
import dotplot.Hit;

public class AltHit extends Hit implements Cloneable {
	private static final double LOG_10_BASE_E = Math.log(10);

	private Hit hit;
	private int altX, altY;
	private int score;

	public AltHit(Hit hit, double xFactor, double yFactor) {
		this.hit = hit;
		altX = (int) Math.round(xFactor * (double) hit.getCoord(X));
		altY = (int) Math.round(yFactor * (double) hit.getCoord(Y));
		if (hit.getEvalue() <= 0)
			score = Integer.MAX_VALUE;
		else
			score = (int) (-Math.log(hit.getEvalue() / LOG_10_BASE_E));
	}

	public AltHit(Hit hit) {
		this.hit = hit;
		altX = hit.getCoord(X);
		altY = hit.getCoord(Y);
		if (hit.getEvalue() <= 0)
			score = Integer.MAX_VALUE;
		else
			score = (int) (-Math.log(hit.getEvalue() / LOG_10_BASE_E));
	}

	public AltHit(Hit hit, int score) {
		this.hit = hit;
		this.score = score;
		altX = hit.getCoord(X);
		altY = hit.getCoord(Y);
	}

	public AltHit(Hit hit, double xFactor, double yFactor, int score) {
		this.hit = hit;
		this.score = score;
		altX = (int) Math.round(xFactor * (double) hit.getCoord(X));
		altY = (int) Math.round(yFactor * (double) hit.getCoord(Y));
	}

	public Hit getHit() {
		return hit;
	}

	public int getScore() {
		return score;
	}

	public void setScore(int s) {
		score = s;
	}

	public void boundScore(int max) {
		if (score > max)
			score = max;
	}

	public int getMaxScore(int max) {
		return score < Integer.MAX_VALUE && score > max ? score : max;
	}

	public void mergeScore(AltHit h) {
		score += h.score;
	}

	public int getAltY() {
		return altY;
	}

	public int getAltX() {
		return altX;
	}

	public int getAltPos(int axis) {
		return axis == X ? altX : altY;
	}

	public void setAltY(int y) {
		altY = y;
	}

	public void setAltX(int x) {
		altX = x;
	}

	public void setAlt(double xFactor, double yFactor) {
		altX = (int) Math.round(xFactor * (double) getX());
		altY = (int) Math.round(yFactor * (double) getY());
	}

	public void setAltYFlipped(int maxY) {
		altY = maxY - altY;
	}

	public void setAltXFlipped(int maxX) {
		altX = maxX - altX;
	}

	public Object clone() {
		AltHit h = (AltHit) super.clone();
		h.hit = hit; //(Hit)hit.clone(); // not deep since hit isn't going to change
		h.altX = altX;
		h.altY = altY;
		h.score = score;
		return h;
	}

	public int getCoord(int axis) {
		return hit.getCoord(axis);
	}

	public int getX() {
		return hit.getX();
	} // just for performance

	public int getY() {
		return hit.getY();
	}

	public double getEvalue() {
		return hit.getEvalue();
	}

	public double getPctid() {
		return hit.getPctid();
	}

	public int getType() {
		return hit.getType();
	}
	
	// mdb added 12/17/07
	public int getLength() {
		return 0; // fixme
	}

	public boolean isRepetitive() {
		return hit.isRepetitive();
	}

	public boolean isBlock() {
		return hit.isBlock();
	}

	public boolean isBlock(int altNum) {
		return hit.isBlock(altNum);
	}

	public void setAltBlock(int altNum, boolean block) {
		hit.setAltBlock(altNum, block);
	}

	/*
	public static double pearson(List path, AltHit[] hits) {
	int i;
	BigInteger x, y;
	BigInteger xx_sum = BigInteger.ZERO, x_sum = BigInteger.ZERO, yy_sum = BigInteger.ZERO, y_sum = BigInteger.ZERO, xy_sum = BigInteger.ZERO;
	Iterator iter;

	for (iter = path.iterator(); iter.hasNext(); ) {
	    i = ((Integer)iter.next()).intValue();
	    x = BigInteger.valueOf(hits[i].altX);
	    y = BigInteger.valueOf(hits[i].altY);

	    x_sum = x_sum.add(x);
	    y_sum = y_sum.add(y);

	    xx_sum = xx_sum.add(x.multiply(x));
	    yy_sum = yy_sum.add(y.multiply(y));

	    xy_sum = xy_sum.add(x.multiply(y));
	}

	return figurePearson(path.size(),x_sum,y_sum,xx_sum,yy_sum,xy_sum);
	}

	public static double pearson(List hits) {
	AltHit h;
	BigInteger x, y;
	BigInteger xx_sum = BigInteger.ZERO, x_sum = BigInteger.ZERO, yy_sum = BigInteger.ZERO, y_sum = BigInteger.ZERO, xy_sum = BigInteger.ZERO;
	Iterator iter;

	for (iter = hits.iterator(); iter.hasNext(); ) {
	    h = (AltHit)iter.next();
	    x = BigInteger.valueOf(h.altX);
	    y = BigInteger.valueOf(h.altY);

	    x_sum = x_sum.add(x);
	    y_sum = y_sum.add(y);

	    xx_sum = xx_sum.add(x.multiply(x));
	    yy_sum = yy_sum.add(y.multiply(y));

	    xy_sum = xy_sum.add(x.multiply(y));
	}

	return figurePearson(hits.size(),x_sum,y_sum,xx_sum,yy_sum,xy_sum);
	}
	 */

	public static AltHit[] getAltHitsCopy(Hit[] hits, double xFactor,
			double yFactor, boolean filtered) {
		if (hits == null || hits.length == 0)
			return new AltHit[0];
		AltHit[] rHits = new AltHit[hits.length];
		if (!filtered) { // optimized
			for (int i = 0; i < hits.length; ++i)
				rHits[i] = new AltHit(hits[i], xFactor, yFactor);
		} else {
			int size = 0;
			for (int i = 0; i < hits.length; ++i) {
				if (!hits[i].isRepetitive() || !filtered)
					rHits[size++] = new AltHit(hits[i], xFactor, yFactor);
			}
			if (size < hits.length) {
				AltHit[] aHits = new AltHit[size];
				System.arraycopy(rHits, 0, aHits, 0, size);
				rHits = aHits;
			}
		}
		return rHits;
	}

	public static AltHit[] getAltHitsCopy(Hit[] hits) {
		if (hits == null || hits.length == 0)
			return new AltHit[0];
		AltHit[] rHits = new AltHit[hits.length];
		for (int i = 0; i < hits.length; ++i)
			rHits[i] = new AltHit(hits[i]);
		return rHits;
	}

	public static AltHit[] getAltHitsCopy(Hit[] hits, double xFactor,
			double yFactor, boolean filtered, int score) {
		if (hits == null || hits.length == 0)
			return new AltHit[0];
		AltHit[] rHits = new AltHit[hits.length];
		if (!filtered) { // optimized
			for (int i = 0; i < hits.length; ++i)
				rHits[i] = new AltHit(hits[i], xFactor, yFactor, score);
		} else {
			int size = 0;
			for (int i = 0; i < hits.length; ++i) {
				if (!hits[i].isRepetitive() || !filtered)
					rHits[size++] = new AltHit(hits[i], xFactor, yFactor, score);
			}
			if (size < hits.length) {
				AltHit[] aHits = new AltHit[size];
				System.arraycopy(rHits, 0, aHits, 0, size);
				rHits = aHits;
			}
		}
		return rHits;
	}

	public static Comparator<AltHit> altComparator() {
		return new Comparator<AltHit>() {
			public int compare(AltHit h1, AltHit h2) {
				if (h1.getAltX() != h2.getAltX())
					return h1.getAltX() - h2.getAltX();
				return h1.getAltY() - h2.getAltY();
			}
		};
	}
}
