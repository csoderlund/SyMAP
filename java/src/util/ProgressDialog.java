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

import java.io.FileWriter;
import java.io.FileDescriptor;
import util.ErrorCount;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class ProgressDialog extends JDialog implements Logger {
	private static final int MIN_DISPLAY_TIME = 1500; // milliseconds
	
	private JProgressBar progressBar;
	private JTextArea textArea;
	private JPanel panel;
	private JButton button;
	private long startTime;
	private Thread userThread = null;
	private boolean bCancelled = false;
	private boolean bCloseWhenDone = false;
	private boolean bCloseIfNoErrors = false;
	private boolean bDone = false;
	private FileWriter logFileW = null;
	
	public ProgressDialog(final Frame owner, String strTitle, String strMessage, 
			boolean isDeterminate, boolean hasConsole, FileWriter logW) 
	{
		super(owner, strTitle, true);
		Cancelled.init();
		logFileW = logW;
		//setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); // interferes with cancel() somehow
		
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

		// Console and button
		if (hasConsole) {
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
		}
		
		// Add everything to top-level panel
		panel = new JPanel();
		panel.setLayout( new BoxLayout ( panel, BoxLayout.Y_AXIS ) );
		panel.setBorder( BorderFactory.createEmptyBorder(15, 10, 10, 10) );
		panel.setAlignmentX(Component.CENTER_ALIGNMENT);
		panel.add( label );
		panel.add( Box.createVerticalStrut(5) );
		panel.add( progressBar );
		panel.add( Box.createVerticalStrut(1) );
		if (hasConsole) {
			panel.add( new JScrollPane(textArea, 
					JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
					JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) );
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
		    	if (!bDone)
		    		cancel();
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
	public void closeIfNoErrors()
	{
		ErrorCount.init();
		bCloseIfNoErrors = true;
	}
	public void start(Thread thread) {
		this.userThread = thread;
		
        thread.start();
        
        setVisible(true); // blocking
	}
	
	public void start() {
		setVisible(true); // blocking
	}
	public void closeWhenDone()
	{
		bCloseWhenDone = true;	
	}
	public synchronized void increment(int n) { 
		progressBar.setValue( progressBar.getValue() + n );
	}
	
	public synchronized void increment() {
		increment(1);
	}
	
	public synchronized void setText(String s) {
		textArea.setText(s);
	}
	
	public synchronized void appendText(String s) {
		if (!s.endsWith("\n")) s += "\n";
		textArea.append(s);
		System.out.print(s);
		if (logFileW != null)
		{
			try
			{
				logFileW.write(s);
				logFileW.flush();
			}
			catch (Exception e) {}
		}
	}
	
	public synchronized void updateText(String search, String newText) {
		String text = textArea.getText();
		int pos = text.lastIndexOf(search);
		if (pos < 0)
			textArea.append(search + newText);
		else {
			text = text.substring(0, pos) + search + newText;
			textArea.setText(text);
	
		}
		
	}
	
	public void finish() {
		finish(true);
	}
	
	public void finish(boolean success) {
		if (bCloseWhenDone)
		{
			finishAndClose(success);	
			return;
		}
		if (bCloseIfNoErrors)
		{
			setCursor( Cursor.getDefaultCursor() );
			progressBar.setIndeterminate(false);
			progressBar.setString("Completed, click 'Done' to continue ...");
			if (ErrorCount.getCount() > 0)
			{
				msg("Some errors occurred, check printout before closing.");
				button.setText("Done (Error Occurred)"); 
			}
			else
			{
				finishAndClose(success);	
				return;				
			}
		}
		else
		{
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
			else
			{
				dispose();
			}
		}
	}
	public void finishAndClose(boolean success) {
		long runTime = System.currentTimeMillis() - startTime;
		if (runTime < MIN_DISPLAY_TIME) // ensure dialog is visible
			try { Thread.sleep(MIN_DISPLAY_TIME - runTime); } 
			catch (Exception e) { }
		setCursor( Cursor.getDefaultCursor() );
		dispose(); // Close this dialog
	}	
	public void setCancelled()
	{
		bCancelled = true;	
	}
	private void cancel() {
		bCancelled = true;
		Cancelled.cancel();
		if (userThread != null)
			userThread.interrupt();
		while (userThread != null && userThread.isAlive())
		{
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
	
	
	// Logger interface
	public void msg(String msg) {
		appendText(msg + "\n");
	}
	public void msgToFile(String s) {
		if (logFileW != null)
		{
			try
			{
				logFileW.write(s);
				logFileW.flush();
			}
			catch (Exception e) {}
		}
	}	
	public void write(char c) {
		appendText(new String(new char[] { c }));
	}
}
