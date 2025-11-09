package symap.closeup;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.font.TextLayout;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import javax.swing.JPanel;

/**************************************
 * SyMAP query multiple alignment panel; this is the top part of AlignMainPanel, where the key table is the bottom
 **************************************/
public class MsaPanel extends JPanel {
	private static final long serialVersionUID = 8622662337486159085L;
	
	protected static final int GRAPHICMODE = 1, TEXTMODE = 0;
	private static final Font DEFAULT_FONT = new Font("Monospaced", Font.PLAIN, 11);
	private static final char gap=MsaRun.gapOut;
	private static final int nTopGap = 10, nBottomGap = 10, nRightGap = 10, nLeftGap = 10;
	private static final int nInsetGap = 5, nRowHeight = 15;

	protected MsaPanel(MsaRun alignData) {
		setBackground(Color.white);
		setBorderColor(Color.BLACK);
		setAlignmentY(Component.LEFT_ALIGNMENT);
		setLayout(null);
		setPreferredSize(new Dimension(0,100));  
		
		isDNA=true; // No AA
		theFont = DEFAULT_FONT;
		fontMetrics = getFontMetrics(DEFAULT_FONT);
		dFontAscent = fontMetrics.getAscent();
		dFontMinHeight = fontMetrics.getAscent() + fontMetrics.getDescent() + fontMetrics.getLeading();
		dFontCellWidth = fontMetrics.stringWidth("A");
		dRulerHeight = getTextWidth("999999");
		
		seqSeqs 	= alignData.getSeqs();
		seqNames 	= alignData.getNames();
		consensus   = seqSeqs[0].toUpperCase(); // seqSeqs[0] is lower for mismatch; need this to compare all upper
		
		// Compute where name stop and seq start for each seq; compute longest seq; 
		seqStarts 	= new int[alignData.getNumSeqs()];
		seqStops 	= new int[alignData.getNumSeqs()];
		seqSelected = new boolean[alignData.getNumSeqs()];
		
		dSeqStartf = 0.0;	// set here, not changed again
		nMaxIndexf = 0;
		for(int x=0; x<seqSeqs.length; x++) { 
			for (seqStarts[x]=0; 
					seqStarts[x]<seqSeqs[x].length() && seqSeqs[x].charAt(seqStarts[x])==gap; 
					seqStarts[x]++);
			for(seqStops[x]=seqSeqs[x].length()-1; 
					seqStops[x]>=0 && seqSeqs[x].charAt(seqStops[x])==gap; 
					seqStops[x]--);
			
			dSeqStartf = Math.max(dSeqStartf, getTextWidth(seqNames[x]));
			nMaxIndexf = Math.max(nMaxIndexf, seqSeqs[x].length());
			seqSelected[x] = false;
		}
		columnSelected = new boolean[nMaxIndexf];
		for(int x=0; x<columnSelected.length; x++) columnSelected[x] = false;

		dSeqStartf += nInsetGap * 2.0d + nLeftGap;
		dMinPixelX = dSeqStartf;

		dFrameHeight = nInsetGap * ((double)seqSeqs.length) + nRowHeight * ((double)seqSeqs.length) + // Sequences
					 + nInsetGap * ((double)seqSeqs.length) + dRulerHeight; 		// Ruler
		dDivider1Y = nTopGap + dHeaderHeight;
		dRulerTop = dDivider1Y + nInsetGap;
		dRulerBottom = dRulerTop + dRulerHeight;
		
		seqTop = new double[seqSeqs.length];
		seqBottom = new double[seqSeqs.length];
		topSelect = new double[seqSeqs.length];
		bottomSelect = new double[seqSeqs.length];
		
		double top = dRulerBottom + nInsetGap;
		for(int x=0; x < seqSeqs.length; x++) {
			seqTop[x] = top;
			seqBottom[x] = seqTop[x] + nRowHeight;
			
			topSelect[x] = seqTop[x] - nInsetGap / 2.0;
			bottomSelect[x] = seqBottom[x] + nInsetGap / 2.0;
			
			top = seqBottom[x] + nInsetGap;
		}
	}
	
