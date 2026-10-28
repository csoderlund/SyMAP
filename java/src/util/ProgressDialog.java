package util;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent; 
import java.awt.event.WindowAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseAdapter;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.text.DefaultCaret;

import symap.Globals;

import java.io.FileWriter;

/***************************************************************
 * Used by ManagerFrame for popup dialog tracking progress
 */
public class ProgressDialog extends JDialog  {
	private static final long serialVersionUID = 1L;

	private static final int MIN_DISPLAY_TIME = 1500; // milliseconds
	
	public static final String CANCEL="Cancel", DONE="Done";
	
	private JProgressBar progressBar;
	private JTextArea textArea;
	private JPanel panel;
	private JButton button;
	private long startTime;
	private Thread userThread = null;
	private boolean bCancelled = false; 
	private boolean bDone = false;
	private FileWriter logFileW = null;
	
	public ProgressDialog(final Frame owner, String strTitle, String strMessage, 
			boolean hasCancel, 
			FileWriter logW)   // LOAD.log or symap.log (2nd also written to by backend.Log)
	{
		super(owner, strTitle, true);
		Cancelled.init();
		logFileW = logW;
		boolean isDeterminate=false; 
	
		msgLog("-----------------------------------------------------------------");
		String x = "    Running Java v" + System.getProperty("java.version") + " on " + ErrorReport.getDate(); 
		msgLog(">>> SyMAP " + Globals.VERDATE + x + " <<<");
		
		// Label
		JLabel label = new JLabel(strMessage);
		label.setAlignmentX(Component.CENTER_ALIGNMENT);
		label.setFont( new Font(label.getFont().getName(), Font.PLAIN, 12) );
		
		// Progress Bar
		progressBar = new JProgressBar();
		progressBar.setIndeterminate(!isDeterminate);
		if (isDeterminate) {
			progressBar.setMinimum(0);
			progressBar.setMaximum(100);
			progressBar.setValue(0);
		}
		progressBar.setStringPainted(true);
		progressBar.setString(""); // set height

		textArea = new JTextArea(15, 80);
		textArea.setMargin(new Insets(5,5,5,5));
		textArea.setEditable(false);
		textArea.setFont(new Font("monospaced", Font.PLAIN, 10));
		textArea.setLineWrap(true);
		((DefaultCaret)textArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // enable auto-scroll
			
		button = new JButton(CANCEL);
		button.setAlignmentX(Component.CENTER_ALIGNMENT);
		button.addActionListener( handleButton );
		
		// Add everything to top-level panel
		panel = new JPanel();
		panel.setLayout( new BoxLayout ( panel, BoxLayout.Y_AXIS ) );
		panel.setBorder( BorderFactory.createEmptyBorder(15, 10, 10, 10) );
		panel.setAlignmentX(Component.CENTER_ALIGNMENT);
		panel.add( label );
		panel.add( Box.createVerticalStrut(5) );
		panel.add( progressBar );
		panel.add( Box.createVerticalStrut(1) );
		
		panel.add( new JScrollPane(textArea, 
					JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
					JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) );
		
		if (hasCancel) {
			panel.add( Box.createVerticalStrut(10) );
			panel.add( button );
		}
		panel.add( Box.createVerticalStrut(5) );
		
		// Setup dialog
		setContentPane(panel);
		pack();
		setLocationRelativeTo(owner);
		setCursor( new Cursor(Cursor.WAIT_CURSOR) );
		
		addWindowListener(new WindowAdapter() {
		    public void windowClosing(WindowEvent we) {
		    	if (!bDone) cancelThread();
		    }
		});
		addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() >= 2
						&& (!getLocation().equals(owner.getLocation())
								|| !getSize().equals(owner.getSize()))) 
				{
					setLocation(owner.getLocation());
					setSize(owner.getSize());
				}
			}
		});
		
		startTime = System.currentTimeMillis(); // must be here and not in start() for some reason
	}
	public void start(Thread thread) {
		try {
			this.userThread = thread;
			
	        thread.start();
	        
	        setVisible(true); // blocking
		}
		catch (Exception e) {ErrorReport.print(e, "Progress thread panel");}
	}
	
	public void start() {
		try {
			setVisible(true); // blocking
		}
		catch (Exception e) {ErrorReport.print(e, "Progress panel");}
	}

	public void finish(boolean bSuccess) {
		setCursor( Cursor.getDefaultCursor() );
		progressBar.setIndeterminate(false);
		
		if (ErrorCount.getCount() > 0) {
			progressBar.setString("Error occurred, click 'Done' to abort ...");
			msg("Some errors occurred. See terminal and /logs.");
			button.setText(DONE + " (Error Occurred)"); 
		}
		else {
			finishAndClose();	
			return;				
		}
	}
	public void finishAndClose() {
		long runTime = System.currentTimeMillis() - startTime;
		if (runTime < MIN_DISPLAY_TIME) {// ensure dialog is visible
			try { Thread.sleep(MIN_DISPLAY_TIME - runTime); } 
			catch (Exception e) { }
		}
		try {
			setCursor( Cursor.getDefaultCursor() );
			dispose(); 					// Close this dialog
		}
		catch (Exception e) {} 
		
		if (logFileW != null) {
			try {
				logFileW.close(); 
				logFileW=null;
			}
			catch (Exception e) {} 
		}
	}	
	public void setCancelled() {
		if (!bCancelled) msg("Cancelled");
		bCancelled = true;	
		Cancelled.cancel();
	}
	public void setFailed() {
		int cnt = ErrorCount.getCount();
		if (cnt<10000) ErrorCount.inc();// actual number not used
	}

	private void cancelThread() {
		setCancelled();
		if (userThread != null) userThread.interrupt();
		
		while (userThread != null && userThread.isAlive()) {
			try {Thread.sleep(100);} catch(Exception e){}
		}
	}
	public boolean wasCancelled() { return bCancelled; }
	
	public boolean failed() { return ErrorCount.getCount()>0; }
	
	public void addActionListener(ActionListener listener) {
		button.removeActionListener( handleButton );
		button.addActionListener( listener );
	}
	
	private ActionListener handleButton = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			cancelThread();
			ProgressDialog.this.setVisible(false);//ProgressDialog.this.dispose();
		}
	};
	/************************************************************
	 * Write to textArea, logFile, stdout
	 **********************************************************/
	
	public synchronized void updatePop(String search, String newText) {//updatePop
		if (textArea==null) return;
		String text = textArea.getText();
		
		int pos = text.lastIndexOf(search);
		if (pos < 0)
			textArea.append(search + newText);
		else {
			text = text.substring(0, pos) + search + newText;
			textArea.setText(text);
		}	
	}
	public void msg(String msg) {
		msgPopLogOut(msg);
	}
	public synchronized void msgPopLogOut(String s) { // msgPopLogOut
		if (!s.endsWith("\n")) s += "\n";
		
		System.out.print(s);
		
		msgLog(s);
		
		if (textArea!=null) textArea.append(s);
	}
	// textArea
	public void msgPop(String s) { 
		if (!s.endsWith("\n")) s += "\n";
		if (textArea!=null) textArea.append(s);
	}
	// file, stdout
	public void msgLogOut(String s) {		
		if (!s.endsWith("\n")) s += "\n";
		System.out.print(s);
		
		msgLog(s);
	}	
	// file
	public void msgLog(String s) { 
		if (!s.endsWith("\n")) s += "\n";
		if (logFileW != null) {
			try {
				logFileW.write(s);
				logFileW.flush();
			}
			catch (Exception e) {}
		}
	}
	// Dialog, symap.log, error.log/out
	public void msgPopLogErr(String s) {	
		ErrorReport.print(s);
		msgPop(s);
		msgLog(s);
	}
}
