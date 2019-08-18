package symap.sequence;

import java.util.Vector;
import java.util.Collections;
import java.util.Comparator;

public class PseudoData {
	protected int project;
	protected int group;
	protected String name;
	protected long size;
	protected AnnotationData[] annotData;

	public PseudoData(int project, int group) {
		this.project = project;
		this.group = group;
		annotData = new AnnotationData[0];
	}

	public long getSize() {
		return size;
	}

	public void setSize(long size) {
		this.size = size;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name.intern();
	}

	public boolean equals(Object obj) {
		if (obj instanceof PseudoData) {
			PseudoData d = (PseudoData)obj;
			return project == d.project && group == d.group;
		}
		return false;
	}

	public int hashCode() {
		return (new Integer(project).toString() + ':' + new Integer(group).toString()).hashCode();
	}

	public void setAnnotationData(AnnotationData[] annotData) {
		if (annotData == null) annotData = new AnnotationData[0];
		this.annotData = annotData;
	}

	public void setAnnotations(Vector<Annotation> annotations) {
		annotations.clear();
		annotations.ensureCapacity(annotData.length);

		Annotation centromere = null; // only one
		Annotation annot;
		for (int i = 0; i < annotData.length; i++) {
			annot = annotData[i].getAnnotation();
			if (annot.isCentromere()) centromere = annot;
			else annotations.add(annot);
		}
		if (centromere != null) annotations.add(centromere);
		
		annotations.trimToSize();
		
		// Sort the annotations by type (ascending order) so that exons are
		// drawn on top of genes.
		// mdb added 5/30/07
		Collections.sort(annotations,
			new Comparator<Annotation>() {
				public int compare(Annotation a1, Annotation a2) { 
					//return -1*a1.getType().compareTo(a2.getType()); // mdb removed 3/31/08 #156
					return (a1.getType() - a2.getType()); 			  // mdb added   3/31/08 #156
				}
			}
		);
	}

	public String toString() {
		return "[SequencePoolData: (Project: "+project+") (Group: "+group+") (Size: "+size+")]";
	}
}
