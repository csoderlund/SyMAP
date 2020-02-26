package symap.pool;

import java.sql.SQLException;

import symap.SyMAP;
import symap.block.ContigPool;
import symap.contig.ContigClonePool;
import symap.mapper.MapperPool;
import symap.sequence.PseudoPool;
import symap.closeup.SequencePool;
import util.DatabaseReader;
import util.PropertiesReader;
import softref.SoftListCache;

public class Pools {
	protected static final boolean DO_CACHING; 
	protected static final int MAX_CONTIG_POOL, MIN_CONTIG_POOL;
	protected static final int MAX_CONTIG_CLONE_POOL, MIN_CONTIG_CLONE_POOL;
	protected static final int MAX_PSEUDO_POOL, MIN_PSEUDO_POOL;
	protected static final int MAX_FPCPSEUDO_POOL, MIN_FPCPSEUDO_POOL;
	protected static final int MAX_SHAREDMARKERFILTER_POOL, MIN_SHAREDMARKERFILTER_POOL;
	protected static final int MAX_FPCFPC_POOL, MIN_FPCFPC_POOL;
	protected static final int MAX_PSEUDOPSEUDO_POOL, MIN_PSEUDOPSEUDO_POOL; 
	protected static final int MAX_BES_SEQUENCE_POOL, MIN_BES_SEQUENCE_POOL;
	protected static final int MAX_MRK_SEQUENCE_POOL, MIN_MRK_SEQUENCE_POOL;
	protected static final int MAX_GENE_POOL, MIN_GENE_POOL;

	protected static final int MAX_MRK_MESSAGE_POOL, MIN_MRK_MESSAGE_POOL;
	protected static final int MAX_BES_MESSAGE_POOL, MIN_BES_MESSAGE_POOL;

	protected static final int MAX_CLONE_REMARKS_POOL, MIN_CLONE_REMARKS_POOL;

	static {
		PropertiesReader props = new PropertiesReader(SyMAP.class.getResource("/properties/pool.properties"));
		DO_CACHING					= props.getBoolean("do_caching"); 
		MAX_CONTIG_POOL             = props.getInt("maxContigPoolSize");
		MIN_CONTIG_POOL             = props.getInt("minContigPoolSize");
		MAX_CONTIG_CLONE_POOL       = props.getInt("maxContigClonePoolSize");
		MIN_CONTIG_CLONE_POOL       = props.getInt("minContigClonePoolSize");
		MAX_PSEUDO_POOL             = props.getInt("maxPseudoPoolSize");
		MIN_PSEUDO_POOL             = props.getInt("minPseudoPoolSize");
		MAX_FPCPSEUDO_POOL          = props.getInt("maxFPCPseudoPoolSize");
		MIN_FPCPSEUDO_POOL          = props.getInt("minFPCPseudoPoolSize");
		MAX_SHAREDMARKERFILTER_POOL = props.getInt("maxSharedMarkerFilterPoolSize");
		MIN_SHAREDMARKERFILTER_POOL = props.getInt("minSharedMarkerFilterPoolSize");
		MAX_FPCFPC_POOL             = props.getInt("maxFPCFPCPoolSize");
		MIN_FPCFPC_POOL             = props.getInt("minFPCFPCPoolSize");
		MAX_PSEUDOPSEUDO_POOL       = props.getInt("maxPseudoPseudoPoolSize"); 
		MIN_PSEUDOPSEUDO_POOL       = props.getInt("minPseudoPseudoPoolSize"); 

		MAX_MRK_MESSAGE_POOL        = props.getInt("maxMrkMessagePoolSize");
		MIN_MRK_MESSAGE_POOL        = props.getInt("minMrkMessagePoolSize");
		MAX_BES_MESSAGE_POOL        = props.getInt("maxBesMessagePoolSize");
		MIN_BES_MESSAGE_POOL        = props.getInt("minBesMessagePoolSize");

		MAX_BES_SEQUENCE_POOL       = props.getInt("maxBesSequencePoolSize");
		MIN_BES_SEQUENCE_POOL       = props.getInt("minBesSequencePoolSize");
		MAX_MRK_SEQUENCE_POOL       = props.getInt("maxMrkSequencePoolSize");
		MIN_MRK_SEQUENCE_POOL       = props.getInt("minMrkSequencePoolSize");
		MAX_GENE_POOL               = props.getInt("maxGenePoolSize");
		MIN_GENE_POOL               = props.getInt("minGenePoolSize");

		MAX_CLONE_REMARKS_POOL       = props.getInt("maxCloneRemarksPoolSize");
		MIN_CLONE_REMARKS_POOL       = props.getInt("minCloneRemarksPoolSize");
	}

