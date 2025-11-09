package symap.closeup;

/**
 * The DPalign method perform dynamic programming; called by AlignPair
 * The methods getHorzResult and getVertResult build and scored the aligned sequences
 */
public class AlignPair 
{
	private final char stopCh = AlignData.stopCh;
	private final char gapCh = AlignData.gapCh;
	private float matchScore = 1.8f;
	private float mismatchScore = -1.0f;
	private float gapOpen =   7.0f; // 4.0f;	// 3.0f changed to negative in code
	private float gapExtend = 0.7f; // 0.5f;	// 0.7f
	private boolean bFreeEndGaps = true;  // semi-global; don't penalize gaps at ends
	private boolean bUseAffineGap = true; 
	  
    protected boolean DPalign ( String strHorz, String strVert, boolean dna ) {
		strGapHorz=strGapVert=null;
    	isDNA = dna;
    	
    	if (bUseAffineGap) 	return matchAffine( strHorz, strVert );
    	else 				return matchNonAffine( strHorz, strVert );
    }
    protected String getAlignResult1 ( ) {
		if (strGapHorz==null) buildOutput ( gapCh ); 
		return strGapHorz; 
    }
    protected String getAlignResult2 () {
		if (strGapVert==null) buildOutput ( gapCh ); 
		return strGapVert; 
    }
    
    // Build strGapHorz and strGapVert which have inserted gaps
    private void buildOutput ( char chGap ) {       
        if ( bUseAffineGap )	buildAffineOutput ( chGap );
        else					buildNonAffineOutput ( chGap );   
        
        score(chGap);
    }   
    /**
     * Shared Routines for affine and non-affine dynamic programming
     */
    private float cmp(char x, char y) {
		if (isDNA) { 
			if (Character.toUpperCase(x)==Character.toUpperCase(y)) return matchScore;
			else return mismatchScore;
		}
		return (float) getBlosum(x,y);
    }
 
