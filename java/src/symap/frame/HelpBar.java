package symap.frame;

import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import javax.swing.text.DefaultCaret;
import javax.swing.BorderFactory;

import colordialog.ColorListener;

/******************************************************************
 * Help Box on left side of Chromosome explorer and bottom of other displays
 */
public class HelpBar extends JPanel 
					 implements MouseListener, MouseMotionListener, ActionListener, ColorListener {
	private static final long serialVersionUID = 1L;
	private static final Font helpFont = new Font("Courier", 0, 12); 
	private static final Color helpColor = Color.black;
	private static final Color helpBackgroundColor = Color.white;
	private final String helpText = "Move the mouse over an object for further information.";
	
	private JTextArea helpLabel = null; 
	private Object currentHelpObj = null;
	
	private HashMap<Component,Object> comps;

	public HelpBar(int width, int height) {// Values -1, 17 if help is on the bottom
		super(false);

		comps = new HashMap<Component,Object>();

		setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
		
		if (width <= 0) width = Toolkit.getDefaultToolkit().getScreenSize().width;
		setPreferredSize(new Dimension(width,height));

		helpLabel = new JTextArea();
		helpLabel.setBorder( BorderFactory.createEmptyBorder(0, 5, 0, 5) );
		helpLabel.setLineWrap(true);
		helpLabel.setWrapStyleWord(true);
		helpLabel.setEditable(false);
		 ((DefaultCaret)helpLabel.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE); // disable auto-scroll (for scrollpane in SyMAPFrame3D)

		helpLabel.setOpaque(true);
		helpLabel.setBackground(helpBackgroundColor);
		helpLabel.setForeground(helpColor);
		helpLabel.setFont(helpFont);
		helpLabel.setMaximumSize(new Dimension(width*10,height*10));
		helpLabel.setMinimumSize(new Dimension(0,height));
			
		add(helpLabel);
	}
	public void setHelp(String help, Object obj) {
		if (help == null || help.equals("")) { 
			help = helpText; 
		}
		else {
			if (getSize().height < 50) {
				help = help.replaceAll("\n", "   ");
				help = help.replaceAll(":    ", ": ");
				help = help.replaceAll(":   ", ": ");
				help = help.replaceAll(":  ", ": "); 
			}
		}
		helpLabel.setText(help);
		currentHelpObj = obj;	
	}

	public void addHelpListener(Component comp) { 
		synchronized (comps) {
			comps.put(comp,comp);

			comp.addMouseListener(this);
			comp.addMouseMotionListener(this); 
		}
	}

	public void addHelpListener(Component comp, HelpListener listener) { 
		synchronized (comps) {
			comps.put(comp,listener);

			comp.addMouseListener(this);
			comp.addMouseMotionListener(this);
		}
	}

	public void removeHelpListener(Component comp) { 
		synchronized (comps) {
			comps.remove(comp);

			comp.removeMouseListener(this);
			comp.removeMouseMotionListener(this);
			if (currentHelpObj == comp)
				setHelp("",null);
		}
	}

	public void mouseMoved(MouseEvent e) {
		synchronized (comps) {
			Object obj = comps.get(e.getSource());
			if (obj != null) {
				if (obj instanceof HelpListener) {
					setHelp( ((HelpListener)obj).getHelpText(e), e.getSource() );
				}
				else if (obj instanceof JComponent) {
					setHelp( ((JComponent)obj).getToolTipText(e), e.getSource() );
				}
			}
			else if (e.getSource() instanceof JComponent) {
				setHelp( ((JComponent)e.getSource()).getToolTipText(e), e.getSource() );
			}
		}
	}

	public void mouseEntered(MouseEvent e) {
		mouseMoved(e);		
	}

	public void mouseExited(MouseEvent e) {
		if (e.getSource() == currentHelpObj) {
			setHelp("",null);
		}
	}
	
	public void mouseDragged(MouseEvent e) { }
	public void mouseClicked(MouseEvent e) { }
	public void mousePressed(MouseEvent e) { }
	public void mouseReleased(MouseEvent e) { }

	public void actionPerformed(ActionEvent e) {}
	public void resetColors() {
		if (helpLabel != null) {
			helpLabel.setBackground(helpBackgroundColor);
			helpLabel.setForeground(helpColor);
		}
	}
}
