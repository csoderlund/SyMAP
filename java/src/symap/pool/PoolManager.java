package symap.pool;

import java.util.HashMap;
import java.sql.SQLException;
import util.DatabaseReader;

public class PoolManager {

    private static PoolManager pm = null;

    private HashMap<String,Pools> pools;

    private PoolManager() { 
    	pools = new HashMap<String,Pools>();
    }
    
    public Pools getPools(DatabaseReader dr) throws SQLException {
		Pools p = pools.get(dr.getURL());
		if (p == null) {
		    p = new Pools(dr);
		    pools.put(dr.getURL(),p);
		}
		return p;
    }

    public static PoolManager getInstance() {
		if (pm == null) pm = new PoolManager();
		return pm;
    }
}
