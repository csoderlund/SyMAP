package symapMultiAlign;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.JPanel;

public class AlignmentPanelBase extends JPanel
{
	public AlignmentPanelBase ( Font baseFont )
	{
		theFont = baseFont;
		fontMetrics = getFontMetrics ( baseFont );
		dFontAscent = fontMetrics.getAscent();
		dFontMinHeight = fontMetrics.getAscent() + fontMetrics.getDescent() 
			+ fontMetrics.getLeading();
		dFontCellWidth = fontMetrics.stringWidth( "A" );
	}
	
	// Abstract methods (child class should over-ride)
	public void selectAllRows () { }
	public void selectNoRows () { }
	public void selectAllColumns () { }
	public void selectNoColumns () { }
	public boolean hasSelection () { return false; }
	public Vector<String> getContigIDs() { return null; } 
	public void getSelectedContigIDs ( TreeSet<String> set ) { }
	public void setSelectedContigIDs ( TreeSet<String> set ) { }
	public void selectMatchingSequences ( String strSeqName ) { } 
	public void addSelectedConsensusToFASTA ( PrintStream seqFile, PrintStream qualFile ) throws Exception {}
	public void addConsensusToFASTA ( PrintStream seqFile, PrintStream qualFile ) throws Exception {} 
	public void addSelectedSequencesToFASTA ( PrintStream seqFile, PrintStream qualFile ) throws Exception {}
	public void addAllSequencesToFASTA ( PrintStream seqFile, PrintStream qualFile ) throws Exception {} 
	public void addSelectedSequencesToSet ( TreeSet<String> set ){ }
	
	public void handleClick( MouseEvent e, Point localPoint ) { }
	public double getGraphicalDeadWidth ( ) { return 0; }
	public void refreshPanels ( ) { }
	
	public int getNumBuried() { return 0; }	
	public int getNumSNPs() { return 0; }	
	
	// Frame end points.  For filling backgrounds of coding, etc
	protected double getFrameLeft () { return 0; }
	protected double getFrameRight () { return 0; }
	
	// Bases Per Pixel : the number of bases represent by each column of pixels in graphical mode
	public void setBasesPerPixel ( int n ) throws Exception { nBasesPerPixel = n; clearPanels ( ); }
	public int getBasesPerPixel ( ) { return nBasesPerPixel; }
	
	// Min Pixel X : the lowest pixel on the x-axis for drawing; corresponds to the base at Min Index
	public void setMinPixelX ( double dX ) { dMinPixelX = dX; clearPanels ( ); }
	public double getMinPixelX ( ) { return dMinPixelX; }
	
	public void setIndexRange ( int nMin, int nMax ) 
	{ 
		if ( nMin >= nMax )
			throw new RuntimeException ( "Min (" + nMin + ") must be less than Max (" + nMax + ")." );
		
		nMinIndex = nMin;   // the lowest index for bases in the alignment
		nMaxIndex = nMax;	// the highest index for bases in the alignment
		clearPanels ( );
	}
	public int getMinIndex ( ) { return nMinIndex; }	
	public int getMaxIndex ( ) { return nMaxIndex; }	
	public int getTotalBases ( ) { return nMaxIndex - nMinIndex + 1; }
		
	public static int GRAPHICMODE = 1;
	public static int TEXTMODE = 0;
	public int getDrawMode ( ) { return nDrawMode; }
	public void setDrawMode(int mode) {
		nDrawMode = mode;
		clearPanels();
	}
	public void setCtgDisplay(boolean b) { // CAS 20aug10
		bCtgDisplay = b;
	}
	
	public void changeDraw()
  	{
  		if(nDrawMode == GRAPHICMODE) nDrawMode = TEXTMODE;
    	else nDrawMode = GRAPHICMODE;
  		clearPanels ( );
  	}
	
