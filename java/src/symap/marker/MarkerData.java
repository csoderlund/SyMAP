package symap.marker;

import java.util.Vector;
import number.GenomicsNumber;

public class MarkerData {
    private String name;
    private String type;
    private long   pos;
    
    public MarkerData(String name, String type, long pos) {
	this.name = name;//.intern(); // mdb removed intern() 2/2/10 - can cause memory leaks in this case
	this.type = type.intern();
	this.pos = pos;	
    }

    public String getName() {
    	return name;
    }
    
    public String getType() {
    	return type;
    }
    
    public long getPosition() {
    	return pos;
    }
    
    public Marker getMarker(MarkerTrack track, Vector<Object> condFilters) {
    	return new Marker(track,condFilters,name,type,new GenomicsNumber(track,pos));
    }
}
