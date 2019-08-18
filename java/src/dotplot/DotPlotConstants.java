package dotplot;

public interface DotPlotConstants {
    public static final boolean METHOD_TRACE = false;

    public static final int NUM_HIT_TYPES = 3;

    public static final int MRK = 0;
    public static final int BES = 1;
    public static final int FP  = 2;

    public static final int MIN = 0;
    public static final int MAX = 1;

    public static final int X   = 0;
    public static final int Y   = 1;

    public static final double ANY_EVALUE = 1, NO_EVALUE = 0;
    public static final double ANY_PCTID  = 0, NO_PCTID  = 100;

    public static int BLOCK_HITS = 0;
    public static int NON_REPETITIVE_HITS = 1;
    public static int ALL_HITS = 2;
    public static int CONTAINED_GENE_HITS = 3; // mdb added 3/7/07
    public static int OVERLAP_GENE_HITS = 4; // mdb added 3/7/07
    public static int NON_GENE_HITS = 5; // mdb added 3/7/07
}