	//---------------------------------JPanel Over-rides-------------------------------------//
	public void paintComponent(Graphics g){
		Graphics2D g2 = (Graphics2D)g;
		super.paintComponent(g);
		
		// Draw the outside frame and divide
		Rectangle2D outsideBox = new Rectangle2D.Double(nLeftGap, nTopGap, getFrameWidth(), dFrameHeight);
		
		if (nDrawMode == TEXTMODE) {
			double width = getTextWidth("?");

			g2.setColor(selectColor);
			for(int x=0; x<columnSelected.length; x++) {
				if (columnSelected[x]) {
					double pos = calcWriteX(x);
					
					g2.fill(new Rectangle2D.Double(pos, nTopGap, width, dFrameHeight));
				}
			}	
		}
		if (hasSelection()){
			g2.setColor(selectColor);// Outline whole thing
			
			for(int x=0; x<seqSelected.length; x++) {
				if (seqSelected[x]) {
					g2.fill(new Rectangle2D.Double(nLeftGap, topSelect[x], getFrameRight()-nLeftGap, bottomSelect[x]-topSelect[x]));
				}
			}
			g2.setColor(Color.BLACK);
		}
			
		g2.setColor(borderColor);
		g2.draw(outsideBox);
		g2.draw(new Line2D.Double(nLeftGap, dDivider1Y, getFrameRight(), dDivider1Y));
		
		drawRuler(g2, 0, dRulerTop, dRulerBottom);

		for (int x=0; x<seqSeqs.length; x++) {
			drawText(g2, seqNames[x], nLeftGap + nInsetGap, seqTop[x]);
			drawSequence(g2, seqNames[x], seqSeqs[x], seqTop[x], seqBottom[x]);
		}
		selectionBox = new Rectangle2D.Double(nLeftGap, nTopGap, getFrameWidth(), dFrameHeight);
	}
	
	public Dimension getMaximumSize(){
		return new Dimension(Integer.MAX_VALUE,(int)getPreferredSize().getHeight());
	}
	
	public Dimension getPreferredSize(){
		int nWidth  = nLeftGap + (int)getFrameWidth() + nRightGap;
		int nHeight = nTopGap + (int)dFrameHeight + nBottomGap;
		return new Dimension(nWidth, nHeight); 
	}
	/*----------------------------------------------------------*/
	/** Called from MsaMainPanel **/
	protected void setBasesPerPixel(int n) throws Exception { // when zoom size changes
		nBasesPerPixel = n; 
		
		repaintPanel();
	}
	protected void handleClick(MouseEvent e, Point pt){
		boolean highlightChanged = false;
        if (selectionBox.contains(pt)){     //  Determine the column for the click
    		if (nDrawMode == TEXTMODE) {
    			int xPos = calcIndexFromWriteX(pt.x);
    			if (xPos >= 0 && xPos < columnSelected.length) {
    				highlightChanged = true;
        			if (!e.isControlDown() && !e.isShiftDown()) selectNoColumns();
    				columnSelected[xPos] = !columnSelected[xPos];
    			}
    		}
            // Determine the row for the click.
        	if (topSelect[0] <= pt.y && pt.y <= bottomSelect[bottomSelect.length-1]){
        		highlightChanged = true;
    			if (!e.isControlDown() && !e.isShiftDown()) selectNoRows();
    			
    			for(int x=0; x<topSelect.length; x++) {
        			if (topSelect[x] <= pt.y && pt.y <= bottomSelect[x])
        				seqSelected[x] = !seqSelected[x];    				
    			}
    		}
    		if (!highlightChanged) {// Click is inside the box, but not within any of the sequences (i.e. in the header)
    			if ((e.isControlDown() || e.isShiftDown()) && hasSelection()) {
    				selectNoRows ();
    				selectNoColumns();
    			}
    			else {// Select all sequences
    				selectAllRows();
    				selectAllColumns();
    			}
            }
        }
        else if (!e.isControlDown() && !e.isShiftDown()) { // Click outside of main box
            selectNoRows();
            selectNoColumns();
        }
		repaint();
	}
	protected void setBorderColor(Color newColor) {
		borderColor = newColor;
	}
	protected void selectNoRows() {	
		for(int x=0; x<seqSelected.length; x++) seqSelected[x] = false;
		repaint();
	}
	protected void selectNoColumns() {
		for(int x=0; x<columnSelected.length; x++)
			columnSelected[x] = false;
		repaint();
	}
	protected void setDrawMode(int mode) {
		nDrawMode = mode;
		dMinPixelX = dSeqStartf;
		repaintPanel();
	}
	protected void changeDraw(){
		if (nDrawMode == GRAPHICMODE) nDrawMode = TEXTMODE;
    	else nDrawMode = GRAPHICMODE;
		dMinPixelX = dSeqStartf;
  		repaintPanel();
  	}
	protected double getSequenceWidth(){
		if (nDrawMode == GRAPHICMODE) return getDrawWidth();
		else return getWriteWidth();
	}
	
