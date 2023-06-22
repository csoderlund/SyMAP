package symap.mapper;

/**
 * Class <code>MapperData</code> holds the data of a mapper which can be
 * used to recreate the same state of a Mapper.
 */
public class MapperData {

    private HfilterData hf;

    public MapperData(HfilterData hf) {
    	this.hf = hf.copy();
    }
    
    protected MapperData(Mapper mapper) {
    	this(mapper.getHitFilter());
    }

    protected void setMapper(Mapper mapper) {
    	mapper.getHitFilter().set(hf);
    }
}
