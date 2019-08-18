package symap.contig;

import java.util.Vector;

import symap.track.Track;
import number.GenomicsNumber;

public class CloneData {
	private String name;
	private long[] cb;
	private byte[] bes;
	private int[]  remarks;

	public CloneData(String name, long cb1, long cb2, byte bes1, byte bes2,
			byte bes1Filter, byte bes2Filter, String remarks) {
		this(name, cb1, cb2, bes1, bes2, bes1Filter, bes2Filter, Clone
				.getRemarks(remarks));
	}

	public CloneData(String name, long cb1, long cb2, byte bes1, byte bes2,
			byte bes1Filter, byte bes2Filter, int[] remarks) {
		this.name = name;//.intern(); // mdb removed intern() 2/2/10 - can cause memory leaks in this case
		this.cb = new long[] { cb1, cb2 };
		this.bes = new byte[] { (byte) (bes1 | bes1Filter),
				(byte) (bes2 | bes2Filter) };
		this.remarks = remarks;
	}

	public Clone getClone(Track track, Vector<Object> condFilters) {
		return new Clone(track, condFilters, getName(), new GenomicsNumber(
				track, getCB1()), new GenomicsNumber(track, getCB2()),
				getBES1(), getBES2(), getBES1Filter(), getBES2Filter(),
				getRemarks());
	}

	public boolean equals(Object obj) {
		return obj instanceof CloneData && name.equals(((CloneData)obj).name);
	}

	public String getName() {
		return name;
	}

	public long getCB1() {
		return cb[0];
	}

	public long getCB2() {
		return cb[1];
	}

	public byte getBES1() {
		return (byte)(bes[0] & 0x0f);
	}

	public byte getBES2() {
		return (byte)(bes[1] & 0x0f);
	}

	public byte getBES1Filter() {
		return (byte)(bes[0] & 0xf0);
	}

	public byte getBES2Filter() {
		return (byte)(bes[1] & 0xf0);
	}

	public int[] getRemarks() {
		return remarks;
	}
}
