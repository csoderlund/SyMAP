package symap.mapper;

/**
 * Class MapperData holds the data of a mapper which can be
 * used to recreate the same state of a Mapper - may be used for History.
 */
public class MapperData {

    private HfilterData hf;

    protected MapperData(Mapper mapper) {
    	this.hf = mapper.getHitFilter().copy("MapperData");
    }
   
    protected void setMapper(Mapper mapper) {
    	mapper.getHitFilter().setChanged(hf, "setMapper");
    }
}
