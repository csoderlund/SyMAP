package symap.sequence;

/**
 * Class <code>AnnotationData</code> holds that data needed to recreate an Annotation
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see Annotation
 */
public class AnnotationData {	
	private String name;
	private String type;
	private /*long*/int start, end; // mdb changed 1/8/09 - changed type to int to match database
	//private String text; // mdb removed 1/8/09
	private String strand; // mdb added 1/8/09 for pseudo-pseudo closeup

	/**
	 * Creates a new <code>AnnotationData</code> instance.
	 *
	 * @param name a <code>String</code> value
	 * @param type a <code>String</code> value
	 * @param start a <code>long</code> value
	 * @param end a <code>long</code> value
	 * @param text a <code>String</code> value
	 */
	public AnnotationData(String name, String type, /*long*/int start, /*long*/int end, String strand) 
	{
		this.name = name;
		this.type = type.intern();
		this.start = start;
		this.end = end;
		//this.text = text; 			// mdb removed 1/8/09
		this.strand = strand.intern(); 	// mdb added 1/8/09
	}

	/**
	 * Method <code>getAnnotation</code> returns an annotation object using the values
	 * in this AnnotationData object.
	 *
	 * @return an <code>Annotation</code> value
	 */
	public Annotation getAnnotation() {
		return new Annotation(name,type,start,end,strand);
	}
}