    /*************************************************************
     * NonAffine 
     */
    private boolean matchNonAffine (String strHorz, String strVert )
    {
        nRows = strVert.length() + 1;
        nCols = strHorz.length() + 1;
        nCells = nCols * nRows;
        strInHorz = strHorz;
        strInVert = strVert;
 
        if (!checkAllocation ( )) return false;

        // Initialize top row
        for ( int i = 1; i < nCols; ++i ){
            if ( bFreeEndGaps ) matchRow[i] = 0;
            else matchRow[i] = - ( gapOpen * i );                    
            matchDir[i] = DIRECTION_LEFT;
        }
        
        // Initalize left column for direction arrays
        for ( int k = 1, i = nCols; k < nRows; ++k, i += nCols ){
            matchDir[i] = DIRECTION_UP;
        }
        matchRow[0] = 0;
        
        // Fill in all matricies simultaneously, row-by-row
        for ( int v = 1; v < nRows; ++v ){
        	float fMatch, fUp, fDiag, fLeft;
            int i = ( v * nCols ) + 1;
            
            // Only saves two rows, the last one and current one.
            float [] temp = matchLastRow;
            matchLastRow = matchRow;
            matchRow = temp;  
            temp = gapHorzLastRow;
            gapHorzLastRow = gapHorzRow;
            gapHorzRow = temp;
            temp = gapVertLastRow;
            gapVertLastRow = gapVertRow;
            gapVertRow = temp;
            
            // Initialize column 0 for the current row
            if ( bFreeEndGaps ) matchRow[0] = 0;
            else matchRow[0] = - ( gapOpen * v );    
            
            for ( int h = 1; h < nCols; ++h, ++i ){
                fMatch = cmp(strVert.charAt(v-1), strHorz.charAt(h-1));

                fUp = matchLastRow[h];
                if (!bFreeEndGaps || h != nCols - 1)  fUp -= gapOpen;
                
                fDiag = matchLastRow[h-1] + fMatch;    
                
                fLeft = matchRow[h-1];
                if (!bFreeEndGaps || v != nRows - 1)  fLeft -= gapOpen;
                
                matchRow[h] = fUp;
                matchDir[i] = DIRECTION_UP;
                
                if ( fDiag > matchRow[h] ){
                    matchRow[h] = fDiag;
                    matchDir[i] = DIRECTION_DIAGONAL; 
                }
                if ( fLeft > matchRow[h] ){
                    matchRow[h] = fLeft;
                    matchDir[i] = DIRECTION_LEFT; 
	            } 
	        }
	    }  
        return true;
    }
    private void buildNonAffineOutput ( char chGap ){
        strGapHorz = "";
        strGapVert = "";
        
        int i = nCells - 1;
        int v = strInVert.length() - 1;
        int h = strInHorz.length() - 1;
      
        while ( i > 0 ){
            switch ( matchDir[i] ){
            case DIRECTION_UP:
                strGapHorz = chGap  + strGapHorz;
                strGapVert = strInVert.charAt(v) + strGapVert;
                --v;
                i -= nCols;
                break;
            case DIRECTION_LEFT:
                strGapHorz = strInHorz.charAt(h) + strGapHorz;
                strGapVert = chGap + strGapVert;
                --h;
                --i;
                break;
            case DIRECTION_DIAGONAL:
                strGapHorz = strInHorz.charAt(h)  + strGapHorz;
                strGapVert = strInVert.charAt(v) + strGapVert;
                --h;
                --v;
                i -= (nCols + 1);
                break;
            default:
                throw new RuntimeException ( "Invalid direction..." );
            }
        }       
    }
    /********************************************************
     * XXX Affine
     */
    private boolean matchAffine (String strHorz, String strVert ){
        nRows = strVert.length() + 1;
        nCols = strHorz.length() + 1;
        nCells = nCols * nRows;
        strInHorz = strHorz;
        strInVert = strVert;
        
        float fTotalGapOpen = - ( gapOpen + gapExtend );
        
        if (!checkAllocation ( )) return false;
        
        // Initialize top row
        for ( int i = 1; i < nCols; ++i ){
            matchRow[i] = -Float.MAX_VALUE;
            matchDir[i] = DIRECTION_DIAGONAL;
            gapHorzRow[i] = -Float.MAX_VALUE;
            gapHorzDir[i] = DIRECTION_UP;
            if ( bFreeEndGaps ) gapVertRow[i] = 0;
            else   gapVertRow[i] =  -( gapOpen + (i-1) * gapExtend );
            gapVertDir[i] = DIRECTION_LEFT;
       }
        
        // Initalize left column for direction arrays
        for ( int k = 1, i = nCols; k < nRows; ++k, i += nCols ){
            matchDir[i] = DIRECTION_DIAGONAL;
            gapHorzDir[i] = DIRECTION_UP;
            gapVertDir[i] = DIRECTION_LEFT;
        }
        matchRow[0] = 0;
 
        // Fill in all matricies simultaneously, row-by-row
        for ( int v = 1; v < nRows; ++v ){
            int i = ( v * nCols ) + 1;
            
            // "Rotate" the score arrays.  The current row is now uninitialized,
            //  but the last row is completely filled in.
            float [] temp = matchLastRow;
            matchLastRow = matchRow;
            matchRow = temp;  
            temp = gapHorzLastRow;
            gapHorzLastRow = gapHorzRow;
            gapHorzRow = temp;
            temp = gapVertLastRow;
            gapVertLastRow = gapVertRow;
            gapVertRow = temp;
            
            // Initialize column 0 for the current row of all "rotating" arrays        
            matchRow[0] = -Float.MAX_VALUE;
            if ( bFreeEndGaps ) gapHorzRow[0] = 0;
            else   gapHorzRow[0] = - ( gapOpen + (v - 1) * gapExtend );     
            gapVertRow[0] = -Float.MAX_VALUE;
            
            for ( int h = 1; h < nCols; ++h, ++i){
                float fMatch = cmp(strVert.charAt(v-1), strHorz.charAt(h-1));
                
                // Match matrix. Compare with the value one cell up and one to the left.
                testBest ( h - 1, false, fMatch, fMatch, fMatch );     
                matchRow [h] = fLastBest;
                matchDir [i] = chLastBest;
                
                // Horizonal gap matrix. Compare with the value one cell up.
                if ( bFreeEndGaps && ( h == nCols - 1 ) )
                    testBest ( h, false, 0, 0, 0 );
                else
                    testBest ( h, false, -gapExtend, fTotalGapOpen, fTotalGapOpen );
                gapHorzRow [h] = fLastBest;
                gapHorzDir [i] = chLastBest;
                
                // Vertical gap matrix.  Compare with the value one cell to the left.
                if ( bFreeEndGaps && ( v == (nRows - 1) ) )
                    testBest ( h-1, true, 0, 0, 0 );
                else
                    testBest ( h-1, true, fTotalGapOpen, fTotalGapOpen, -gapExtend );
                gapVertRow [h] = fLastBest;
                gapVertDir [i] = chLastBest;
             }
        }      
        // Set the starting "pointer" for building the output strings
        testBest ( nCols - 1, true, 0, 0, 0 );
        startDir = chLastBest;
        return true;
    }
    private void buildAffineOutput ( char chGap ){      
        strGapHorz = "";
        strGapVert = "";
        
        int i = nCells - 1;
        int v = strInVert.length() - 1;
        int h = strInHorz.length() - 1;
        
        char chNextHop = startDir;       
        
        while ( i > 0 ){
            switch ( chNextHop ){
            case DIRECTION_UP:
                chNextHop = gapHorzDir [i];
                strGapHorz = chGap  + strGapHorz;
                strGapVert = strInVert.charAt(v) + strGapVert;
                --v;
                i -= nCols;
                break;
            case DIRECTION_LEFT:
                chNextHop = gapVertDir [i];
                strGapHorz = strInHorz.charAt(h) + strGapHorz;
                strGapVert = chGap + strGapVert;
                --h;
                --i;
                break;
            case DIRECTION_DIAGONAL:
                chNextHop = matchDir[i];
                strGapHorz = strInHorz.charAt(h)  + strGapHorz;
                strGapVert = strInVert.charAt(v) + strGapVert;
                --h;
                --v;
                i -= (nCols + 1);
                break;
            default:
                System.err.println( "Error aligning sequences, may have run out of memory..." );
            }  
        }      
    }
   
