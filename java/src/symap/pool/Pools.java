package symap.pool;

/*********************************************************
 * CAS522 remove all FPC and caching. Caching was turned off in properites.
 */
import java.sql.SQLException;

import symap.mapper.MapperPool;
import symap.sequence.PseudoPool;
import symap.closeup.AlignPool;
import util.DatabaseReader;

public class Pools {
	//private static boolean TRACE = symap.projectmanager.common.ProjectManagerFrameCommon.TEST_TRACE;

	private ProjectProperties pp;
	private PseudoPool pseudoPool;
	private MapperPool mapperPool;
	private AlignPool alignPool;

	public Pools(DatabaseReader dr) throws SQLException {
		pp = new ProjectProperties(dr);
		
		pseudoPool   = new PseudoPool(dr);
		mapperPool   = new MapperPool(dr,pp); // CAS531 removed null for dead cache
		alignPool    = new AlignPool(dr,pp);
	}

	public void clearPools() {
		pseudoPool.clear();
		mapperPool.clear();
		alignPool.clear();
		try { pp.reset(); } 
		catch (Exception e) { }
	}

	public void closePools() {
		pp.close();
		pseudoPool.close();
		mapperPool.close();
		alignPool.close();
	}

	public PseudoPool getPseudoPool() {
		return pseudoPool;
	}

	public MapperPool getMapperPool() {
		return mapperPool;
	}

	public AlignPool getAlignPool() {
		return alignPool;
	}

	public ProjectProperties getProjectProperties() {
		return pp;
	}
}
