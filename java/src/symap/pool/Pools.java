package symap.pool;

/*********************************************************
 * CAS522 remove all FPC and caching. Caching was turned off in properites.
 */
import java.sql.SQLException;

import symap.mapper.MapperPool;
import symap.sequence.PseudoPool;
import symap.closeup.SequencePool;
import util.DatabaseReader;

public class Pools {
	//private static boolean TRACE = symap.projectmanager.common.ProjectManagerFrameCommon.TEST_TRACE;

	private ProjectProperties pp;
	private PseudoPool pseudoPool;
	private MapperPool mapperPool;
	private SequencePool sequencePool;

	public Pools(DatabaseReader dr) throws SQLException {
		pp = new ProjectProperties(dr);
		
		pseudoPool           = new PseudoPool(dr,null);
		mapperPool           = new MapperPool(dr,pp,null,null,null,null); // nulls are for dead cach
		sequencePool        = new SequencePool(dr,pp);
	}

	public void clearPools() {
		pseudoPool.clear();
		mapperPool.clear();
		sequencePool.clear();
		try { pp.reset(); } 
		catch (Exception e) { }
	}

	public void closePools() {
		pp.close();
		pseudoPool.close();
		mapperPool.close();
		sequencePool.close();
	}

	public PseudoPool getPseudoPool() {
		return pseudoPool;
	}

	public MapperPool getMapperPool() {
		return mapperPool;
	}

	public SequencePool getSequencePool() {
		return sequencePool;
	}

	public ProjectProperties getProjectProperties() {
		return pp;
	}
}
