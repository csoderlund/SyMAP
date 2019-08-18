package util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import java.util.Vector;

import util.LinkLabel;

// mdb added 7/15/09 #166 - a URL-savvy graphical text box
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class TextBox extends JComponent  {
	private static final Color bgColor = Color.yellow;
	private static final int INSET = 5;
	private static final int startWidth = 600;
	private static final int startHeight = 200;
	private boolean bFullSize = true;
	private int trueWidth = 0;
	
	public TextBox(Vector<String> text, Font font, int x, int y, int wrapLen, int truncLen) {
		this(text.toArray(new String[0]), font, x, y, wrapLen, truncLen);
	}
	
	public TextBox(String text, Font font, int x, int y, int wrapLen, int truncLen) {
		this(text.split("\n"), font, x, y, wrapLen, truncLen);
	}

	// WMN 1/12/12 modified to wrap and trucate, with considerable duplicated code
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
				if (width > startWidth)
				{
					bFullSize = false;
					width = startWidth;
				}
			}
			else
			{
				if (line.length() <= wrapLen)
				{
					JLabel label = new JLabel(line);
				
					label.setLocation(tx, ty);
					label.setFont(font);
					label.setSize(label.getMinimumSize());
					add(label);
					
					ty += label.getHeight();
					width = Math.max(width, label.getWidth() + INSET*2);
					trueWidth = Math.max(trueWidth, label.getWidth() + INSET*2);
					if (width > startWidth)
					{
						bFullSize = false;
						width = startWidth;
					}
				}
				else
				{
					String[] words = line.split("\\s+");
					StringBuffer curLine = new StringBuffer();
					int totalLen = 0;
					for (int i = 0; i < words.length; i++)
					{
						curLine.append(" ");
						curLine.append(words[i]);
						totalLen += 1 + words[i].length();
						int curLen = curLine.length();
						if (curLen >= wrapLen || i == words.length - 1)
						{
							// done with this line, but add "..." if being truncated
							if (i < words.length - 1 && totalLen >= truncLen)
							{
								curLine.append("...");
							}
							if (curLine.length() > 1.25*wrapLen)
							{
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
							if (width > startWidth)
							{
								bFullSize = false;
								width = startWidth;
							}
							if (totalLen >= truncLen) break; // note, don't truncate until filling a line
							curLine = new StringBuffer();
						}
					}
				}
			}
		}
		if (false && !bFullSize)
		{
			JLabel label = new ExpandLabel("expand", this);
			label.setLocation(tx,ty);
			label.setFont(font);
			label.setSize(label.getMinimumSize());
			add(label);	
			ty += label.getHeight();
		}
		setSize(width, ty + INSET);
		setLocation(x, y);
	}
	
	public void paintComponent(Graphics g) {
		//super.paintComponent(g);
		
		Graphics2D g2 = (Graphics2D)g;
		int width = getWidth()-1;
		int height = getHeight()-1;
		
		// Draw rectangle
		g2.setColor(bgColor);
		g2.fillRect(0, 0, width, height);
		g2.setColor(Color.black);
		g2.drawRect(0, 0, width, height);
		
		paintComponents(g); // draw text
	}
	public void growShrink()
	{
		int curWidth = getWidth();
		int curHeight = getHeight();
		if (curWidth == trueWidth && trueWidth > startWidth)
		{
			setSize(startWidth,curHeight);	
		}
		else if (curWidth < trueWidth)
		{
			setSize(trueWidth,curHeight);
		}
		paintComponent(getGraphics());
	}
}