	public void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D)g;
		super.paintComponent( g );
		
		if ( bPanelsDirty )
		{
			refreshPanels ();
			bPanelsDirty = false;
		}
		drawCodingPanels ( g2 );
	}
	
	protected double getSequenceWidth ( )
	{
		if( nDrawMode == GRAPHICMODE ) return getDrawWidth();
		else return getWriteWidth();
	}
	
	protected void drawSequence( Graphics2D g2, String label, String sequence, double dYTop, double dYBottom )
	{
		if( nDrawMode == GRAPHICMODE )
			drawSequenceLine( g2, label, sequence, dYTop, dYBottom );
		else
			writeSequenceLetters( g2, label, sequence, dYTop, dYBottom );
	}
	
	protected void writeSequenceLetters ( Graphics2D g2,
											String label,
											String sequence,
											double dYTop, 
											double dYBottom  ) 
	{
		double dWriteBaseline = (dYBottom - dYTop) / 2 - dFontMinHeight / 2 + dYTop + dFontAscent; 

		g2.setColor(Color.black);
			
		// Draw the columns of bases that are currently visible TODO
        int nStart = 0;
        int nEnd = sequence.length();
        
		for( int i = nStart; i < nEnd; i++ )
		{		
			double dX = calculateWriteX ( i );
			
			Color baseColor = Color.black;
			if (bCtgDisplay) {
				if ( getIsNAt(i) ) 						baseColor = purple; // just in consensus
				else if ( getIsMismatchAt( sequence, i ) )		baseColor = mismatchRed;
				else if ( getIsGapAt( sequence, i )) 			baseColor = darkGreen;
				else if ( getIsLowQualityAt ( sequence, i ) )	baseColor = lowQualityBlue;

			} else {
				if ( getIsStop ( sequence, i ) )					baseColor = Color.gray;
				else if ( getIsNAt ( sequence, i ) )				baseColor = mediumGray;  
				else if ( getIsSubstitutionAt ( sequence, i ) ) 	baseColor = purple;
				else if ( getIsGapAt( sequence, i )) 			baseColor = darkGreen;
				else if ( getIsMismatchAt( sequence, i ) )		baseColor = mismatchRed;
			}
			
			drawCenteredBase ( g2, sequence.charAt(i), baseColor, dX, dWriteBaseline );
		}
	}
	
	protected void drawSequenceLine( Graphics2D g2,
										String label,
										String sequence,
										double dYTop, 
										double dYBottom)
	{
		//Used to record if the sequence begins and/or ends with a gap
		//Special formatting used in that case in place of green hash
		int startGapEnd = 0;
		int endGapStart = sequence.length();
		
		//Find the locations of the start and stop 
		while(sequence.charAt(startGapEnd) == '.') startGapEnd++;
		while(sequence.charAt(endGapStart-1) == '.') endGapStart--;
		
		// Determine the position of the sequence line, but don't draw until after the hashes
		// draw the line and arrow heads
		double dXPosStart = calculateDrawX ( startGapEnd );
		double dXPosEnd = calculateDrawX ( endGapStart );

		double dHeight = dYBottom - dYTop;
		double dYCenter = dHeight / 2.0 + dYTop;

		int RED_HASH_UP = (int)(dHeight/2) - 2;
		int RED_HASH_DOWN = RED_HASH_UP;
		int GREEN_HASH_UP = RED_HASH_DOWN - 1;
		int GREEN_HASH_DOWN = RED_HASH_DOWN - 1;
		int GRAY_HASH_UP = GREEN_HASH_UP - 2;
		int GRAY_HASH_DOWN = GREEN_HASH_DOWN - 2;
		int BLUE_HASH_UP = GREEN_HASH_UP - 2;
		int BLUE_HASH_DOWN = GREEN_HASH_DOWN - 2;	
		
		boolean bBlueStopHash;  
		boolean bGreenHash;
		boolean bRedHash;
		boolean bGreyHash;
		boolean bBlueHash;
		boolean bPurpleHash;
		boolean bGrayHash;
		int nCurBasesToGroup = -1;
		
		// For each letter in the sequence
		for( int i = startGapEnd; i < endGapStart;)
		{
			double dHashXPos = calculateDrawX ( i );
		
			bBlueStopHash = false;
			bGreenHash = false;
			bRedHash = false;
			bBlueHash = false;
			bGreyHash = false;
			bPurpleHash = false;
			bGrayHash = false;
			
			if ( nCurBasesToGroup <= 0 )
			{
				// We may short the first group so that all grouping is aligned between ESTs
				nCurBasesToGroup = getBasesPerPixel ();
				nCurBasesToGroup -= ( getMinIndex () ) % getBasesPerPixel ();
			}
			else
			{
				// Not the first group, do the full amount
				nCurBasesToGroup = getBasesPerPixel ();
			}
			
			// Aggregate together the information for the next BASES_PER_PIXEL bases 
			// That is, multiple bases are represented together
			// TODO
			for ( int j = 0; i < sequence.length() && j < nCurBasesToGroup; ++i, ++j )
			{			
				if (bCtgDisplay) {
					if ( getIsNAt(i) )							bGreyHash = true; 
					else if ( getIsLowQualityAt ( sequence, i ))	bBlueHash = true;
					if ( getIsEndGapAt( sequence, i))			bGrayHash = true;
					else if ( getIsGapAt ( sequence, i ) )				bGreenHash = true;
					else if ( getIsMismatchAt ( sequence, i ))		bRedHash = true;
				} else {
					if ( getIsEndGapAt( sequence, i))			bGrayHash = true;
					else if ( getIsNAt ( sequence, i ) )				bGreyHash = true;  		// n's and x's
					else if ( getIsSubstitutionAt ( sequence, i ) )	bPurpleHash = true;       // aa only
					else if ( getIsGapAt ( sequence, i ) )				bGreenHash = true;
					else if ( getIsMismatchAt ( sequence, i ))		bRedHash = true;
					if ( getIsStop( sequence, i ))				bBlueStopHash = true;
				}
			}

			if ( bBlueStopHash ) // little T above sequence
			{
				drawHash ( g2, dHashXPos, dYTop, dYTop + 2, mediumGray );	
				g2.draw( new Line2D.Double( dHashXPos - 1, dYTop, dHashXPos + 1, dYTop ) );
			}
			
			if ( bRedHash ) // larger up down - mismatch
				drawHash ( g2, dHashXPos, dYCenter - RED_HASH_UP, dYCenter + RED_HASH_DOWN, mismatchRed );
			
			if ( bGreenHash )// little smaller up down - gap
				drawHash ( g2, dHashXPos, dYCenter - GREEN_HASH_UP, dYCenter + GREEN_HASH_DOWN, gapGreen );
			
			if( bGrayHash )
				drawHash ( g2, dHashXPos, dYCenter - GRAY_HASH_UP, dYCenter + GRAY_HASH_DOWN, mediumGray );
			
			if ( bGreyHash ) // smaller up and down - N's or x's
				drawHash ( g2, dHashXPos, dYCenter - BLUE_HASH_UP, dYCenter + BLUE_HASH_DOWN, mediumGray );	
			
			if ( bPurpleHash )  // little smaller up down - substitute
				drawHash ( g2, dHashXPos, dYCenter - BLUE_HASH_UP, dYCenter + BLUE_HASH_DOWN, purple );
			
			if( bBlueHash ) // smaller up down - low quality
				drawHash ( g2, dHashXPos, dYCenter - BLUE_HASH_UP, dYCenter + BLUE_HASH_DOWN, lowQualityBlue );
			
		}
		
		// Draw the line for the sequence
		g2.setColor( Color.black );
		g2.draw( new Line2D.Double( dXPosStart, dYCenter, dXPosEnd, dYCenter ) );
		
		// Put a green dot on the line anywhere there is a gap (overwrite that position of the line)
		// getDrawGreenDotAt always returns false
		for(int i = 0; i < sequence.length(); ++i )
		{
			if ( getDrawGreenDotAt ( sequence, i ) )
			{
				double dHashXPos = calculateDrawX (i);
				drawDot ( g2, dHashXPos, dYCenter, gapGreen );
			}
		}

		// Draw the arrow head
		{
			double dArrowHeight = dHeight / 2.0 - 2;
			drawArrowHead ( g2, dXPosEnd, dYCenter, dArrowHeight, true /* right */ );
		
			g2.setColor(Color.black);
		}
	}
	
	//	 Draw a ruler indicating base position
	protected void drawRuler ( Graphics2D g2, double dXMin, double dYTop, double dYBottom )  
	{
		// Center the text (as much as possible in the input box)
		TextLayout layout = new TextLayout( "9999", theFont, g2.getFontRenderContext() );		
		double dY = (dYBottom + dYTop) / 2 + layout.getBounds().getWidth() / 2;
		
		int nTickIncrement = 100;
		if ( nDrawMode != GRAPHICMODE )
			nTickIncrement = 10;
		int nTickStart = ( nMinIndex / nTickIncrement ) * nTickIncrement;		
		if ( nTickStart < nMinIndex )
			nTickStart += nTickIncrement;
		
		for ( int i = nTickStart; i < nMaxIndex; i += nTickIncrement )
		{	
			double dX;
			if ( nDrawMode != GRAPHICMODE )
				dX = ( calculateWriteX (i) + calculateWriteX (i+1) ) / 2;
			else
				dX = calculateDrawX (i);
			
			if ( dX > dXMin )
				drawVerticalText ( g2, String.valueOf(i), dX, dY );
		}
	}
	
	// defined in PairwiseAlignmentPanel and ContigPanel
	protected boolean getIsLowQualityAt ( String seq, int nPos ) { return false; }
	protected boolean getIsEndGapAt( String seq, int nPos) { return false; }
	protected boolean getIsGapAt ( String seq, int nPos ) { return false;}		
	protected boolean getIsMismatchAt ( String seq, int nPos ) { return false; }
	protected boolean getIsJoinedByNAt ( String seq, int nPos ) { return false; }
	protected boolean getIsSubstitutionAt ( String seq, int nPos ) { return false; }
	protected boolean getIsStop ( String seq, int nPos ) { return false; } 
	protected boolean getIsNAt ( String seq, int nPos ) { return false; } 
	protected boolean getIsNAt ( int nPos ) { return false; } 
	
	protected boolean getDrawGreenDotAt ( String seq, int nPos ) { return false; }
	
	protected int getCellWidthInt ( )
	{
		return (int)dFontCellWidth;
	}
	
	protected double getDrawWidth ( )
	{
		return ( nMaxIndex - nMinIndex + 1 ) / nBasesPerPixel;
	}
	
	// The total width need for writing the bases
	protected int getWriteWidthInt ( )
	{
		return (int)getWriteWidth ();
	}

	protected double getWriteWidth ( )
	{
		return dFontCellWidth * ( nMaxIndex - nMinIndex + 1 );
	}
	
	protected double calculateX ( int nBasePos ) 
	{
  		if(nDrawMode == GRAPHICMODE) return calculateDrawX ( nBasePos );
    	else return calculateWriteX ( nBasePos );	
	}
	
	// Returns the pixel position for the left hand side of the "box" to write in
	protected double calculateWriteX ( int nBasePos ) 
	{
		return dMinPixelX + ( nBasePos - nMinIndex ) * dFontCellWidth;
	}	
	
	protected int calculateIndexFromWriteX ( double dX ) 
	{ 
		return (int)( (dX - dMinPixelX) / dFontCellWidth ) + nMinIndex; 
	}	

	protected double calculateDrawX ( int nBasePos ) 
	{ 
		return dMinPixelX + ( nBasePos - nMinIndex + 1 ) / nBasesPerPixel;
	}

	// Drawing utility methods
	protected double getTextWidth ( String str )
	{
		if ( str == null )
			return 0;
		else
			return fontMetrics.stringWidth ( str );
	}
	
	protected void drawText ( Graphics2D g2, String str, double dXLeft, double dYTop, Color theColor )
	{
		if ( str.length() == 0 )
			return;
		
		g2.setColor ( theColor );
		
		TextLayout layout = new TextLayout( str, theFont, g2.getFontRenderContext() );
		float fBaseline = (float)(dYTop + dFontAscent); 
		layout.draw( g2, (float)dXLeft, fBaseline );
	}
	
	protected void drawText ( Graphics2D g2, String str, double dX, double dY )
	{
		drawText ( g2, str, dX, dY, Color.BLACK );
	}
	
	private void drawCenteredBase ( Graphics2D g2, char chBase, Color theColor, double dCellLeft, double dBaseline )
	{
		g2.setColor ( theColor );
		
		String str = "" + chBase;
		TextLayout layout = new TextLayout( str, theFont, g2.getFontRenderContext() );
	   	Rectangle2D bounds = layout.getBounds();
		float fCharWidth = (float)bounds.getWidth();
		
		// Adjust x draw position to center the character in its rectangle
		float fX = (float)dCellLeft + (float)(dFontCellWidth - 1.0f) / 2.0f - fCharWidth / 2.0f; 
		   
		layout.draw( g2, fX, (float)dBaseline );

		g2.setColor ( Color.black );		
	}
	
	protected void drawCodingPanels ( Graphics2D g2 )
	{
		Iterator<JPanel> iterPanel = codingPanels.iterator();
		while ( iterPanel.hasNext() )
		{
			JPanel curPanel = (JPanel)iterPanel.next();
			g2.setColor( curPanel.getBackground() );
			g2.fill( curPanel.getBounds() );
		}		
	}
	
	protected void clearPanels ( )
	{
		// Remove the old panels
		bPanelsDirty = true;
		Iterator<JPanel> iter = codingPanels.iterator();
		while ( iter.hasNext() )
			remove( (Component)iter.next() );
		codingPanels.removeAllElements();
		super.repaint();
	}
	

