package util;

import util.Converters;

/**
 * The DPalign method perform dynamic programming
 * The methods getHorzResult and getVertResult build and scored the aligned sequences
 */
public class PairwiseAligner 
{
	 public static boolean cmpScore( int len1, double sim1, int stop1,
				  					int len2, double sim2, int stop2) 
	{
		len1-=stop1;
		len2-=stop2;
		if (len1 >= len2 && sim1 >= sim2) return true;
		if (len2 >= len1 && sim2 >= sim1) return false;
		if (len1 > 20 && len2 < 20) return true;
		if (len2 > 20 && len1 < 20) return false;
		
		// discourage small hits
		int len = len1; 		
		double sim = sim1*100;
		for (int i=len; i<100 && len > 0;) {len-=2; sim-=1.0; i+=10;}
		if (len<=0) len=1; 	if (sim<=0) sim=0.0001;
		
		double s1 = len * (1/Math.pow(2, 100-sim));

		len = len2; 			
		sim = sim2*100;
		for (int i=len; i<100 && len > 0;) {len-=2; sim-=1.0; i+=10;}
		if (len<=0) len=1; 	if (sim<=0) sim=0.0001;
		
		double s2 = len * (1/Math.pow(2, 100-sim));
		if (s1 > s2) return true;
		else return false;
	}
	 
    public String getHorzResult ( char chGap )
    {
       buildOutput ( chGap ); 
       return strGapHorz; 
    }

    public String getVertResult ( char chGap )
    {
       buildOutput ( chGap ); 
       return strGapVert; 
    }
     
    private void buildOutput ( char chGap )
    {       
        if ( lastGap != null && lastGap.charValue() == chGap )
            return; // called for HorzResult and VertResult
        
        // Build strGapHorz and strGapVert which have inserted gaps
        buildAffineOutput ( chGap );
        
        //Score the alignment; sets all values that can later be obtained with gets
  
        HSRmatch = HSRlen = HSRstops = HSRstart = HSRend = 0;
        float HSRsim = 0.0f, HSRscore=0.0f;
        OLPmatch = OLPlen = 0;
        int i, j;
        int allS = 0, allE = 0; // start and stop of complete overlap
        float chScore=0.0f;
        boolean isOpen=false;
        
        // allows mismatches; saved in the HSR variables
        int qS=0, qE=0, qM=0, qLen=0, qStops=0;  
        float qSim =  0.0f, qScore = 0.0f;
        // no mismatches or gaps; saved in the second set of variables
        int pS=0, pE=0, pM=0; 
        int pStart=0, pEnd=0, pLen=0, pMatch=0; 
     
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
        OLPlen = allE - allS;  
        
        for (i = allS; i <= allE; i++) 
        {
	    		if (strGapHorz.charAt(i) == '*' && strGapVert.charAt(i) == '*') {qStops++;}
	    	
	        	if (strGapHorz.charAt(i) == chGap || strGapVert.charAt(i) == chGap) {	        		
	        		if (isOpen ) qScore -= gapExtend;
	        		else qScore -= gapOpen;
	        		isOpen = true;
	        		pM=pS=0;
	        		chScore = -1;
	        	}
	        	else {
	        		isOpen = false;
	        		chScore = cmp(strGapHorz.charAt(i), strGapVert.charAt(i));
	        		qScore += chScore;
	        	}
		    // XXX heuristic best score with mismatches and gaps
	        if (chScore < 0) {
				if (qScore < 0.0f || qStops > 2) { 
				    qStops=qM=qS=0;
				    qScore = 0.0f;
				}  
				pM=pS=0;
				continue;
	        }
 
			OLPmatch++; 
			pM++;
			qM++;
			
	    		if (qS == 0) qS = i;
	    		qE = i;
	    		qLen = qE-qS+1;
	    		qSim = (float) qM/qLen;
			//System.err.println("> " + qLen + " " + qSim + " " + qStops + " " + qM+ " " + qS+ " " + qE);
			//System.err.println("  " + HSRlen + " " + HSRsim + " " + HSRstops + " " + HSRmatch+ " " + HSRstart+ " " + HSRend);
    		if (cmpScore(qLen, qSim, qStops, HSRlen, HSRsim, HSRstops)) { 
    		//if (qScore > HSRscore) {
    			HSRlen = qLen;
    			HSRmatch = qM; 
    			HSRstart = qS;
    			HSRend = qE;
    			HSRsim = qSim;
    			HSRstops = qStops;
    			HSRscore = qScore;
    		} 
    		// bestscore with no mismatches or gaps	        		
    		if (pS==0) pS = i;
	    		pE = i;
	    		if (pM > pMatch) {
	    			pMatch = pM;
	    			pLen = pE-pS+1;
	    			pStart = pS;
	    			pEnd = pE;
	    		}
        } 

        // XXX heuristics -- perfect hits 
        if ((pLen > 100 && HSRsim < 90.0f) || (pLen > 200 && HSRsim < 95.0f)) {
	    		HSRlen = pLen;
	    		HSRmatch = pMatch;
	    		HSRstart = pStart;
	    		HSRend = pEnd;
	    		HSRstops = 0;
	    		HSRsim = 1.0f;
        }
    }
    
