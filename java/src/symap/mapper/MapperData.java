package symap.mapper;

/**
 * Class <code>MapperData</code> holds the data of a mapper which can be
 * used to recreate the same state of a Mapper.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 */
public class MapperData {

    private HitFilter hf;

    public MapperData(HitFilter hf) {
    	this.hf = hf.copy();
    }
    
    protected MapperData(Mapper mapper) {
    	this(mapper.getHitFilter());
    }

    protected void setMapper(Mapper mapper) {
    	mapper.getHitFilter().set(hf);
    }
}
