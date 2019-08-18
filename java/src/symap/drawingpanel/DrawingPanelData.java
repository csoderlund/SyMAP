package symap.drawingpanel;

import symap.mapper.Mapper;
import symap.mapper.MapperData;
import symap.track.TrackData;
import symap.track.TrackHolder;

/**
 * Holds all of the data to draw the map(s) of the drawing panel.
 * 
 * @author Austin Shoemaker
 */
public class DrawingPanelData {
	private MapperData[] mapperData;
	private TrackData[]  trackData;

	/**
	 * Creates a new <code>DrawingPanelData</code> instance using the passed in data.
	 *
	 * @param mapperData a <code>MapperData[]</code> value
	 * @param trackData a <code>TrackData[]</code> value
	 */
	public DrawingPanelData(MapperData[] mapperData, TrackData[] trackData) {
		this.mapperData = new MapperData[mapperData.length];
		this.trackData = new TrackData[mapperData.length+1];
		for (int i = 0; i < mapperData.length; i++) this.mapperData[i] = mapperData[i];
		for (int i = 0; i < trackData.length;  i++) this.trackData[i] = trackData[i];
	}

	protected DrawingPanelData(Mapper[] mappers, TrackHolder[] trackHolders, int numMaps) {
		mapperData = new MapperData[numMaps];
		trackData = new TrackData[numMaps+1];
		for (int i = 0; i < mapperData.length; i++) mapperData[i] = mappers[i].getMapperData();
		for (int i = 0; i < trackData.length; i++) trackData[i] = trackHolders[i].getTrackData();
	}

	protected MapperData[] getMapperData() {
		return mapperData;
	}

	protected TrackData[] getTrackData() {
		return trackData;
	}
}