    /*****************************************************
     * Used by affine method
     */
    private void testBest ( int i, boolean bCurRow, float fDUp, float fDDiag, float fDLeft ){
        // Choose the best choice with the arbitrary tie break of up, diagonal, left       
        // Note: the inversion between the direction in the matrix and the gap is correct
        float fUp, fDiag, fLeft;
     
        if ( bCurRow ){
            fUp = fDUp + gapHorzRow [i];
            fDiag = fDDiag + matchRow [i];
            fLeft = fDLeft + gapVertRow [i];
        }
        else{
            fUp = fDUp + gapHorzLastRow [i];
            fDiag = fDDiag + matchLastRow [i];
            fLeft = fDLeft + gapVertLastRow [i];
        }
        
        fLastBest = fUp;
        chLastBest = DIRECTION_UP;
        if ( fDiag > fLastBest ){
            fLastBest = fDiag;
            chLastBest = DIRECTION_DIAGONAL;
        }
        if ( fLeft > fLastBest ){
            fLastBest = fLeft;
            chLastBest = DIRECTION_LEFT;
        }
    }
   /***********************************************************/
    // For doHomology, the arrays get reused.
    private boolean checkAllocation ( ){
		if (nCells > maxCells) {
	   		System.err.println("Not enough memory to align sequences - need " + nCells + "kb");
	   		isGood=false;
			return false;
		}
	    fLastBest = -Float.MAX_VALUE;
	    chLastBest = DIRECTION_DIAGONAL;
	    
		try {
	        if ( matchDir == null || matchDir.length < nCells ){
	            matchDir = new char [nCells];
	            if ( bUseAffineGap ){
	                gapHorzDir = new char [nCells];
	                gapVertDir = new char [nCells];        
	            }
	        }
	        else {
        		for (int i=0; i< matchDir.length; i++) matchDir[i] = ' ';
        		 if ( bUseAffineGap )
        			 for (int i=0; i< matchDir.length; i++) 
        				 gapHorzDir[i] =  gapVertDir[i] = ' ';
	        }
	        int max = (nRows > nCols ) ? nRows : nCols;
	        
	        if ( matchRow == null || matchRow.length < max ){
	            matchRow = new float [max];
	            matchLastRow = new float [max];  
	            if ( bUseAffineGap ){
	                gapHorzRow = new float [max];
	                gapHorzLastRow = new float [max];
	                gapVertRow = new float [max];
	                gapVertLastRow = new float [max];
	            }
	        }
	        else {
    			for (int i=0; i< matchRow.length; i++) matchRow[i] = 0.0f;
    			if ( bUseAffineGap )
    				for (int i=0; i< matchRow.length; i++) 
    					gapHorzRow[i] =  gapVertRow[i] = 
    						gapHorzLastRow[i] =  gapVertLastRow[i] = 0.0f;
        	}
		}
		catch (OutOfMemoryError E) {
			matchDir = null;
			maxCells = nCells;
			System.err.println("Not enough memory to align sequences");
			System.err.println("Increase in executable script (e.g. execAnno, viewSingleTCW");
			isGood=false;
			return false;
		}
        return true;
    }
    
