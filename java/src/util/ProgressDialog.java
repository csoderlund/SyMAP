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
 * CAS535 removed Logger interface
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ProgressDialog extends JDialog  {
	private static final int MIN_DISPLAY_TIME = 1500; // milliseconds
	
	private JProgressBar progressBar;
	private JTextArea textArea;
	private JPanel panel;
	private JButton button;
	private long startTime;
	private Thread userThread = null;
	private boolean bCancelled = false, bCloseWhenDone = false, bCloseIfNoErrors = false, bDone = false;
	private FileWriter logFileW = null;
	
	public ProgressDialog(final Frame owner, String strTitle, String strMessage, 
			boolean hasCancel, // CAS511 Removes has this as false, cause Hangs if Cancel
			FileWriter logW)   // LOAD.log or symap.log (2nd also written to by backend.Log)
	{
		super(owner, strTitle, true);
		Cancelled.init();
		logFileW = logW;
		boolean isDeterminate=false; // CAS535 was parameter, but always false
	
		msgToFileOnly("-----------------------------------------------------------------");
		msgToFileOnly(">>> " + Globals.VERDATE + "   " + ErrorReport.getDate() + " <<<");
		
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
		//textArea.setWrapStyleWord(true);
		textArea.setLineWrap(true);
		((DefaultCaret)textArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // enable auto-scroll
			
		button = new JButton("Cancel");
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
		    	if (!bDone) cancel();
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
		this.userThread = thread;
		
        thread.start();
        
        setVisible(true); // blocking
	}
	
	public void start() {
		setVisible(true); // blocking
	}
	public void closeIfNoErrors(){
		ErrorCount.init();
		bCloseIfNoErrors = true;
	}

	public void closeWhenDone(){
		bCloseWhenDone = true;	
	}
	
	public void finish(boolean success) {
		if (bCloseWhenDone) {
			finishAndClose(success);	
			return;
		}
		if (bCloseIfNoErrors) {
			setCursor( Cursor.getDefaultCursor() );
			progressBar.setIndeterminate(false);
			
			if (ErrorCount.getCount() > 0) {
				progressBar.setString("Completed, click 'Done' to continue ..."); // CAS511 move after if
				msg("Some errors occurred, check text in this window, terminal or view logs/[dbName]_load.log.");
				button.setText("Done (Error Occurred)"); 
			}
			else {
				finishAndClose(success);	
				return;				
			}
		}
		else {
			long runTime = System.currentTimeMillis() - startTime;
			if (runTime < MIN_DISPLAY_TIME) // ensure dialog is visible
				try { Thread.sleep(MIN_DISPLAY_TIME - runTime); } 
				catch (Exception e) { }
			setCursor( Cursor.getDefaultCursor() );
			if (button != null ) {
				progressBar.setIndeterminate(false);
				if (success) {
					bDone = true;
					progressBar.setString("Completed, click 'Done' to continue ...");
					button.setText("Done"); // Wait for user to close
				}
				else {
					//cancel();//bCancelled = true;
					progressBar.setString("Error occurred, click 'Cancel' to abort ...");
				}
			}
			else {
				dispose();
			}
		}
	}
	public void finishAndClose(boolean success) {
		long runTime = System.currentTimeMillis() - startTime;
		if (runTime < MIN_DISPLAY_TIME) // ensure dialog is visible
			try { Thread.sleep(MIN_DISPLAY_TIME - runTime); } 
			catch (Exception e) { }
			
		try {
			setCursor( Cursor.getDefaultCursor() );
			dispose(); // Close this dialog
		}
		catch (Exception e) {} // CAS508 Cancel causes stacktrace on dispose; crashes anyway on dispose
		
		if (logFileW != null) // CAS500
			try {
				logFileW.close(); 
				logFileW=null;}
			catch (Exception e) {} // CAS500
	}	
	public void setCancelled() {
		try {
			if (logFileW!=null) logFileW.append("Cancel"); // CAS535
			bCancelled = true;	
		}
		catch (Exception e) {}
	}
	private void cancel() {
		bCancelled = true;
		Cancelled.cancel();
		if (userThread != null)
			userThread.interrupt();
		while (userThread != null && userThread.isAlive()) {
			try{Thread.sleep(100);} catch(Exception e){}
		}
	}
	public boolean wasCancelled() { return bCancelled; }
	
	public void addActionListener(ActionListener listener) {
		button.removeActionListener( handleButton );
		button.addActionListener( listener );
	}
	
	private ActionListener handleButton = new ActionListener() {
		public void actionPerformed(ActionEvent e) {
			if (button.getText().equals("Cancel"))
				cancel();
			ProgressDialog.this.setVisible(false);//ProgressDialog.this.dispose();
		}
	};
	/************************************************************/
	
	public synchronized void updateText(String search, String newText) {
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
		appendText(msg);
	}
	
	public synchronized void appendText(String s) {
		if (!s.endsWith("\n")) s += "\n";
		
		msgToFile(s);
		
		if (textArea!=null) textArea.append(s);
	}
	
	public void msgOnly(String s) {
		if (!s.endsWith("\n")) s += "\n";
		if (textArea!=null) textArea.append(s);
	}
	
	public void msgToFile(String s) {
		if (!s.endsWith("\n")) s += "\n";
		System.out.print(s);
		
		msgToFileOnly(s);
	}	
	
	public void msgToFileOnly(String s) {
		if (!s.endsWith("\n")) s += "\n";
		if (logFileW != null) {
			try {
				logFileW.write(s);
				logFileW.flush();
			}
			catch (Exception e) {}
		}
	}
}
