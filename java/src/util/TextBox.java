package util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.Point;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import java.util.Vector;

import util.LinkLabel;

/******************************************************
 * Draws the annotation description box
 * They stay the same width regular of expand/shrink, instead turns on scroll bar
 */
// a URL-savvy graphical text box
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class TextBox extends JComponent  {
	private static final Color bgColor = new Color(255,255,153); // CAS503
	private static final int INSET = 5;
	private static final int startWidth = 600;
	private boolean bFullSize = true;
	private int trueWidth = 0;
	private String [] descText=null; // CAS503
	private Rectangle2D.Double rect = new Rectangle2D.Double(); // CAS503
	
	public TextBox(Vector<String> text, Font font, int x, int y, int wrapLen, int truncLen) {
		this(text.toArray(new String[0]), font, x, y, wrapLen, truncLen);
	}
	
	public TextBox(String text, Font font, int x, int y, int wrapLen, int truncLen) {
		this(text.split("\n"), font, x, y, wrapLen, truncLen);
	}

	private TextBox(String[] text, Font font, int x, int y,int wrapLen, int truncLen) {
		descText=text;
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
		
		setSize(width, ty + INSET);
		setLocation(x, y);
		rect.setRect(x, y, width, ty + INSET); // CAS503
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
	// CAS503 - add the following code to allow a popup of the description
	// this was added so that it can be copied, and because sometimes the box gets half hidden
	public boolean containsP(Point p) {return rect.contains(p);}
	public void popupDesc() {
		String msg = "";
		for (String x : descText) msg += x + "\n";
		displayInfoMonoSpace(this, "Description", msg, false);
	}
	//isModal=true means that everything is frozen until the window is closed
	private void displayInfoMonoSpace(Component parentFrame, String title, 
			String theMessage, boolean isModal) {
		JOptionPane pane = new JOptionPane();
		
		JTextArea messageArea = new JTextArea(theMessage);

		JScrollPane sPane = new JScrollPane(messageArea); 
		messageArea.setFont(new Font("monospaced", Font.BOLD, 12));
		messageArea.setEditable(false);
		messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		pane.setMessage(sPane);
		pane.setMessageType(JOptionPane.PLAIN_MESSAGE);

		JDialog helpDiag = pane.createDialog(parentFrame, title);
		helpDiag.setModal(isModal);
		helpDiag.setResizable(true);
		
		helpDiag.setVisible(true);		
	}
}
