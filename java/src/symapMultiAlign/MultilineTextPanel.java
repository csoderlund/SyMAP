package symapMultiAlign;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Vector;

import javax.swing.JLabel;
import javax.swing.JPanel;

public class MultilineTextPanel extends JPanel
{
    public MultilineTextPanel ( Font inFont, Vector<String> inTextLines, int nInInset, int nInWidth, int nMinColumns ) throws Exception
    {
        FontMetrics metrics = getFontMetrics( inFont );
        setLayout ( null );
        
        nWidth = nInWidth;
        nInset = nInInset;
        textLines = new String [ inTextLines.size() ];
        htmlLines = new URL [ inTextLines.size() ];
        nColumnSpace = nInInset * 3;
        int nMaxColumnWidth = ( nWidth - nInset * 2 + nColumnSpace ) / nMinColumns - nColumnSpace;
        
        
        // Go through the list of lines to
        // 1) Determine the column width
        // 2) Parse any links off of the input text lines
        nColumnWidth = 0;
        for ( int i = 0; i < inTextLines.size(); ++i )
        {
        	// Initialize both arrays
        	textLines [i] = null;
        	htmlLines [i] = null;
        	
        	// Get the current string and parse it
            String str = (String)inTextLines.get(i);
            if ( str == null || str.length() == 0 )
            	continue;
            String [] strList = str.split( "\n" );
            textLines [i] = strList[0];
            if ( strList.length > 1 )
            	htmlLines [i] = new URL ( strList[1] );
            
            // Update the column width
            nColumnWidth = Math.max( metrics.stringWidth( textLines [i] ), nColumnWidth );
            nColumnWidth = Math.min( nColumnWidth, nMaxColumnWidth );
        }
        
        // force single column layout
        nRows = textLines.length;
        nColumns = 1;
        
        // Determine the height        
        nRowHeight = metrics.getHeight();     
        nHeight = nRows * nRowHeight + 2 * nInset;
        
        // Now that we know the number of rows and columns, lay out the control
        for ( int i = 0; i < nColumns; ++i )
            for ( int j = 0; j < nRows; ++j )
            {
                float fX = nInset + i * (nColumnWidth + nColumnSpace);
                float fY = nInset + j * nRowHeight;
                
                int nIdx = j + (i * nRows);
                if ( nIdx >= textLines.length )
                    break;
                
                if ( textLines [nIdx] == null || textLines[nIdx].length() == 0 )
                	continue;
                    
                // Create a lable to display the text and handle the link/tool-tip
                JLabel label = createLabel ( textLines [nIdx], htmlLines[nIdx] );
                label.setFont( inFont );
                label.setLocation( (int)fX, (int)fY );
                Dimension size = new Dimension ( nColumnWidth, nRowHeight );
                label.setSize( size );
                label.setPreferredSize( size );
                add ( label );
            }
        
        // Set the boundaries of the base class panel
        Dimension dim =  new Dimension ( nWidth, nHeight );
        setSize ( dim );
        setPreferredSize( dim );
    }
    
    private JLabel createLabel ( String strText, final URL theLink )
    { 	
    	JLabel label = null;
    	
    	if ( theLink != null )
    	{
    		final Color linkColor = Color.BLUE;
    		
    		label = new JLabel ( strText )
    		{
    			// Convoluted work-around to make Java underline text...
		    	public void paint(Graphics g)
		        {
		            super.paint(g);
	     
	                 // really all this size stuff below only needs to be recalculated if font or text changes
	                Rectangle2D textBounds =  getFontMetrics(getFont()).getStringBounds(getText(), g);
	                
	                 //this layout stuff assumes the icon is to the left, or null
	                int y = getHeight()/2 + (int)(textBounds.getHeight()/2);
	                int w = (int)textBounds.getWidth();
	                int x = (getIcon()==null ? 0 : getIcon().getIconWidth() + getIconTextGap()); 
	     
	                g.setColor(linkColor);
	                g.drawLine(0, y, x + w, y);
		        }
		    	
		        private static final long serialVersionUID = 1;
    		};
    		
    		label.addMouseListener
    		(	new MouseListener ()
    			{
    				public void mouseClicked(MouseEvent e)
    				{
    					// Opens the link
    					tryOpenURL( theLink );
    				}

    				public void mouseEntered(MouseEvent e)
    				{
			    		// Changes the cursor to a hand if a valid link was passed into the constructor
			    	    setCursor( new Cursor(Cursor.HAND_CURSOR) );
    				}
    		
    				public void mouseExited(MouseEvent e)
    				{
		    			// Change back the cursor
		    			setCursor( new Cursor(Cursor.DEFAULT_CURSOR) );
    				}
    				
    				public void	mousePressed(MouseEvent e) { };
    	
    				public void	mouseReleased(MouseEvent e) { };

    			}
    		);
    		label.setForeground( linkColor );
    	}
    	else
    	{
    		label = new JLabel ( strText );
    	}
    	
    	// Set a tool-tip in case the text is too large
    	label.setToolTipText( strText );
    	
    	return label;
    }
    
	public static boolean tryOpenURL ( URL theLink )
    {
    	String osName = System.getProperty("os.name"); 
    	try 
    	{ 
    		if (osName.startsWith("Mac OS")) 
    		{ 
    			Class<?> fileMgr = Class.forName("com.apple.eio.FileManager"); 
    			Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[] {String.class}); 
    			openURL.invoke(null, new Object[] {theLink}); 
    			return true;
    		} 
    		else if (osName.startsWith("Windows")) 
    		{
    			Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + theLink); 
    			return true;
    		}
    		else 
    		{ 
    			//assume Unix or Linux 
    			String[] browsers = { "firefox", "opera", "konqueror", "epiphany", "mozilla", "netscape" }; 
    			String browser = null; 
    			for (int count = 0; count < browsers.length && browser == null; count++) 
    				if (Runtime.getRuntime().exec( new String[] {"which", browsers[count]}).waitFor() == 0) 
    					browser = browsers[count]; 
    			if (browser == null) 
    				return false;
    			else 
    			{
    				Runtime.getRuntime().exec(new String[] {browser, theLink.toString()});
    				return true;
    			}
    		}
    	}
    	catch (Exception e) 
    	{ 	
    		e.toString();
    	}
    	
		return false;
    }

    
    private String textLines [] = null;
    private URL htmlLines [] = null;
    private int nColumnSpace;
    private int nRowHeight;
    private int nColumnWidth;
    private int nColumns;
    private int nRows;
    private int nWidth;
    private int nHeight;
    private int nInset;    
    
    private static final long serialVersionUID = 1;

}
