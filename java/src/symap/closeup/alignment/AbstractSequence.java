package symap.closeup.alignment;

import java.awt.Graphics;
import java.awt.FontMetrics;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.Arrays;

public /*abstract*/ class AbstractSequence { 
	public static final byte DASH = '-';

	public static final char PLUS       = '+';
	public static final char MINUS      = '-';
	public static final char STRAND_SEP = '/';

	public static final int SEQ_PER_ROW = 60;

	private byte[] seq;
	private int realLength;
	private char strand;

	public AbstractSequence(String seq, char strand) {
		this.seq = (seq == null ? new byte[0] : seq.toUpperCase().getBytes());
		this.strand = strand;
		realLength = getRL(this.seq,0,this.seq.length);
	}

	public boolean equals(Object obj) {
		return obj instanceof AbstractSequence && Arrays.equals(seq,((AbstractSequence)obj).seq);
	}

	public String toString() {
		return new String(seq);
	}
	
	public char charAt(int i) {
		return (char)seq[i];
	}

	public char getStrand() {
		return strand;
	}

	public boolean isPlus() {
		return strand == PLUS;
	}

	public int length() {
		return seq.length;
	}

	public void draw(Graphics g, int x, int y) {
		g.drawBytes(seq,0,seq.length,x,y);
	}

	public int getWidth(FontMetrics fm) {
		return fm.bytesWidth(seq,0,seq.length);
	}

	public static void drawLines(AbstractSequence query, AbstractSequence target, Graphics g, int x, int y) { 
		char lines[] = getLines(query,target);
		g.drawChars(lines,0,lines.length,x,y);
	}

	public static String toString(AbstractSequence q, int queryStart, AbstractSequence t, int targetStart) { 
		AbstractSequence query = (AbstractSequence)q;
		AbstractSequence target = (AbstractSequence)t;
		String x = String.format("%d", (targetStart+target.seq.length));
		int padSpace = x.length()+1; // CAS520  new Integer(targetStart+target.seq.length).toString()
		StringBuffer buf = new StringBuffer();
		for (int i = 0; i < padSpace; i++) buf.append(" ");

		final String PAD_SPACE = buf.toString();
		final String QUERY = "Query: ";
		final String SBJCT = "Sbjct: ";
		final String LPSPC = "       "+PAD_SPACE;
		final char   NL    = '\n';

		final int qMult = query.strand  == PLUS ? 1 : -1;
		final int tMult = target.strand == PLUS ? 1 : -1;

		if (qMult < 0)
			queryStart += query.seq.length-1;

		if (tMult < 0)
			targetStart += target.seq.length-1;

		buf = new StringBuffer();
		int len;
		int qStart=0, tStart=0, qEnd=queryStart-qMult, tEnd=targetStart-tMult;
		for (int i = 0; i < query.seq.length; i += SEQ_PER_ROW) {
			len = i+SEQ_PER_ROW < query.seq.length ? SEQ_PER_ROW : query.seq.length-i;

			qStart = qEnd + qMult;
			tStart = tEnd + tMult;

			qEnd = getRL( query.seq,i,len+i)*qMult + qEnd;
			tEnd = getRL(target.seq,i,len+i)*tMult + tEnd;
			// CAS520 new Integer(qStart).toString().length()
			x = String.format("%d", qStart);
			buf.append(QUERY).append(qStart).append(PAD_SPACE.substring(0,x.toString().length()));
			buf.append(new String(query.seq,i,len)).append(" ").append(qEnd).append(NL);
			buf.append(LPSPC).append(getLines(q,t,i,i+len)).append(NL);
			x = String.format("%d", qStart);
			buf.append(SBJCT).append(tStart).append(PAD_SPACE.substring(0,padSpace-x.toString().length()));
			buf.append(new String(target.seq,i,len)).append(" ").append(tEnd).append(NL).append(NL);
		}

		char[] dashes = new char[LPSPC.length()+SEQ_PER_ROW+padSpace];
		java.util.Arrays.fill(dashes,'-');
		buf.append(dashes).append(NL);

		return buf.toString();
	}

	private static int getRL(byte[] seq, int i, int len) {
		int r;
		for (r = 0; i < len; ++i)
			if (seq[i] != DASH) ++r;
		return r;
	}

	public static char[] getLines(/*QuerySequence*/AbstractSequence s1, /*TargetSequence*/AbstractSequence s2) { 
		return getLines(s1,s2,0,((AbstractSequence)s1).seq.length);
	}

	public static char[] getLines(/*QuerySequence*/AbstractSequence s1, /*TargetSequence*/AbstractSequence s2, int startIndex, int endIndex) { 
		char ret[] = new char[endIndex-startIndex];
		final char SPACE = ' ';
		final char LINE  = '|';
		for (int i = startIndex; i < endIndex; i++) {
			if ((/*(AbstractSequence)*/s1).seq[i] == (/*(AbstractSequence)*/s2).seq[i]) ret[i-startIndex] = LINE;
			else                        ret[i-startIndex] = SPACE;
		}
		return ret;
	}

	public static double[] getQueryMisses(/*QuerySequence*/AbstractSequence query, /*TargetSequence*/AbstractSequence target) { 
		AbstractSequence qs = (AbstractSequence)query;
		AbstractSequence ts = (AbstractSequence)target;

		if (qs.seq.length != ts.seq.length)
			throw new IllegalArgumentException("The sequence lengths are not equal in getQueryMisses()! "+qs.seq.length+" "+ts.seq.length);

		List<Double> misses = new LinkedList<Double>();
		double size = (double)ts.realLength;
		int pos = 0, prev = -1, i;

		for (i = 0; i < qs.seq.length; ++i) {
			if (qs.seq[i] != ts.seq[i] && qs.seq[i] != DASH && ts.seq[i] != DASH) {
				if (pos != prev) {
					misses.add((double)pos/(double)size); // CAS520 new Double
					prev = pos;
				}
			}
			if (ts.seq[i] != DASH) ++pos;
		}
		double[] ret = new double[misses.size()];
		Iterator<Double> iter = misses.iterator();
		for (i = 0; i < ret.length; ++i)
			ret[i] = ((Double)iter.next()).doubleValue();
		return ret;
	}


	public static double[] getQueryInserts(AbstractSequence query, AbstractSequence target) { 
		AbstractSequence qs = (AbstractSequence)query;
		AbstractSequence ts = (AbstractSequence)target;

		if (qs.seq.length != ts.seq.length)
			throw new IllegalArgumentException("The sequence lengths are not equal in getQueryMisses()!");

		List<Double> misses = new LinkedList<Double>();
		double size = (double)ts.realLength;
		int pos = 0, prev = -1, i;

		for (i = 0; i < qs.seq.length; ++i) {
			if (ts.seq[i] == DASH) {
				if (pos != prev) {
					misses.add((double)pos/(double)size); //CAS520 double
					prev = pos;
				}
			}
			else ++pos;
		}
		double[] ret = new double[misses.size()];
		Iterator<Double> iter = misses.iterator();
		for (i = 0; i < ret.length; ++i)
			ret[i] = ((Double)iter.next()).doubleValue();
		return ret;
	}

	public static double[] getQueryDeletes(AbstractSequence query, AbstractSequence target) { 
		AbstractSequence qs = (AbstractSequence)query;
		AbstractSequence ts = (AbstractSequence)target;

		if (qs.seq.length != ts.seq.length)
			throw new IllegalArgumentException("The sequence lengths are not equal in getQueryMisses()!");

		List<Double> misses = new LinkedList<Double>();
		double size = (double)ts.realLength;
		int pos = 0, prev = -1, i;

		for (i = 0; i < qs.seq.length; ++i) {
			if (qs.seq[i] == DASH) {
				if (pos != prev) {
					misses.add((double)pos/(double)size); //CAS520 double
					prev = pos;
				}
			}
			if (ts.seq[i] != DASH) ++pos;
		}
		double[] ret = new double[misses.size()];
		Iterator<Double> iter = misses.iterator();
		for (i = 0; i < ret.length; ++i)
			ret[i] = ((Double)iter.next()).doubleValue();
		return ret;
	}
}