	/** From old deleted AlignPanelBase **/
	private void drawRuler(Graphics2D g2, double dXMin, double dYTop, double dYBottom)  {
		// Center the text (as much as possible in the input box)
		TextLayout layout = new TextLayout("9999", theFont, g2.getFontRenderContext());		
		double dY = (dYBottom + dYTop) / 2 + layout.getBounds().getWidth() / 2;
		
		int nTickIncrement = 100;
		if (nDrawMode != GRAPHICMODE)
			nTickIncrement = 10;
		int nTickStart = nTickIncrement; // (nMinIndex / nTickIncrement) * nTickIncrement;		
		if (nTickStart < 0)
			nTickStart += nTickIncrement;
		
		for (int i = nTickStart; i < nMaxIndexf; i += nTickIncrement){	
			double dX;
			if (nDrawMode != GRAPHICMODE)
				dX = (calcWriteX(i) + calcWriteX(i+1)) / 2;
			else
				dX = calcDrawX(i);
			
			if (dX > dXMin)
				drawVerticalText(g2, String.valueOf(i), dX, dY);
		}
	}
	private void drawText(Graphics2D g2, String str, double dXLeft, double dYTop, Color theColor){
		if (str.length()==0) return;
		
		g2.setColor(theColor);
		
		TextLayout layout = new TextLayout(str, theFont, g2.getFontRenderContext());
		float fBaseline = (float)(dYTop + dFontAscent); 
		layout.draw(g2, (float)dXLeft, fBaseline);
	}
	
