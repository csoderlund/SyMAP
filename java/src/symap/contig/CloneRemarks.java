package symap.contig;

import java.util.Collection;
import java.util.Arrays;

public class CloneRemarks {

	private int project;
	private CloneRemark[] remarks;

	public CloneRemarks(int project) {
		this.project = project;
	}

	public CloneRemarks(int project, Collection cloneRemarks) {
		this(project,(cloneRemarks == null ? new CloneRemark[0] : (CloneRemark[])cloneRemarks.toArray(new CloneRemark[0])));
	}

	private CloneRemarks(int project, CloneRemark[] remarks) {
		this.project = project;
		this.remarks = remarks;
		if (remarks != null) Arrays.sort(remarks);
	}

	public int getProject() {
		return project;
	}

	public CloneRemark getRemark(int id) {
		int i = Arrays.binarySearch(remarks,new CloneRemark(id,null));
		return i < 0 ? null : remarks[i];
	}

	public void setRemarks(Collection<CloneRemark> remarkList, int[] ids) {
		if (ids == null) return;
		CloneRemark cr = new CloneRemark();
		int ind;
		for (int i = 0; i < ids.length; ++i) {
			cr.id = ids[i];
			ind = Arrays.binarySearch(remarks,cr);
			if (ind >= 0) remarkList.add(remarks[ind]);
			//else System.err.println("Remark "+ids[i]+" not found!");
		}
	}

	public boolean equals(Object obj) {
		return obj instanceof CloneRemarks && ((CloneRemarks)obj).project == project;
	}

	public String toString() {
		return "[CloneRemarks for project "+project+" has "+(remarks == null ? 0 : remarks.length)+" remarks]";
	}

	public static class CloneRemark implements Comparable<CloneRemark> {

		private int id;
		private String remark;

		private CloneRemark() { }

		public CloneRemark(int id, String remark) {
			this.id = id;
			this.remark = remark;//(remark == null) ? null : remark.intern(); // mdb removed intern() 2/2/10 - can cause memory leaks in this case
		}

		public int getID() {
			return id;
		}

		public String getRemark() {
			return remark;
		}

		public boolean equals(Object obj) {
			return obj instanceof CloneRemark && ((CloneRemark)obj).id == id;
		}

		public String toString() {
			return remark;
		}

		public int compareTo(CloneRemark c) {
			return id - c.id;
		}
	}
}