    protected void clear() {
    	matchLastRow = null;
    	matchRow = null;
    	matchDir = null;
    	gapHorzRow = null;
    	gapHorzLastRow = null;
    	gapHorzDir = null;
    	gapVertRow = null;
    	gapVertLastRow = null;
    	gapVertDir = null;
    	
    	strInHorz = null; 
        strInVert = null;
        strGapHorz = null; 
        strGapVert = null; 
    }
   
    //Score the alignment; sets all values that can later be obtained with gets
    private void score(char chGap) {   
        OLPmatch = OLPlen = OLPstops = OLPscore = OLPgap = 0;
        int i, j;
        int allS = 0, allE = 0; // start and stop of complete overlap
        boolean isOpen=false;
     
        int lenStr = strGapHorz.length(); // lengths of two strings the same

        // find start of overlap
        for (i = 0; i< lenStr; i++) {
          	if (strGapHorz.charAt(i) != chGap && strGapVert.charAt(i) != chGap) {
        		allS = i;
        		break;
        	}
        }       
        // find end of overlap
        for (j = lenStr-1; j> 0; j--) {
          	if (strGapHorz.charAt(j) != chGap && strGapVert.charAt(j) != chGap) {
        		allE = j;
        		break;
        	}
        }
        OLPlen = allE - allS + 1; 
        if (allE>=strGapHorz.length() || allE>=strGapVert.length()) return;
        
        for (i = allS; i <= allE; i++) {	    		
    		if (strGapHorz.charAt(i) == chGap || strGapVert.charAt(i) == chGap) {	 
    			OLPgap++;
        		if (isOpen && bUseAffineGap) OLPscore -= gapExtend;
        		else OLPscore -= gapOpen;
        		isOpen = true;
        		if (strGapHorz.charAt(i) == stopCh || strGapVert.charAt(i) == stopCh) OLPstops++;
        	}
        	else {
        		isOpen = false;
        		if (strGapHorz.charAt(i) == stopCh || strGapVert.charAt(i) == stopCh) {
        			OLPstops++;
        			OLPscore -= 4;
        		}
        		else {
        			double s = cmp(strGapHorz.charAt(i), strGapVert.charAt(i));
        			if (s>0) OLPmatch++;
        			OLPscore +=  s;
        		}
        	}  
        } 
        if (OLPmatch>OLPlen) OLPmatch=OLPlen; // just to make sure
    }
  
    /*******************************************************/ 
    private String strInHorz = null;
    private String strInVert = null;
    private String strGapHorz = null, strGapVert = null; // final results
  
    private static final char DIRECTION_UP = '^';
    private static final char DIRECTION_LEFT = '<';
    private static final char DIRECTION_DIAGONAL = '\\'; 
    
    private int nCells, nRows, nCols = Integer.MIN_VALUE;
    private float [] matchLastRow = null;
    private float [] matchRow = null;
    private char []  matchDir = null;
    // used for affine. 
    private float [] gapHorzRow = null;
    private float [] gapHorzLastRow = null;
    private char []  gapHorzDir = null;
    private float [] gapVertRow = null;
    private float [] gapVertLastRow = null;
    private char []  gapVertDir = null;
    
    private char startDir;
    private float fLastBest = -Float.MAX_VALUE;
    private char chLastBest = DIRECTION_DIAGONAL;
    
    private long maxCells = Long.MAX_VALUE;
    
    // returning values for score to compare alignments from different frames
    // maybe should have just gotten a dp score.
    private int OLPmatch = 0;
    private int OLPlen = 0;
    private int OLPstops = 0, OLPscore = 0, OLPgap = 0;
    private boolean isDNA = true;
    private boolean isGood=true;
    
    protected void prtInfo() {System.out.format("Stops %d Score %d Gap %d Good %b", OLPstops, OLPscore, OLPgap, isGood);}
    