	private void drawText(Graphics2D g2, String str, double dX, double dY){
		drawText(g2, str, dX, dY, Color.BLACK);
	}
	// Positioned by the center of the left side
	private void drawVerticalText(Graphics2D g2, String str, double dX, double dY){
		if (str.length() == 0) return;
		
		TextLayout layout = new TextLayout(str, theFont, g2.getFontRenderContext());
		g2.rotate(- Math.PI / 2.0);
		g2.setColor(Color.BLACK);
		float fHalf = (float)(layout.getBounds().getHeight() / 2.0f);
		layout.draw(g2, (float)-dY, (float)dX + fHalf);	
		g2.rotate(Math.PI / 2.0);
	}	
	/****************************************************************************
	 * Draw/write sequences
	 */
	private void drawSequence(Graphics2D g2, String label, String sequence, double dYTop, double dYBottom){
		if (nDrawMode == GRAPHICMODE)
			drawSeqLine(g2, label, sequence, dYTop, dYBottom);
		else
			writeSeqLetters(g2, label, sequence, dYTop, dYBottom);
	}
	private void writeSeqLetters(Graphics2D g2,String label,String seq, double dYTop, double dYBottom) {
		double dWriteBaseline = (dYBottom - dYTop) / 2 - dFontMinHeight / 2 + dYTop + dFontAscent; 

		g2.setColor(Color.black);
			
		// Draw the columns of bases that are currently visible 
        int nStart = 0;
        int nEnd = seq.length();
        
		for(int i = nStart; i < nEnd; i++){		
			double dX = calcWriteX(i);
			
			Color baseColor = Color.black;
			if (isDNA) {
				if (getIsNAt(seq, i))				baseColor = unkPurple;  
				else if (getIsGapAt(seq, i)) 		baseColor = darkGreen;
				else if (getIsLowQuality(seq, i))	baseColor = lowQuality;
				else if (getIsMismatchAt(seq, i))	baseColor = mismatchRed;
			}
			else {
				if (getIsStop(seq, i))				baseColor = Color.gray;
				else if (getIsNAt(seq, i))			baseColor = mediumGray;  
				else if (getIsSubAt(seq, i)) 		baseColor = purple;
				else if (getIsGapAt(seq, i)) 		baseColor = darkGreen;
				else if (getIsMismatchAt(seq, i))	baseColor = mismatchRed;
			}
			drawCenteredBase(g2, seq.charAt(i), baseColor, dX, dWriteBaseline);
		}
	}
	private void drawSeqLine(Graphics2D g2,String label,String seq,double dYTop, double dYBottom){
		//Find the locations of the start and stop 
		int startGapEnd = 0;
		int endGapStart = seq.length();
		while (startGapEnd<endGapStart && seq.charAt(startGapEnd) == gap) startGapEnd++; 
		while (endGapStart>0 && seq.charAt(endGapStart-1) == gap) endGapStart--;		 
		
		// Determine the position of the sequence line, but don't draw until after the hashes
		double dXPosStart = calcDrawX(startGapEnd);
		double dXPosEnd = calcDrawX(endGapStart);

		double dHeight = dYBottom - dYTop;
		double dYCenter = dHeight / 2.0 + dYTop;

		int RED_HASH = (int)(dHeight/2) - 2;
		int GREEN_HASH = RED_HASH - 1;
		int GRAY_HASH = GREEN_HASH - 2;
		int BLUE_HASH = GREEN_HASH - 2;
		
		// Draw column at at time
		for(int i = startGapEnd; i < endGapStart;){
			double dHashXPos = calcDrawX(i);
		
			boolean bGreenHash=false, bRedHash=false, bGreyHash=false;
			boolean bBlueStopHash=false, bBlueHash=false, bPurpleHash=false;
			
			// multiple bases are represented together; if any true, then it gets the hash
			for (int j=0; i<seq.length() && j < nBasesPerPixel; ++i, ++j){			
				if (isDNA) { // order is important
					if (getIsGapAt(seq, i))				bGreenHash = true;
					else if (getIsNAt(seq, i))			bPurpleHash = true;  		// n's and x's
					else if (getIsLowQuality(seq, i))	bBlueHash = true;
					else if (getIsMismatchAt(seq, i))	bRedHash = true;
				}
				else { // if start using for AA, then check this, may be wrong
					if (getIsEndGapAt(seq, i))			bGreyHash = true;
					else if (getIsNAt(seq, i))			bGreyHash = true;  		
					else if (getIsSubAt(seq, i))		bPurpleHash = true;       
					else if (getIsGapAt(seq, i))		bGreenHash = true;
					else if (getIsMismatchAt(seq, i))	bRedHash = true;
					else if (getIsLowQuality(seq, i)) 	bBlueHash = true;
					if (getIsStop(seq, i))				bBlueStopHash = true;
				}
			}	
			if (bGreenHash)// little smaller up down - gap
				drawHash(g2, dHashXPos, dYCenter - GREEN_HASH, dYCenter + GREEN_HASH, gapGreen);// both
			else if (bRedHash) // larger up down - mismatch
				drawHash(g2, dHashXPos, dYCenter - RED_HASH, dYCenter + RED_HASH, mismatchRed);	// sequence
			else if (bPurpleHash) // smaller up and down - N's or x's
				drawHash(g2, dHashXPos, dYCenter - RED_HASH, dYCenter + RED_HASH, unkPurple);	// consensus
			else if (bBlueHash)
				drawHash(g2, dHashXPos, dYCenter - BLUE_HASH, dYCenter + BLUE_HASH, lowQuality);// consensus
			
			if (!isDNA) {
				if (bBlueStopHash) { // little T above sequence
					drawHash(g2, dHashXPos, dYTop, dYTop + 2, mediumGray);	
					g2.draw(new Line2D.Double(dHashXPos - 1, dYTop, dHashXPos + 1, dYTop));
				}
				if (bGreyHash)
					drawHash(g2, dHashXPos, dYCenter - GRAY_HASH, dYCenter + GRAY_HASH, mediumGray);
				else if (bPurpleHash)  // little smaller up down - substitute
					drawHash(g2, dHashXPos, dYCenter - BLUE_HASH, dYCenter + BLUE_HASH, purple);
			}
		}
		
		// Draw the line for the sequence
		g2.setColor(Color.black);
		g2.draw(new Line2D.Double(dXPosStart, dYCenter, dXPosEnd, dYCenter));
		
		// Draw the arrow head
		double dArrowHeight = dHeight / 2.0 - 2;
		drawArrowHead(g2, dXPosEnd, dYCenter, dArrowHeight, true /* right */);
	
		g2.setColor(Color.black);
	}
	/*---  For graphics ------------*/
	private void drawHash(Graphics2D g2, double dHashXPos, double dHashTopY, double dHashBottomY, Color hashColor){
		g2.setColor(hashColor);
		g2.draw(new Line2D.Double(dHashXPos,  dHashTopY, dHashXPos, dHashBottomY));
	}
	private boolean getIsGapAt(String seq, int nPos) {// only green on gap strand
		if (seq.charAt(nPos) == gap) return true;
		return false;
	}
	private boolean getIsMismatchAt(String seq, int nPos) { 	
		char x0 = consensus.charAt(nPos);
		if (x0==gap) return false;
		
		char x1 = seq.charAt(nPos);
		return x1 != x0; 
	}
	private boolean getIsNAt(String seq, int nPos) {// ambiguous
		char c = seq.charAt(nPos);
		if (isDNA && (c == 'N' || c == 'n')) return true;
		if (c == 'X' || c == 'x') return true;
		return false;
	}
	private boolean getIsLowQuality(String seq, int nPos) {
		return (Character.isLowerCase(seq.charAt(nPos)));
	}
	private boolean getIsStop(String seq, int nPos) {   
		if (isDNA) return false;
		if (seq.charAt(nPos) == '*') return true;
		return false;
	}
	private boolean getIsEndGapAt(String seq, int nPos) {
		if (nPos < seqStarts[0] || nPos >seqStops[0]) return true;
		return false;
	}
	
