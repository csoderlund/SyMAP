package symap.sequence;

/**
 * Class <code>AnnotationData</code> holds that data needed to recreate an Annotation
 * @see Annotation
 */
public class AnnotationData {	
	private String name;
	private String type;
	private /*long*/int start, end; 
	private String strand; 

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
		this.strand = strand.intern(); 	
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