    // BLOSUM62 7/17/19 from ftp://ftp.ncbi.nlm.nih.gov/blast/matrices/BLOSUM62
    // Called in cmp above. 
    // Also called by util.align.AlignData
    // by moving the array to this file sped it up about 10-fold!!
    static protected int getBlosum(char a1, char a2) {
     	String residues = "ARNDCQEGHILKMFPSTWYVBZX*";
		int idx1 = residues.indexOf(a1);
		int idx2 = residues.indexOf(a2);
		if (idx1==-1 || idx2==-1) {
			if (idx1==-1) System.err.println("AA not recognized a1 '" + a1 + "' ");
			if (idx2==-1) System.err.println("AA not recognized a2 '" + a2 + "' ");
			return -10;
		}
		return (blosum[idx1][idx2]); 
	}
    private static final int blosum[][] = new int [][] {
    	{ 4, -1, -2, -2,  0, -1, -1,  0, -2, -1, -1, -1, -1, -2, -1,  1,  0, -3, -2,  0, -2, -1,  0, -4},
    	{-1,  5,  0, -2, -3,  1,  0, -2,  0, -3, -2,  2, -1, -3, -2, -1, -1, -3, -2, -3, -1,  0, -1, -4},
    	{-2,  0,  6,  1, -3,  0,  0,  0,  1, -3, -3,  0, -2, -3, -2,  1,  0, -4, -2, -3,  3,  0, -1, -4},
    	{-2, -2,  1,  6, -3,  0,  2, -1, -1, -3, -4, -1, -3, -3, -1,  0, -1, -4, -3, -3,  4,  1, -1, -4},
    	{ 0, -3, -3, -3,  9, -3, -4, -3, -3, -1, -1, -3, -1, -2, -3, -1, -1, -2, -2, -1, -3, -3, -2, -4},
    	{-1,  1,  0,  0, -3,  5,  2, -2,  0, -3, -2,  1,  0, -3, -1,  0, -1, -2, -1, -2,  0,  3, -1, -4},
    	{-1,  0,  0,  2, -4,  2,  5, -2,  0, -3, -3,  1, -2, -3, -1,  0, -1, -3, -2, -2,  1,  4, -1, -4},
    	{ 0, -2,  0, -1, -3, -2, -2,  6, -2, -4, -4, -2, -3, -3, -2,  0, -2, -2, -3, -3, -1, -2, -1, -4},
    	{-2,  0,  1, -1, -3,  0,  0, -2,  8, -3, -3, -1, -2, -1, -2, -1, -2, -2,  2, -3,  0,  0, -1, -4},
    	{-1, -3, -3, -3, -1, -3, -3, -4, -3,  4,  2, -3,  1,  0, -3, -2, -1, -3, -1,  3, -3, -3, -1, -4},
    	{-1, -2, -3, -4, -1, -2, -3, -4, -3,  2,  4, -2,  2,  0, -3, -2, -1, -2, -1,  1, -4, -3, -1, -4},
    	{-1,  2,  0, -1, -3,  1,  1, -2, -1, -3, -2,  5, -1, -3, -1,  0, -1, -3, -2, -2,  0,  1, -1, -4},
    	{-1, -1, -2, -3, -1,  0, -2, -3, -2,  1,  2, -1,  5,  0, -2, -1, -1, -1, -1,  1, -3, -1, -1, -4},
    	{-2, -3, -3, -3, -2, -3, -3, -3, -1,  0,  0, -3,  0,  6, -4, -2, -2,  1,  3, -1, -3, -3, -1, -4},
    	{-1, -2, -2, -1, -3, -1, -1, -2, -2, -3, -3, -1, -2, -4,  7, -1, -1, -4, -3, -2, -2, -1, -2, -4},
    	{ 1, -1,  1,  0, -1,  0,  0,  0, -1, -2, -2,  0, -1, -2, -1,  4,  1, -3, -2, -2,  0,  0,  0, -4},
    	{ 0, -1,  0, -1, -1, -1, -1, -2, -2, -1, -1, -1, -1, -2, -1,  1,  5, -2, -2,  0, -1, -1,  0, -4},
    	{-3, -3, -4, -4, -2, -2, -3, -2, -2, -3, -2, -3, -1,  1, -4, -3, -2, 11,  2, -3, -4, -3, -2, -4},
    	{-2, -2, -2, -3, -2, -1, -2, -3,  2, -1, -1, -2, -1,  3, -3, -2, -2,  2,  7, -1, -3, -2, -1, -4},
    	{ 0, -3, -3, -3, -1, -2, -2, -3, -3,  3,  1, -2,  1, -1, -2, -2,  0, -3, -1,  4, -3, -2, -1, -4},
    	{-2, -1,  3,  4, -3,  0,  1, -1,  0, -3, -4,  0, -3, -3, -2,  0, -1, -4, -3, -3,  4,  1, -1, -4},
    	{-1,  0,  0,  1, -3,  3,  4, -2,  0, -3, -3,  1, -1, -3, -1,  0, -1, -3, -2, -2,  1,  4, -1, -4},
    	{ 0, -1, -1, -1, -2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -2,  0,  0, -2, -1, -1, -1, -1, -1, -4},
    	{-4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4,  1}
    	};

}