	private boolean getIsSubAt(String seq, int nPos)  {       // sub both strands
		return !isDNA && seqSeqs[0].charAt(nPos) != seq.charAt(nPos)
				&& MsaRun.isCommonAcidSub(seqSeqs[0].charAt(nPos), seq.charAt(nPos)); 
	}
	/*---  For letters ------------*/
	private void drawCenteredBase(Graphics2D g2, char chBase, Color theColor, double dCellLeft, double dBaseline){
		g2.setColor(theColor);
		
		String str = "" + chBase;
		TextLayout layout = new TextLayout(str, theFont, g2.getFontRenderContext());
	   	Rectangle2D bounds = layout.getBounds();
		float fCharWidth = (float)bounds.getWidth();
		
		// Adjust x draw position to center the character in its rectangle
		float fX = (float)dCellLeft + (float)(dFontCellWidth - 1.0f) / 2.0f - fCharWidth / 2.0f; 
		   
		layout.draw(g2, fX, (float)dBaseline);

		g2.setColor(Color.black);		
	}
	private void drawArrowHead(Graphics2D g2, double dArrowPntX, double dArrowPntY, double dHeight, boolean bRight){
		final double ARROW_WIDTH = dHeight - 1;
		
		double dYStartTop = dArrowPntY - dHeight;
		double dYStartBottom = dArrowPntY + dHeight;
		double dXStart = dArrowPntX;
		
		if (bRight) 	dXStart -= ARROW_WIDTH;
		else			dXStart += ARROW_WIDTH;

		g2.draw(new Line2D.Double(dXStart, dYStartTop, dArrowPntX, dArrowPntY));
		g2.draw(new Line2D.Double(dXStart - 1, dYStartTop, dArrowPntX - 1, dArrowPntY));
		g2.draw(new Line2D.Double(dXStart, dYStartBottom, dArrowPntX, dArrowPntY));
		g2.draw(new Line2D.Double(dXStart - 1, dYStartBottom, dArrowPntX - 1, dArrowPntY));
	}
	/********************************************************************/
	private double getFrameWidth(){
		double dWidth = dSeqStartf + getSequenceWidth() + nInsetGap;
		return Math.max(700, dWidth);
	}
	private double getFrameRight() {
		return nLeftGap + getFrameWidth();
	}
	private void selectAllRows() { 
		for(int x=0; x<seqSelected.length; x++) seqSelected[x] = true;
		repaint(); 
	}
	private void selectAllColumns() {
		for(int x=0; x<columnSelected.length; x++)
			columnSelected[x] = true;
		repaint();
	}
	private boolean hasSelection() {
		boolean retVal = false;
		for(int x=0; x<seqSelected.length && !retVal; x++)
			retVal = seqSelected[x];
		return retVal;
	}
	// Returns the pixel position for the left hand side of the "box" to write in
	private double calcWriteX(int nBasePos) {
		return dMinPixelX + nBasePos * dFontCellWidth;
	}		
	private int calcIndexFromWriteX(double dX) { 
		return (int)((dX - dMinPixelX) / dFontCellWidth); 
	}
	private double calcDrawX(int nBasePos) { 
		return dMinPixelX + (nBasePos + 1) / nBasesPerPixel;
	}
	
		
	private double getTextWidth(String str){
		if (str == null) return 0;
		else return fontMetrics.stringWidth(str);
	}
	private double getDrawWidth(){
		return (nMaxIndexf + 1) / nBasesPerPixel;
	}
	private double getWriteWidth(){
		return dFontCellWidth * (nMaxIndexf + 1);
	}
	private void repaintPanel(){
		super.repaint();
	}
	/**** Original locals ****/
	private String [] seqSeqs = null;
	private String [] seqNames = null;
	private int [] seqStarts = null;
	private int [] seqStops = null;
	private String consensus = null;
	