    public int getHSRmatch() 	{ return HSRmatch;}
    public int getHSRlen() 		{ return HSRlen;}
    public int getHSRstart() 	{ return HSRstart;}
    public int getHSRend() 		{ return HSRend;}
    public int getHSRstops() 	{ return HSRstops;}
    
    public int getOLPmatch() 	{ return OLPmatch;}
    public int getOLPlen() 		{ return OLPlen;}
    public int getOLPmaxGap() 	{ return OLPmaxGap;}
 
    public String getDescription() { 	
	    	String y = Converters.formatDecimal ( ((double) HSRmatch/HSRlen) * 100.0);
	    	int f = HSRstart % 3;
	    	if (f==0) f = 3;
	    	String s;
			s = "HSR: ";
			s += "   Id " + y;
	    	s += "   Len " + HSRlen;
	    	s += "   f " + f;
	    	return s;
    }
    

    
    /**
     * Routines for affine and non-affine dynamic programming
     */
    
    public boolean DPalign ( String strHorz, String strVert)
    {
	    	return matchAffine( strHorz, strVert );
    }
    
    private float cmp(char x, char y) {
    	if (x==y) return matchScore;
    	else return mismatchScore;
  
    }
    
    private boolean matchNonAffine (String strHorz, String strVert )
    {
        nRows = strVert.length() + 1;
        nCols = strHorz.length() + 1;
        nCells = nCols * nRows;
        lastGap = null;
        strInHorz = strHorz;
        strInVert = strVert;
 
        if (!checkAllocation ( )) return false;

        // Initialize top row
        for ( int i = 1; i < nCols; ++i )
        {
            if ( bFreeEndGaps ) matchRow[i] = 0;
            else matchRow[i] = - ( gapOpen * i );                    
            matchDir[i] = DIRECTION_LEFT;
        }
        
        // Initalize left column for direction arrays
        for ( int k = 1, i = nCols; k < nRows; ++k, i += nCols )
        {
            matchDir[i] = DIRECTION_UP;
        }
        
        matchRow[0] = 0;
        
        // Fill in all matricies simultaneously, row-by-row
        for ( int v = 1; v < nRows; ++v )
        {
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
            
            for ( int h = 1; h < nCols; ++h, ++i )
            {
                float fMatch = cmp(strVert.charAt(v-1), strHorz.charAt(h-1));

                float fUp = matchLastRow[h];
                if ( !bFreeEndGaps || ( h != nCols - 1 ) ) 
                    fUp -= gapOpen;
                float fDiag = matchLastRow[h-1] + fMatch;    
                float fLeft = matchRow[h-1];
                if ( !bFreeEndGaps || ( v != nRows - 1 ) ) 
                    fLeft -= gapOpen;
                
                matchRow[h] = fUp;
                matchDir[i] = DIRECTION_UP;
                
                if ( fDiag > matchRow[h] )
                {
                    matchRow[h] = fDiag;
                    matchDir[i] = DIRECTION_DIAGONAL; 
                }
                
                if ( fLeft > matchRow[h] )
                {
                    matchRow[h] = fLeft;
                    matchDir[i] = DIRECTION_LEFT; 
	            } 
	        }
	    }  

        return true;
    }
    private void buildNonAffineOutput ( char chGap )
    {
        strGapHorz = "";
        strGapVert = "";
        
        int i = nCells - 1;
        int v = strInVert.length() - 1;
        int h = strInHorz.length() - 1;
      
        while ( i > 0 )
        {
            switch ( matchDir[i] )
            {
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
        lastGap = new Character ( chGap );
    }
    
    private boolean matchAffine (String strHorz, String strVert )
    {
        nRows = strVert.length() + 1;
        nCols = strHorz.length() + 1;
        nCells = nCols * nRows;
        lastGap = null;
        strInHorz = strHorz;
        strInVert = strVert;
        
        float fTotalGapOpen = - ( gapOpen + gapExtend );
        
        if (!checkAllocation ( )) return false;
        
        // Initialize top row
        for ( int i = 1; i < nCols; ++i )
        {
            matchRow[i] = -Float.MAX_VALUE;
            matchDir[i] = DIRECTION_DIAGONAL;
            gapHorzRow[i] = -Float.MAX_VALUE;
            gapHorzDir[i] = DIRECTION_UP;
            if ( bFreeEndGaps )
                gapVertRow[i] = 0;
            else
                gapVertRow[i] =  -( gapOpen + (i-1) * gapExtend );
            gapVertDir[i] = DIRECTION_LEFT;
       }
        
        // Initalize left column for direction arrays
        for ( int k = 1, i = nCols; k < nRows; ++k, i += nCols )
        {
            matchDir[i] = DIRECTION_DIAGONAL;
            gapHorzDir[i] = DIRECTION_UP;
            gapVertDir[i] = DIRECTION_LEFT;
        }
        
        matchRow[0] = 0;
 
        // Fill in all matricies simultaneously, row-by-row
        for ( int v = 1; v < nRows; ++v )
        {
            int i = ( v * nCols ) + 1;
            
            // "Rotate" the score arrays.  The current row is now uninitialized, but the
            // last row is completely filled in.
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
            if ( bFreeEndGaps )
                gapHorzRow[0] = 0;
            else
                gapHorzRow[0] = - ( gapOpen + (v - 1) * gapExtend );     
            gapVertRow[0] = -Float.MAX_VALUE;
            
            for ( int h = 1; h < nCols; ++h, ++i)
            {
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

    private void testBest ( int i, boolean bCurRow, float fDUp, float fDDiag, float fDLeft )
    {
        // Choose the best choice with the arbitrary tie break of up, diagonal, left       
        // Note: the inversion between the direction in the matrix and the gap is correct
        float fUp, fDiag, fLeft;
     
        if ( bCurRow )
        {
            fUp = fDUp + gapHorzRow [i];
            fDiag = fDDiag + matchRow [i];
            fLeft = fDLeft + gapVertRow [i];
        }
        else
        {
            fUp = fDUp + gapHorzLastRow [i];
            fDiag = fDDiag + matchLastRow [i];
            fLeft = fDLeft + gapVertLastRow [i];
        }
        
        fLastBest = fUp;
        chLastBest = DIRECTION_UP;
        if ( fDiag > fLastBest )
        {
            fLastBest = fDiag;
            chLastBest = DIRECTION_DIAGONAL;
        }
        if ( fLeft > fLastBest )
        {
            fLastBest = fLeft;
            chLastBest = DIRECTION_LEFT;
        }
    }
    
    private void buildAffineOutput ( char chGap )
    {      
        strGapHorz = "";
        strGapVert = "";
        
        int i = nCells - 1;
        int v = strInVert.length() - 1;
        int h = strInHorz.length() - 1;
        
        char chNextHop = startDir;       
        
        while ( i > 0 )
        {
            switch ( chNextHop )
            {
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
                throw new RuntimeException ( "Invalid direction..." );
            }  
        }      
        lastGap = new Character ( chGap );
    }
    
    // For doHomology, the arrays get reused.
    private boolean checkAllocation ( )
    {
    		if (nCells > maxCells) {
       		System.err.println("Not enough memory to align sequences - need " + nCells + "kb");
    			return false;
    		}
    	    fLastBest = -Float.MAX_VALUE;
    	    chLastBest = DIRECTION_DIAGONAL;
    	    
    		try {
	        if ( matchDir == null || matchDir.length < nCells )
	        {
	            matchDir = new char [nCells];

	            gapHorzDir = new char [nCells];
	            gapVertDir = new char [nCells];        
	            
	        }
	        else {
	        		for (int i=0; i< matchDir.length; i++) matchDir[i] = ' ';
	        		 
	        		for (int i=0; i< matchDir.length; i++) 
	        				 gapHorzDir[i] =  gapVertDir[i] = ' ';
	        }
	        int max = (nRows > nCols ) ? nRows : nCols;
	        
	        if ( matchRow == null || matchRow.length < max )
	        {
	            matchRow = new float [max];
	            matchLastRow = new float [max];  
	           
                gapHorzRow = new float [max];
                gapHorzLastRow = new float [max];
                gapVertRow = new float [max];
                gapVertLastRow = new float [max];
	            
	        }
	        else {
        			for (int i=0; i< matchRow.length; i++) matchRow[i] = 0.0f;
    				for (int i=0; i< matchRow.length; i++) 
    					gapHorzRow[i] =  gapVertRow[i] = 
    						gapHorzLastRow[i] =  gapVertLastRow[i] = 0.0f;
	        	}
    		}
    		catch (OutOfMemoryError E) {
    			matchDir = null;
    			maxCells = nCells;
    			System.err.println("Not enough memory to align sequences - need " + nCells + "kb");
    			return false;
    		}
        return true;
    }
    
    public void clear() {
	    	matchLastRow = null;
	    	matchRow = null;
	    	matchDir = null;
	    	gapHorzRow = null;
	    	gapHorzLastRow = null;
	    	gapHorzDir = null;
	    	gapVertRow = null;
	    	gapVertLastRow = null;
	    	gapVertDir = null;
    }
     
    private String strInHorz = null;
    private String strInVert = null;
    private String strGapHorz = null;
    private String strGapVert = null;
    private Character lastGap = null;
    
    private static final char DIRECTION_UP = '^';
    private static final char DIRECTION_LEFT = '<';
    private static final char DIRECTION_DIAGONAL = '\\';
    
    private float matchScore = 1.8f;
    private float mismatchScore = -1.0f;
    private float gapOpen = 3.0f;	// 3.0f changed to neg in code
    private float gapExtend = 0.7f;	// lowered gaps and was worse
    private boolean bFreeEndGaps = true;
    
    int nCells, nRows, nCols = Integer.MIN_VALUE;
    private float [] matchLastRow = null;
    private float [] matchRow = null;
    private char []  matchDir = null;
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
    
    private int HSRmatch = 0;
    private int HSRlen = 0;
    private int HSRstart = 0;
    private int HSRend = 0;
    private int HSRstops = 0;
    
    private int OLPmatch = 0;
    private int OLPlen = 0;
    private int OLPmaxGap = 0;
  

}