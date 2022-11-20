package symap.sequence;

/**
 * Holds that data needed to recreate an Annotation Object
 * @see Annotation
 */
public class AnnotationData {	
	protected String name;
	protected String type;
	protected int start, end; 
	
	// CAS512 new variables
	protected String strand, tag; 
	protected int annot_idx, genenum, gene_idx, numhits;

	// Called by PseudoPool
	// "SELECT idx, type,name,start,end,strand, genenum, gene_idx, tag 
	public AnnotationData(int annot_idx,String type, String name,  int start, int end, String strand, 
			int genenum, int gene_idx, String tag, int numhits) {
		this.annot_idx = annot_idx;
		this.type = type.intern();
		this.name = name;
		
		this.start = start;
		this.end = end;
		this.strand = strand.intern(); 
		this.gene_idx = gene_idx;
		this.genenum = genenum;
		this.tag = (tag==null || tag.contentEquals("")) ? type : tag; 	
		this.numhits = numhits;
	}
	
	// Called by PseudoData - convert to annotation object
	public Annotation getAnnotation() {
		return new Annotation(name,type,start,end,strand, tag, gene_idx, annot_idx, genenum, numhits);
	}
}	