	private boolean isDNA = true;
	
    private boolean [] seqSelected = null;
    private boolean [] columnSelected = null;
	private Rectangle2D selectionBox = new Rectangle2D.Double(0,0,0,0);
	private Color borderColor = Color.BLACK;
	
	// Attributes for where to draw
	private double dFrameHeight = 0;
	private double dDivider1Y = 0, dRulerTop = 0, dRulerBottom = 0;
	
	private double [] seqTop = null;
	private double [] seqBottom = null;
	
	private double [] topSelect = null;
	private double [] bottomSelect = null;
	
	private double dHeaderHeight = 0, dRulerHeight = 0, dSeqStartf = 0;
	
	
	/** From AlignPanelBase **/
	private FontMetrics fontMetrics = null;
	private Font theFont = null;
	
	// Attibutes for positioning
	private double dMinPixelX = 0;		// The lowest pixel (associated base at min index) 
	private int nMaxIndexf = 1;			
	private double dFontMinHeight = 0;  // The minimum sized box a letter will fit in based on our font
	private double dFontCellWidth = 0;	
	private double dFontAscent = 0;		// The distance from the top of the write cell to the baseline
	private int nBasesPerPixel = 3;			

	private int nDrawMode = GRAPHICMODE;      
	
    // Color constants 
	private static final Color selectColor = new Color(180, 180, 180); // medium gray
	
	private static final Color mismatchRed = Color.red;
	private static final Color lowQuality = new Color(0,102,0); //new Color(125, 183, 213);	blue, can't see if high
	private static final Color gapGreen = new Color(0,200,0);
	private static final Color unkPurple = new Color(138, 0, 184);
	
	private static final Color darkGreen = new Color(0,102,0);
	private static final Color mediumGray =  new Color(180, 180, 180);
	private static final Color purple = new Color(138, 0, 184); 
}