	private ProjectProperties pp;
	private ContigPool contigPool;
	private ContigClonePool contigClonePool;
	private PseudoPool pseudoPool;
	private MapperPool mapperPool;
	private SequencePool sequencePool;
	private HoverMessagePool hoverMessagePool;

	public Pools(DatabaseReader dr) throws SQLException {
		pp = new ProjectProperties(dr);
		
		if (DO_CACHING) {
			BlockContigCache bcc = new BlockContigCache(MIN_CONTIG_POOL,MAX_CONTIG_POOL,MIN_CONTIG_CLONE_POOL,MAX_CONTIG_CLONE_POOL);
			contigPool           = new ContigPool(dr,pp,bcc);
			contigClonePool      = new ContigClonePool(dr,bcc,new SoftListCache(MIN_CLONE_REMARKS_POOL,MAX_CLONE_REMARKS_POOL));
			pseudoPool           = new PseudoPool(dr,new SoftListCache(MIN_PSEUDO_POOL,MAX_PSEUDO_POOL));
			mapperPool           = new MapperPool(dr,pp,
					new SoftListCache(MIN_FPCPSEUDO_POOL,MAX_FPCPSEUDO_POOL),
					new SoftListCache(MIN_SHAREDMARKERFILTER_POOL,MAX_SHAREDMARKERFILTER_POOL),
					new SoftListCache(MIN_FPCFPC_POOL,MAX_FPCFPC_POOL),
					new SoftListCache(MIN_PSEUDOPSEUDO_POOL,MAX_PSEUDOPSEUDO_POOL)); 
			sequencePool        = new SequencePool(dr,pp);
			hoverMessagePool    = new HoverMessagePool(dr,pp,
					new SoftListCache(MIN_MRK_MESSAGE_POOL,MAX_MRK_MESSAGE_POOL),
					new SoftListCache(MIN_BES_MESSAGE_POOL,MAX_BES_MESSAGE_POOL));
		}
		else { 
			contigPool           = new ContigPool(dr,pp,null);
			contigClonePool      = new ContigClonePool(dr,null,null);
			pseudoPool           = new PseudoPool(dr,null);
			mapperPool           = new MapperPool(dr,pp,
										null,//new SoftListCache(0,0),
										null,//new SoftListCache(0,0),
										null,//new SoftListCache(0,0),
										null);//new SoftListCache(0,0));
			sequencePool        = new SequencePool(dr,pp);
			hoverMessagePool    = new HoverMessagePool(dr,pp,
										null,//new SoftListCache(0,0),
										null);//new SoftListCache(0,0));
		}
	}

	public void clearPools() {
		contigPool.clear();
		contigClonePool.clear();
		pseudoPool.clear();
		mapperPool.clear();
		sequencePool.clear();
		hoverMessagePool.clear();
		try { pp.reset(); } 
		catch (Exception e) { }
	}

	public void closePools() {
		pp.close();
		contigPool.close();
		contigClonePool.close();
		pseudoPool.close();
		mapperPool.close();
		sequencePool.close();
		hoverMessagePool.close();
	}

	public ContigPool getContigPool() {
		return contigPool;
	}

	public ContigClonePool getContigClonePool() {
		return contigClonePool;
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

	public HoverMessagePool getHoverMessagePool() {
		return hoverMessagePool;
	}
}
