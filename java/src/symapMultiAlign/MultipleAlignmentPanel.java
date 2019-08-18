package symapMultiAlign;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import symapQuery.SyMAPQueryFrame;

public class MultipleAlignmentPanel extends AlignmentPanelBase {
	private static final long serialVersionUID = 8622662337486159085L;
	
	private static final Font DEFAULT_FONT = new Font("Monospaced", Font.PLAIN, 11);

	public MultipleAlignmentPanel(SyMAPQueryFrame parentFrame, MultiAlignmentData alignData, int nInTopGap, int nInBottomGap, int nInLeftGap, int nInRightGap) {
		super ( DEFAULT_FONT );
		super.setBackground( Color.WHITE );
		super.setLayout( null );
		
		theSequences =alignData.getSequences();
		theSequenceLabels = alignData.getSequenceNames();
		
		theSeqStarts = new int[alignData.getNumSequences()];
		theSeqStops = new int[alignData.getNumSequences()];
		seqSelected = new boolean[alignData.getNumSequences()];
		dSequenceStart = 0.0;
		int maxSeqLen = 0;
		for(int x=0; x<theSequences.length; x++) { 
			for(theSeqStarts[x] = 0; theSeqStarts[x] < theSequences[x].length() && theSequences[x].charAt(theSeqStarts[x]) == '.'; theSeqStarts[x]++);
			for(theSeqStops[x] = theSequences[x].length() - 1; theSeqStops[x] >= 0 && theSequences[x].charAt(theSeqStops[x]) == '.'; theSeqStops[x]--);
			dSequenceStart = Math.max(dSequenceStart, super.getTextWidth(theSequenceLabels[x]));
			maxSeqLen = Math.max(maxSeqLen, theSequences[x].length());
			seqSelected[x] = false;
		}
//		description = theAlignment.getDescription();
		columnSelected = new boolean[maxSeqLen];
		for(int x=0; x<columnSelected.length; x++)
			columnSelected[x] = false;

		dSequenceStart += nInsetGap * 2.0d + nInLeftGap;
		super.setMinPixelX ( dSequenceStart );
		super.setIndexRange ( 0, maxSeqLen );
		super.setCtgDisplay(false);

		nTopGap = nInTopGap;
		nBottomGap = nInBottomGap;
		nRightGap = nInRightGap;
		nLeftGap = nInLeftGap;	
		isDNA=false;
		
		// Calculate drawing positions
		dFrameLeftX = nInLeftGap;
		dFrameTopY = nInTopGap;
		dRulerHeight = super.getTextWidth( "999999" );
	}
	
	public void refreshPanels ( ) 
	{ 
		selectionBox = new Rectangle2D.Double ( dFrameLeftX, dFrameTopY,  getFrameWidth (), dFrameHeight );
	}
	
	//---------------------------------JPanel Over-rides-------------------------------------//

