package symap.manager;

/*********************************************************
 * CAS522 remove all FPC and caching. Caching was turned off in properites.
 * CAS534 removed PoolManager.java file; have SyMAP2d and SyMAPQuery call this directly
 * 		  moved from symap.pools which is now split between manager and database
 */
import java.sql.SQLException;

import database.DBconn;
import props.ProjectPool;
import symap.mapper.MapperPool;
import symap.sequence.PseudoPool;
import symap.closeup.AlignPool;

public class Pools {
	//private static boolean TRACE = Globals.TRACE;

	private ProjectPool projProp = null;
	private PseudoPool pseudoPool;
	private MapperPool mapperPool;
	private AlignPool alignPool;

	public Pools(DBconn dr) throws SQLException {
		projProp 	 = new ProjectPool(dr);
		pseudoPool   = new PseudoPool(dr);
		mapperPool   = new MapperPool(dr,projProp); // CAS531 removed null for dead cache
		alignPool    = new AlignPool(dr,projProp);
	}
	public void clearPools() {
		pseudoPool.clear();
		mapperPool.clear();
		alignPool.clear();
		try { projProp.reset(); } 
		catch (Exception e) { }
	}

	public void closePools() {
		projProp.close();
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

	public ProjectPool getProjectPropPool() {
		return projProp;
	}
	
}
