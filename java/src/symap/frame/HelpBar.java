package symap.frame;

import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.HashMap;
import java.util.Vector;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.text.DefaultCaret;
import javax.swing.BorderFactory;

import colordialog.ColorListener;
import util.CircleObj;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class HelpBar extends JPanel 
					 implements MouseListener, MouseMotionListener, ActionListener, ColorListener 
{
	public static final int HIGH_PRIORITY = 5;
	public static final int MEDIUM_PRIORITY = 3;
	public static final int LOW_PRIORITY = 1;
	public static final int NO_PRIORITY = 0;

	private static final int LOWER_TIME = 0;
	private static final int SAME_TIME = 1;
	private static final int HIGHER_TIME = 2;
	private static final int DEAD_TIME = 3;
	private static final int[][] TIMES =
	{ {   0,    0,    0,  5000},
		{1000, 2000, 1000,  5000},
		{3000, 2000, 1000,  7500},
		{4000, 2000, 1000, 10000},
		{5000, 3000, 2000, 12500},
		{6000, 4000, 3000, 15000} };

	private static final int STATUS_TIME_CHECK = 500;
	private static final int STATUS_WIDTH = 200;

// mdb removed 1/28/09 #159 simplify properties	
//	private static int height;
//	private static Font helpFont;
//	public static Color helpColor;
//	public static Color helpBackgroundColor;
//	private static Font statusFont;
//	public static Color statusColor;
//	public static Color statusBackgroundColor;
//	public static Color busyColor;
//	public static Color blinkColor;
//	public static Color pausedColor;
//	public static Color interactiveColor;
//	public static Color circleBackgroundColor;
//	static {
//		PropertiesReader props = new PropertiesReader(SyMAP.HELPBAR_PROPS);
//		height = (int)props.getDouble("height");
//		helpFont = props.getFont("helpFont");
//		helpColor = props.getColor("helpColor");
//		helpBackgroundColor = props.getColor("helpBackgroundColor");
//		statusFont = props.getFont("statusFont");
//		statusColor = props.getColor("statusColor");
//		statusBackgroundColor = props.getColor("statusBackgroundColor");
//		busyColor = props.getColor("busyColor");
//		blinkColor = props.getColor("blinkColor");
//		pausedColor = props.getColor("pausedColor");
//		interactiveColor = props.getColor("interactiveColor");
//		circleBackgroundColor = props.getColor("circleBackgroundColor");
//	}
	
	// mdb added 1/28/09 #159 simplify properties
	//private static final int height = 17; // mdb removed 2/13/09
	private static final Font helpFont = new Font("Ariel", 0, 14);
	private static final Color helpColor = Color.black;
	private static final Color helpBackgroundColor = Color.white;
	private static final Font statusFont = helpFont;
	private static final Color statusColor = Color.black;
	private static final Color statusBackgroundColor = Color.white;
	private static final Color busyColor = Color.red;
	private static final Color blinkColor = Color.yellow;
	private static final Color pausedColor = Color.yellow;
	private static final Color interactiveColor = Color.green;
	private static final Color circleBackgroundColor = Color.white;
	
	//private JLabel helpLabel = null; // mdb removed 2/13/09
	private JTextArea helpLabel = null; // mdb added 2/19/09 for 3D
	private Object currentHelp = null;
	private JLabel statusLabel = null;
	private CircleStatus circleStatus = null;
	private Vector<StatusMessage> statusMessages = null;
	private Timer statusTimer = null;
	private JSeparator sep1 = null, sep2 = null;

	private HashMap<Component,Object> comps;

	public HelpBar(int width, int height, boolean hasHelpText, 
			boolean hasStatusText, boolean hasStatusIndicator) 
	{
		super(false);

		comps = new HashMap<Component,Object>();

		setLayout(new BoxLayout(this,BoxLayout.X_AXIS));
		
		if (width <= 0)
			width = Toolkit.getDefaultToolkit().getScreenSize().width;
		setPreferredSize(new Dimension(width,height));

		if (hasHelpText) {
			//helpLabel = new HelpBarLabel(); // mdb removed 2/13/09 for 3D	
			
			// mdb added 2/13/09 for 3D
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

		if (hasStatusText) {
			statusMessages = new Vector<StatusMessage>();
			statusLabel = new JLabel();
			statusLabel.setOpaque(true);
			statusLabel.setBackground(statusBackgroundColor);
			statusLabel.setForeground(statusColor);
			statusLabel.setFont(statusFont);
			statusLabel.setPreferredSize(new Dimension(STATUS_WIDTH,height));
			statusLabel.setMaximumSize(new Dimension(STATUS_WIDTH,height*10));
			statusLabel.setMinimumSize(new Dimension(STATUS_WIDTH,height));
			statusTimer = new Timer(STATUS_TIME_CHECK,this);
			if (hasHelpText) {
				sep1 = createSeparator();
				sep1.setBackground(helpBackgroundColor);
				add(sep1);
			}
			add(statusLabel);
		}

		if (hasStatusIndicator) {
			circleStatus = new CircleStatus(height);
			if (hasHelpText || hasStatusText) {
				sep2 = createSeparator();
				sep2.setBackground(statusBackgroundColor);
				add(sep2);
			}
			add(circleStatus);
		}

		if (statusTimer != null)
			statusTimer.start();
	}

	public void resetColors() {
		if (helpLabel != null) {
			helpLabel.setBackground(helpBackgroundColor);
			helpLabel.setForeground(helpColor);
		}
		if (statusLabel != null) {
			statusLabel.setBackground(statusBackgroundColor);
			statusLabel.setForeground(statusColor);
		}
		if (sep1 != null) sep1.setBackground(helpBackgroundColor);
		if (sep2 != null) sep2.setBackground(statusBackgroundColor);
		if (circleStatus != null) circleStatus.resetColors();
	}

	public void addStatusMessage(String message, int priority) {
		if (statusMessages != null)
			synchronized (statusMessages) {
				statusMessages.add(new StatusMessage(message,priority));
				checkMessages();
			}
	}

	public void addWarningMessage(String message) {
		addStatusMessage(message,MEDIUM_PRIORITY);
	}

	public void setBusy(boolean busy, Object comp) {
		if (circleStatus != null) circleStatus.setBusy(busy,comp);
	}

	public void setPaused(boolean paused, Object comp) {
		if (circleStatus != null) circleStatus.setPaused(paused,comp);
	}

	public void addHelpListener(Component comp) { // mdb changed from JComponent to Component 2/16/09
		synchronized (comps) {
			comps.put(comp,comp);

			comp.addMouseListener(this);
			//comp.removeMouseListener(this); // mdb removed 7/6/09 - strange mistake
			comp.addMouseMotionListener(this); // mdb added 7/6/09
		}
	}

	public void addHelpListener(Component comp, HelpListener listener) { // mdb changed from JComponent to Component 2/16/09
		synchronized (comps) {
			comps.put(comp,listener);

			comp.addMouseListener(this);
			comp.addMouseMotionListener(this);
		}
	}

	public void removeHelpListener(Component comp) { // mdb changed from JComponent to Component 2/16/09
		synchronized (comps) {
			comps.remove(comp);

			comp.removeMouseListener(this);
			comp.removeMouseMotionListener(this);
			if (currentHelp == comp)
				setHelp("",null);
		}
	}

	public void mouseMoved(MouseEvent e) {
		synchronized (comps) {
			Object obj = comps.get(e.getSource());
			if (obj != null) 
			{
				if (obj instanceof HelpListener) 
				{
					setHelp( ((HelpListener)obj).getHelpText(e), e.getSource() );
				}
				else if (obj instanceof JComponent) 
				{
					setHelp( ((JComponent)obj).getToolTipText(e), e.getSource() );
				}
			}
			else if (e.getSource() instanceof JComponent)
			{
				setHelp( ((JComponent)e.getSource()).getToolTipText(e), e.getSource() );
			}
		}
	}

	public void mouseEntered(MouseEvent e) {
		mouseMoved(e);		
	}

	public void mouseExited(MouseEvent e) {
		if (e.getSource() == currentHelp) {
			setHelp("",null);
		}
	}

	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == statusTimer) {
			synchronized (statusMessages) {
				checkMessages();
			}
		}
	}

	private void checkMessages() {
		long time = System.currentTimeMillis();
		StatusMessage cmes = null, pmes = null;
		for (int i = statusMessages.size()-1; i >= 0; i--) {
			cmes = statusMessages.get(i);
			if (cmes.isDead(time) || (pmes != null && cmes.isActive(time) && !cmes.isActive(pmes.getPriority(),time))) {
				statusMessages.remove(i);
			}
			else {
				pmes = cmes;
			}
		}
		if (!statusMessages.isEmpty()) {
			cmes = statusMessages.get(0);
			cmes.setActive(time);
			statusLabel.setText(cmes.getMessage());
		}
		else {
			statusLabel.setText("");
		}
	}

	private JSeparator createSeparator() {
		JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
		sep.setOpaque(true);
		sep.setPreferredSize(new Dimension(3,10));
		sep.setMaximumSize(new Dimension(3,10));
		return sep;
	}

	public void setHelp(String help, Object obj) {
		if (help == null || help.equals("")) { // mdb added empty string 3/28/07
			//help = ""; // mdb removed 3/28/07
			help = "Move the mouse over an object for further info."; // mdb added 3/28/07
		}
		else {
			// mdb added 6/9/09
			if (getSize().height < 50)
				help = help.replaceAll("\n", " ");
		}
				
		helpLabel.setText(help);
		currentHelp = obj;	
	}

	public void mouseDragged(MouseEvent e) { }
	public void mouseClicked(MouseEvent e) { }
	public void mousePressed(MouseEvent e) { }
	public void mouseReleased(MouseEvent e) { }

	private class StatusMessage {
		private int priority;
		private long created;
		private long active;
		private String message;

		public StatusMessage(String message, int priority) {
			this.message = message;
			this.priority = priority;
			this.created = System.currentTimeMillis();
			this.active = -1;
		}

		public boolean isDead(long time) {
			if (created + TIMES[priority][DEAD_TIME] <= time) return true;
			else return false;
		}

		public void setActive(long time) {
			if (active <= 0) active = time;
		}

		public boolean isActive(long time) {
			return !isDead(time) && active > 0;
		}

		public boolean isActive(int replacePriority, long time) {
			if (!isActive(time)) return false;
			else {
				long sec = 0;
				if (replacePriority < priority) {
					sec = TIMES[priority][LOWER_TIME];
				}
				else if (replacePriority == priority) {
					sec = TIMES[priority][SAME_TIME];
				}
				else {
					sec = TIMES[priority][HIGHER_TIME];
				}
				return (active + sec > time);
			}
		}

		public String getMessage() {
			return message;
		}

		public int getPriority() {
			return priority;
		}
	}

	private class CircleStatus extends JComponent implements ActionListener {
		private static final int BLINK_DELAY = 500; // milliseconds between changing colors when blinking

		private CircleObj circle;
		private Vector<Object> busyComps, pausedComps;
		private Timer blinkTimer;

		public CircleStatus(int size) {
			setBackground(circleBackgroundColor);
			setForeground(circleBackgroundColor);
			setPreferredSize(new Dimension(size,size));
			setMaximumSize(new Dimension(size,size));
			setSize(new Dimension(size,size));
			circle = new CircleObj(Math.round( (size/3.0) ), Color.black, interactiveColor);
			busyComps = new Vector<Object>();
			pausedComps = new Vector<Object>();
			blinkTimer = new Timer(BLINK_DELAY, this);
		}

		public void resetColors() {
			setBackground(circleBackgroundColor);
			setForeground(circleBackgroundColor);
			resetCircle();
		}

		public void actionPerformed(ActionEvent evt) {
			synchronized (circle) {
				if (circle.getFill() == blinkColor) circle.setFill(busyColor);
				else if (circle.getFill() == busyColor) circle.setFill(blinkColor);
				repaint();
			}
		}

		public void setBusy(boolean busy, Object comp) {
			if (!busy) busyComps.remove(comp);
			else if (!busyComps.contains(comp)) busyComps.add(comp);
			resetCircle();
		}

		public void setPaused(boolean paused, Object comp) {
			if (!paused) pausedComps.remove(comp);
			else if (!pausedComps.contains(comp)) pausedComps.add(comp);
			resetCircle();
		}

		private void resetCircle() {
			synchronized (circle) {
				blinkTimer.stop();
				if (!busyComps.isEmpty()) {
					circle.setFill(busyColor);
					blinkTimer.start();
				}
				else if (!pausedComps.isEmpty()) circle.setFill(pausedColor);
				else circle.setFill(interactiveColor);
			}
			repaint();
		}

		public void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.setColor(circleBackgroundColor);
			g.fillRect(0,0,getWidth(),getHeight());
			circle.setLocation(Math.round(getWidth()/2.0),Math.round(getHeight()/2.0));
			circle.paint(g);
		}
	}
}