	public void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D)g;
		super.paintComponent( g );
		
		// Draw the outside frame and divide
		Rectangle2D outsideBox = new Rectangle2D.Double ( dFrameLeftX, dFrameTopY, getFrameWidth (), dFrameHeight );
		
		if(super.getDrawMode() == TEXTMODE) {
			double width = getTextWidth("?");

			g2.setColor( selectColor );
			for(int x=0; x<columnSelected.length; x++) {
				if(columnSelected[x]) {
					double pos = calculateWriteX(x);
					
					g2.fill( new Rectangle2D.Double( pos, dFrameTopY, width, dFrameHeight) );
				}
			}
			
		}
		
		if ( hasSelection() )
		{
			// Outline whole thing
			g2.setColor( selectColor );
			
			for(int x=0; x<seqSelected.length; x++) {
				if(seqSelected[x]) {
					g2.fill( new Rectangle2D.Double( dFrameLeftX, topSelect[x], getFrameRight ( ) - dFrameLeftX, bottomSelect[x] - topSelect[x]) );
				}
			}
			g2.setColor( Color.BLACK );
		}
			
		g2.setColor( borderColor );
		g2.draw ( outsideBox );
		g2.draw( new Line2D.Double( dFrameLeftX, dDivider1Y, getFrameRight ( ), dDivider1Y ) );
		
		super.drawRuler( g2, 0, dRulerTop, dRulerBottom );

		for(int x=0; x<theSequences.length; x++) {
			super.drawText( g2, theSequenceLabels[x], dFrameLeftX + nInsetGap, seqTop[x] );
			drawSequence ( g2, theSequenceLabels[x], theSequences[x], seqTop[x], seqBottom[x] );
		}
	}
	
	public Dimension getMaximumSize()
	{
		return new Dimension ( Integer.MAX_VALUE, (int)getPreferredSize ().getHeight() );
	}
	
	public Dimension getPreferredSize()
	{
		int nWidth = nLeftGap + (int)getFrameWidth ( ) + nRightGap;
		int nHeight = nTopGap + (int)dFrameHeight + nBottomGap;
		return new Dimension( nWidth, nHeight ); 
	}
	
	//---------------------------------AlignmentPanelBase Over-rides-------------------------------------//
	
	protected boolean getIsStop ( String seq, int nPos ) // 
	{   // CAS 17aug10 - they get changed to upper case in safeGetBaseAt

		if (isDNA) return false;
		
		if(seq.charAt(nPos) == '*') return true;

		return false;
	}
	
	protected boolean getIsNAt ( String seq, int nPos ) // ambiguous
	{   // CAS 17aug10 - they get changed to upper case in safeGetBaseAt
		char x = 'X';
		if (isDNA) x = 'N';
		
		char c1 = theSequences[0].charAt(nPos);
		char c2 = seq.charAt(nPos);
		
		if(c2 == x || c1 == ' ') return true;
		return false;
	}
	
	protected boolean getIsEndGapAt( String seq, int nPos) {
		if(nPos < theSeqStarts[0] || nPos >theSeqStops[0]) return true;
		return false;
	}
	protected boolean getIsGapAt ( String seq, int nPos ) // only green on gap strand
	{
		if(seq.charAt(nPos) == '.') return true;
		return false;
	}
	
	protected boolean getIsMismatchAt ( String seq, int nPos ) 
	{ 	
		char x = 'X';
		if (isDNA) x = 'N';
		char x1 = seq.charAt( nPos );
		char x2 = theSequences[0].charAt( nPos );
		
		if (x1 == ' ' || x1 == '.' || x1 == x) return false; // space is no base
		if (x2 == ' ' || x2 == '.' || x2 == x) return false; // 

		return x1 != x2; // mis both strands
	}
	
	protected boolean getIsSubstitutionAt ( String seq, int nPos )         // sub both strands
	{ 
		return 	!isDNA 
				&& theSequences[0].charAt( nPos ) != seq.charAt( nPos )
				&& AlignmentData.isCommonAcidSub( theSequences[0].charAt( nPos ), seq.charAt( nPos ) ); 
	}
	
	public void setDrawMode(int mode) {
		super.setDrawMode(mode);
  		super.setMinPixelX ( dSequenceStart );
	}
	
	public void changeDraw()
  	{
		super.changeDraw ();
  		super.setMinPixelX ( dSequenceStart );
  	}
	
	public void setBasesPerPixel ( int nBasesPerPixel ) throws Exception
	{
		super.setBasesPerPixel( nBasesPerPixel );
		super.setMinPixelX ( dSequenceStart );

		dFrameHeight = nInsetGap * ((double)theSequences.length) + nRowHeight * ((double)theSequences.length) + // Sequences
					 + nInsetGap * ((double)theSequences.length) + dRulerHeight; 		// Ruler
		dDivider1Y = dFrameTopY + dHeaderHeight;
		dRulerTop = dDivider1Y + nInsetGap;
		dRulerBottom = dRulerTop + dRulerHeight;
		
		seqTop = new double[theSequences.length];
		seqBottom = new double[theSequences.length];
		topSelect = new double[theSequences.length];
		bottomSelect = new double[theSequences.length];
		
		double top = dRulerBottom + nInsetGap;
		for(int x=0; x < theSequences.length; x++) {
			seqTop[x] = top;
			seqBottom[x] = seqTop[x] + nRowHeight;
			
			topSelect[x] = seqTop[x] - nInsetGap / 2.0;
			bottomSelect[x] = seqBottom[x] + nInsetGap / 2.0;
			
			top = seqBottom[x] + nInsetGap;
		}
	}
	
	public double getGraphicalDeadWidth ( )
	{
		// The total width of what can't be used for drawing the bases in graphical mode
		return dSequenceStart + nInsetGap;
	}
	
	private double getFrameWidth ( )
	{
		double dWidth = dSequenceStart + getSequenceWidth ( ) + nInsetGap;
		return Math.max ( 700, dWidth );
	}
	
	protected double getFrameLeft ( )
	{
		return dFrameLeftX;
	}
	
	protected double getFrameRight ( )
	{
		return dFrameLeftX + getFrameWidth ( );
	}
	
	public void setBorderColor(Color newColor) {
		borderColor = newColor;
	}
	
	public void selectAllRows () { 
		for(int x=0; x<seqSelected.length; x++)
			seqSelected[x] = true;
		repaint (); 
	}
	
	public void selectNoRows () {	
		for(int x=0; x<seqSelected.length; x++)
			seqSelected[x] = false;
		repaint ();
	}
	
	public void selectAllColumns () {
		for(int x=0; x<columnSelected.length; x++)
			columnSelected[x] = true;
		repaint();
	}
	
	public void selectNoColumns () {
		for(int x=0; x<columnSelected.length; x++)
			columnSelected[x] = false;
		repaint();
	}
	
	public boolean hasSelection () {
		boolean retVal = false;
		for(int x=0; x<seqSelected.length && !retVal; x++)
			retVal = seqSelected[x];
		return retVal;
	}
	
	public int getNumSequencesSelected() {
		int retVal = 0;
		for(int x=0; x<seqSelected.length; x++)
			if(seqSelected[x])
				retVal++;
		return retVal;
	}
	
	public String getSelectedSequence() {
		String retVal = "";
		for(int x=0; x<seqSelected.length && retVal.length() == 0; x++) {
			if(seqSelected[x])
				retVal = theSequences[x];
		}
		return retVal;
	}
	
	public void handleClick( MouseEvent e, Point localPoint )
	{
		boolean highlightChanged = false;
        if ( selectionBox.contains(localPoint) )
        {     
        	//Determine the column for the click
    		if(super.getDrawMode() == TEXTMODE) {
    			int xPos = calculateIndexFromWriteX ( localPoint.x );
    			if(xPos >= 0 && xPos < columnSelected.length) {
    				highlightChanged = true;
        			if ( !e.isControlDown() && !e.isShiftDown() )
        				selectNoColumns();
    				columnSelected[xPos] = !columnSelected[xPos];
    			}
    		}
        	
            // Determine the row for the click.
        	if ( topSelect[0] <= localPoint.y && localPoint.y <= bottomSelect[bottomSelect.length-1])
    		{
        		highlightChanged = true;
    			// Clear all selected rows if neither shift nor control are down
    			if ( !e.isControlDown() && !e.isShiftDown() )
    				selectNoRows();
    			
    			for(int x=0; x<topSelect.length; x++) {
        			if ( topSelect[x] <= localPoint.y && localPoint.y <= bottomSelect[x] )
        				seqSelected[x] = !seqSelected[x];    				
    			}
    		}
    		if(!highlightChanged)
    		{	
                // Click is inside the box, but not within any of the sequences (i.e. in the header)
    			if ( ( e.isControlDown() || e.isShiftDown() ) && hasSelection () ) {
    				selectNoRows ();
    				selectNoColumns ();
    			}
    			else {
    				// Select all sequences
    				selectAllRows ();
    				selectAllColumns ();
    			}
            }
        }
        else if ( !e.isControlDown() && !e.isShiftDown() ) {
            // Click outside of the main box, clear everything unless shift or control is down 
            selectNoRows ();
            selectNoColumns ();
        }
		
		repaint();
	}
	
	private String [] theSequences = null;
	private String [] theSequenceLabels = null;
	private int [] theSeqStarts = null;
	private int [] theSeqStops = null;
	private boolean isDNA = true;
//	private Vector<String> description = null;
	
    MultilineTextPanel headerPanel;

    private boolean [] seqSelected = null;
    private boolean [] columnSelected = null;
	Rectangle2D selectionBox = new Rectangle2D.Double (0,0,0,0);
	private Color borderColor = Color.BLACK;
	
	// Attributes for where to draw
	private int nTopGap = 0;
	private int nBottomGap = 0;
	private int nRightGap = 0;
	private int nLeftGap = 0;
	private double dFrameLeftX = 0;
	private double dFrameTopY = 0;
	private double dFrameHeight = 0;
	private double dDivider1Y = 0;
	private double dRulerTop = 0;
	private double dRulerBottom = 0;
	
	private double [] seqTop = null;
	private double [] seqBottom = null;
	
	private double [] topSelect = null;
	private double [] bottomSelect = null;
	
	private double dHeaderHeight = 0;
	private double dRulerHeight = 0;
	private double dSequenceStart = 0;
	static private final int nInsetGap = 5;
	static private final int nRowHeight = 15;
}