/*	private void createCodingPanel ( String strDescription,
										Color theColor,
										double dLeft,
										double dRight,
										double dTop,
										double dBottom )
	{		
		// Create the panel and add it to the main panel
		JPanel thePanel = new JPanel ();
		thePanel.setBackground( theColor );
		thePanel.setSize ( (int)(dRight - dLeft), (int)(dBottom - dTop) );
		thePanel.setToolTipText(strDescription );
		thePanel.setLocation ( (int)dLeft, (int)dTop );
		thePanel.setOpaque( false );
		
		add ( thePanel );
		codingPanels.add( thePanel );  
	}*/

	private void drawHash ( Graphics2D g2, double dHashXPos, double dHashTopY, double dHashBottomY, Color hashColor )
	{
		g2.setColor( hashColor );
		g2.draw( new Line2D.Double( dHashXPos,  dHashTopY, dHashXPos, dHashBottomY ) );
	}
	
	private void drawDot ( Graphics2D g2, double dX, double dY, Color dotColor )
	{
		g2.setColor( dotColor );
		g2.draw( new Line2D.Double( dX - 0.5, dY, dX + 0.5, dY ) );		
	}
	
	private void drawArrowHead ( Graphics2D g2, double dArrowPntX, double dArrowPntY, double dHeight, boolean bRight )
	{
		final double ARROW_WIDTH = dHeight - 1;
		
		double dYStartTop = dArrowPntY - dHeight;
		double dYStartBottom = dArrowPntY + dHeight;
		double dXStart = dArrowPntX;
		
		if ( bRight ) 	dXStart -= ARROW_WIDTH;
		else			dXStart += ARROW_WIDTH;

		g2.draw( new Line2D.Double( dXStart, dYStartTop, dArrowPntX, dArrowPntY ) );
		g2.draw( new Line2D.Double( dXStart - 1, dYStartTop, dArrowPntX - 1, dArrowPntY ) );
		g2.draw( new Line2D.Double( dXStart, dYStartBottom, dArrowPntX, dArrowPntY ) );
		g2.draw( new Line2D.Double( dXStart - 1, dYStartBottom, dArrowPntX - 1, dArrowPntY ) );
	}
	
	// Positioned by the center of the left side
	private void drawVerticalText ( Graphics2D g2, String str, double dX, double dY )
	{
		if ( str.length() == 0 )
			return;
		
		TextLayout layout = new TextLayout( str, theFont, g2.getFontRenderContext() );

		// Draw
		g2.rotate ( - Math.PI / 2.0 );
		g2.setColor ( Color.BLACK );
		float fHalf = (float)(layout.getBounds().getHeight() / 2.0f);
		layout.draw( g2, (float)-dY, (float)dX + fHalf );	
		g2.rotate ( Math.PI / 2.0 );
	}	
	
	protected void drawSquareBracket ( Graphics2D g2, double dHashXPos, double dHashTopY, double dHashBottomY, Color hashColor, boolean bRightwise )
	{
		g2.setColor( hashColor );
		g2.draw( new Line2D.Double( dHashXPos, dHashTopY, dHashXPos, dHashBottomY ) );
		double dXStart = dHashXPos;
		double dXEnd = dHashXPos;
		if ( bRightwise ) 	dXEnd += 2;
		else 				dXEnd -= 2;
		
		g2.draw( new Line2D.Double( dXStart, dHashTopY, dXEnd, dHashTopY ) );
		g2.draw( new Line2D.Double( dXStart, dHashBottomY, dXEnd, dHashBottomY ) );
	}
	
	FontMetrics fontMetrics = null;
	private Font theFont = null;
	
	// Attibutes for positioning
	private double dMinPixelX = 0;		// The lowest pixel (associated base at min index) 
	private int nMinIndex = 1;			// The lowest base position in the alignment
	private int nMaxIndex = 1;
	private double dFontMinHeight = 0;  // The minimum sized box a letter will fit in based on our font
	private double dFontCellWidth = 0;	
	private double dFontAscent = 0;		// The distance from the top of the write cell to the baseline
	private int nBasesPerPixel = 3;			

	private int nDrawMode = GRAPHICMODE;
	private boolean bCtgDisplay = true;
	
	// Panels to show the coding region
	private boolean bPanelsDirty = false;
	private Vector<JPanel> codingPanels = new Vector<JPanel> ();      
	
    // Color constants 
    // coding regions to yellow for Windows 
	public static final Color colorCoding1 = Color.YELLOW;
	public static final Color colorCoding2 = Color.YELLOW;
	public static final Color colorCoding3 = Color.YELLOW;
	public static final Color selectColor = Color.GRAY;
	
	public static final Color mismatchRed = Color.red;
	public static final Color lowQualityBlue = Color.blue;	
	public static final Color gapGreen = new Color(0,200,0);
	public static final Color darkGreen = new Color(0,102,0);
	public static final Color lightGray =  new Color(230, 230, 230);
	public static final Color mediumGray =  new Color(180, 180, 180);
	public static final Color purple = new Color(138, 0, 184); 

    private static final long serialVersionUID = 1;
}
