package symap.sequence;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import java.util.Vector;
import javax.swing.JComponent;
import javax.swing.JLabel;

import util.LinkLabel;

/******************************************************
 * Draws the yellow annotation description box
 * They stay the same width regular of expand/shrink, instead turns on scroll bar
 * CAS544 moved from util to sequence - only used for yellow text box; removed rect because not using popup from here
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class TextBox extends JComponent  {
	private Color borderColor = Color.black;				// CAS554 added border color
	private final Color bgColor = new Color(240,240,240);   // CAS503, CAS554 was yellow, now light gray
	private float stroke = 3f;								// CAS554 added strong
	private final int INSET = 5;
	private final int startWidth = 600;
	private final int wrapLen=40, wrapMaxLen=50, wrapShortLen=10, maxLines=3; // CAS554 made final
	
	private int width=0, tx = INSET, ty = INSET;
	private Font theFont;
	
	// CAS548 Specific for yellow box called from Sequence; two lines input
	// greedy algorithm 
	public TextBox(Vector<String> lines, Font font, int x, int y, Color bgColor) {
		if (lines.size()!=3) return;
		
		theFont=font;
		this.borderColor = bgColor;
		
		setLabel(lines.get(0));
		if (lines.get(1).length()>1) setLabel(lines.get(1));
		String desc=lines.get(2);
		
		if (desc.length() <= wrapLen) {
			if (desc.length()>1) setLabel(desc);
		}
		else {
			int curLen=0, totLines=0;
			String[] words = desc.split("\\s+");
			
			StringBuffer curLine = new StringBuffer();
			
			for (int i = 0; i < words.length; i++) {
				curLen += words[i].length()+1;
				
				if (curLen>wrapLen) {
					boolean bNoWrap = curLine.length()< wrapShortLen && curLen<wrapMaxLen;
					boolean bNoWrap2 = words[i].length()<3 && (i==words.length-1);
					if (!bNoWrap && !bNoWrap2) {
						totLines++;
						if (totLines>=maxLines) {
							setLabel(curLine.toString() + "...");
							break;
						}
						setLabel(curLine.toString());
						curLen = words[i].length()+1;
						curLine = new StringBuffer();
					}
				}
				if (curLine.length()>0) curLine.append(" ");
				curLine.append(words[i]);
				if (i==words.length-1) setLabel(curLine.toString());
			}
		}
		setSize(width, ty + INSET);
		setLocation(x, y);
	}
	private void setLabel(String line) {
		if (line.length()>wrapLen) line = line.substring(0,wrapLen)+"..."; // CAS554 line with no break
		JLabel label = new JLabel(line);
		
		label.setLocation(tx, ty);
		label.setFont(theFont);
		label.setSize(label.getMinimumSize());
		add(label);
		
		ty += label.getHeight();
		width = Math.max(width, label.getWidth() + INSET*2);
		if (width > startWidth) width = startWidth;
	}
	protected double getLowY() { return getHeight() + getY();}	
	
	/// a URL-savvy graphical text box Previous; not used
	private int trueWidth=0;
	public TextBox(Vector<String> text, Font font, int x, int y, int wrapLen, int truncLen) {
		this(text.toArray(new String[0]), font, x, y, wrapLen, truncLen);
	}
	
	private TextBox(String[] text, Font font, int x, int y,int wrapLen, int truncLen) {
		int width = 0;
		int tx = INSET;
		int ty = INSET;
		
		for (String line : text) {
			int urlIndex = line.indexOf("=http://");
			if (urlIndex > -1) { // this line contains a URL
				String tag = line.substring(0, urlIndex);
				String url = line.substring(urlIndex+1);
				JLabel label = new LinkLabel(tag, Color.black, Color.red, url);
				label.setLocation(tx, ty);
				label.setFont(font);
				label.setSize(label.getMinimumSize());
				add(label);
				
				ty += label.getHeight();
				width = Math.max(width, label.getWidth() + INSET*2);
				trueWidth = Math.max(trueWidth, label.getWidth() + INSET*2);
				if (width > startWidth) width = startWidth;
			}
			else {
				if (line.length() <= wrapLen) {
					JLabel label = new JLabel(line);
				
					label.setLocation(tx, ty);
					label.setFont(font);
					label.setSize(label.getMinimumSize());
					add(label);
					
					ty += label.getHeight();
					width = Math.max(width, label.getWidth() + INSET*2);
					trueWidth = Math.max(trueWidth, label.getWidth() + INSET*2);
					if (width > startWidth) width = startWidth;
				}
				else {
					String[] words = line.split("\\s+");
					StringBuffer curLine = new StringBuffer();
					int totalLen = 0;
					for (int i = 0; i < words.length; i++) {
						curLine.append(words[i]+" ");
						
						totalLen += words[i].length()+1;
						int curLen = curLine.length();
						
						if (curLen >= wrapLen || i == words.length - 1) {
							
							if (i < words.length - 1 && totalLen >= truncLen) {// done with this line, but add "..." if being truncated
								curLine.append("...");
							}
							if (curLine.length() > 1.25*wrapLen) {
								curLine.delete((int)(1.25*wrapLen),curLine.length());
								curLine.append("...");
							}
							JLabel label = new JLabel(curLine.toString());
							
							label.setLocation(tx, ty);
							label.setFont(font);
							label.setSize(label.getMinimumSize());
							add(label);
							
							ty += label.getHeight();
							width = Math.max(width, label.getWidth() + INSET*2);
							trueWidth = Math.max(trueWidth, label.getWidth() + INSET*2);
							if (width > startWidth) width = startWidth;
							if (totalLen >= truncLen) break; // note, don't truncate until filling a line
							curLine = new StringBuffer();
						}
					}
				}
			}
		}
		setSize(width, ty + INSET);
		setLocation(x, y);
	}
	
	public void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D)g;
		int width = getWidth()-1;
		int height = getHeight()-1;
		
		// Draw rectangle
		g2.setColor(bgColor);
		g2.fillRect(0, 0, width, height);
		BasicStroke s = new BasicStroke(stroke);
		g2.setStroke(s);
		g2.setColor(borderColor);
		g2.drawRect(0, 0, width, height);
		
		paintComponents(g); // draw text
	}
}
