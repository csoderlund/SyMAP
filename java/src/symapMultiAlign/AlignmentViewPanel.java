package symapMultiAlign;

/**************************************
 * For Symap Query: align selected set using muscle
 * CAS521: pass in shortName instead of computing here. pass in lines for the legend
 */
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import symapQuery.SyMAPQueryFrame;
import util.ErrorReport;
import util.Utilities;

public class AlignmentViewPanel extends JPanel {
	private static final long serialVersionUID = -2090028995232770402L;
	private String [] lines;
	
	public AlignmentViewPanel(SyMAPQueryFrame parentFrame, 
			String [] shortNames, String [] lines, String [] sequences, String fileName) {
		theParentFrame = parentFrame;
		this.lines = lines;
		
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBackground(Color.WHITE);
		
		buildMultiAlignments(shortNames, sequences, fileName);
	}
	
	private void buildMultiAlignments(String [] names, String [] sequences, String fileName) {
		final String [] theNames = names;
		final String [] theSequences = sequences;
		final String theFilename = fileName;
		if(theThread == null)
		{
			theThread = new Thread(new Runnable() {
				public void run() {
					try {
						setStatus();
						//Load sequences from the database
						setMultiSequenceData(theNames, theSequences, theFilename);
					
						createButtonPanel();
						createMainPanel();
					
						showStatus(false);
					
						add(buttonPanel);
						add(scroller);
						
						updateExportButton();
					} catch (Exception e) {
						ErrorReport.print(e, "Build Alignment"); // CAS516 e.printStackTrace
					}
				}
			});
			theThread.setPriority(Thread.MIN_PRIORITY);
			theThread.start();
		}		
	}
	
	private void setStatus() {
		progressField = new JTextField(100);
		progressField.setEditable(false);
		progressField.setMaximumSize(progressField.getPreferredSize());
		progressField.setBackground(Color.WHITE);
		progressField.setBorder(BorderFactory.createEmptyBorder());
		btnCancel = new JButton("Cancel Alignment");
		btnCancel.setBackground(Color.WHITE);
		btnCancel.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				stopThread();
			}
		});
		
		add(progressField);
		add(Box.createVerticalStrut(10));
		add(btnCancel);
	}
	
	private void showStatus(boolean show) {
		btnCancel.setVisible(show);
		progressField.setVisible(show);
	}
	
	private void updateStatus(String status) {
		progressField.setText(status);
	}
	
	private void stopThread() {
		if(theThread != null) {
			Utilities.showInfoMessage("Cancel Alignment", 
					"Remove this tab from Results\nMUSCLE must be stopped by user from terminal using kill.");
		}
	}
	
	private void setMultiSequenceData(String [] names, String [] sequences, String fileName) {
		theMultiAlignmentData = new MultiAlignmentData(fileName, progressField);
		for(int x=0; x<names.length; x++)
			theMultiAlignmentData.addSequence(names[x], sequences[x]);
		
		updateStatus("Aligning sequences please wait..");
		theMultiAlignmentData.alignSequences();
		createMultiAlignPanels();
	}
	
	private void createButtonPanel() {
		buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.PAGE_AXIS));
		buttonPanel.setBackground(Color.WHITE);

		add(Box.createVerticalStrut(10));
		add(getButtonRow1());
		
		//add(Box.createVerticalStrut(10)); CAS521 doesn't seem to work right. Plus in muscle/
		//add(getMultiButtonRow());
		
		buttonPanel.setAlignmentX(LEFT_ALIGNMENT);
	}
	
	private JPanel getButtonRow1() {
		JPanel theRow = new JPanel();
		theRow.setLayout(new BoxLayout(theRow, BoxLayout.LINE_AXIS));
		theRow.setAlignmentX(LEFT_ALIGNMENT);
		theRow.setBackground(Color.WHITE);
		
		menuHorzRatio = new JComboBox <MenuMapper> ();
		menuHorzRatio.addItem( new MenuMapper ( "Horz. Ratio = 1:1", 1 ) );
		menuHorzRatio.addItem( new MenuMapper ( "Horz. Ratio = 1:2", 2 ) );
		menuHorzRatio.addItem( new MenuMapper ( "Horz. Ratio = 1:3", 3 ) );
		menuHorzRatio.addItem( new MenuMapper ( "Horz. Ratio = 1:4", 4 ) );
		menuHorzRatio.addItem( new MenuMapper ( "Horz. Ratio = 1:5", 5 ) );
		menuHorzRatio.addItem( new MenuMapper ( "Horz. Ratio = 1:6", 6 ) );
		menuHorzRatio.addItem( new MenuMapper ( "Horz. Ratio = 1:7", 7 ) );
		menuHorzRatio.addItem( new MenuMapper ( "Horz. Ratio = 1:8", 8 ) );
		menuHorzRatio.addItem( new MenuMapper ( "Horz. Ratio = 1:9", 9 ) );
		menuHorzRatio.addItem( new MenuMapper ( "Horz. Ratio = 1:10", 10 ) );
		menuHorzRatio.setBackground(Color.WHITE);
		menuHorzRatio.setSelectedIndex(0);
		menuHorzRatio.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
					refreshPanels();
			}
		});	
		
		btnShowType = new JButton("Show Sequences");
		btnShowType.setBackground(Color.WHITE);
		btnShowType.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if(btnShowType.getText().equals("Show Graphic")) {
					btnShowType.setText("Show Sequences");
					menuHorzRatio.setEnabled(true);
				}
				else {
					btnShowType.setText("Show Graphic");
					menuHorzRatio.setEnabled(false);
				}
				refreshPanels();
			}
		});
		theRow.add(menuHorzRatio);
		theRow.add(Box.createHorizontalStrut(10));
		theRow.add(btnShowType);
		
		btnExport = new JButton("Export");
		btnExport.setBackground(Color.WHITE);
		btnExport.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if(theMultiPanel != null) {
					String filename = "";
					
					filename = JOptionPane.showInputDialog("Enter file name");
					if(filename != null && filename.length() > 0) {
						if(!filename.endsWith(".fasta"))
							filename += ".fasta";
						theMultiAlignmentData.writeMuscleCache(filename);
					}
				}
			}
		});
		theRow.add(Box.createHorizontalStrut(10));
		theRow.add(btnExport);			
		
		theRow.setMaximumSize(theRow.getPreferredSize());
		return theRow;
	}
	
	private void updateExportButton() {
		if(theMultiAlignmentData != null && theMultiAlignmentData.hasFilename())
			btnExport.setEnabled(true);
		else
			btnExport.setEnabled(false);
	}
	
	private void refreshPanels() {
		refreshMultiPanels();
	}
		
	private void refreshMultiPanels() {
		mainPanel.removeAll();
		try {
			boolean showText = btnShowType.getText().equals("Show Sequences");
			MenuMapper ratioSelection = (MenuMapper) menuHorzRatio.getSelectedItem();
			int ratio = ratioSelection.asInt();
			
			if(theMultiPanel != null) {
				theMultiPanel.setBorderColor(Color.BLACK);
				theMultiPanel.setBasesPerPixel(ratio);
				if(showText)
					theMultiPanel.setDrawMode(AlignmentPanelBase.GRAPHICMODE);
				else
					theMultiPanel.setDrawMode(AlignmentPanelBase.TEXTMODE);
				
				mainPanel.add(theMultiPanel);
				mainPanel.add(Box.createVerticalStrut(3));
				/* CAS502 its wrong - the legend included gaps and mismatches, which are obvious **/
				/* CAS505 made legend the long names; CAS521 made legend table rows */
				LegendPanel lPanel = new LegendPanel(lines);
				//mainPanel.add(Box.createHorizontalStrut(10));
				mainPanel.add(lPanel);
				
			} else {
				mainPanel.add(new JLabel("No Sequences"));
			}
			
			mainPanel.revalidate();
			mainPanel.repaint();
		} catch (Exception e) {ErrorReport.print(e, "Refresh Multi Alignment Panel"); }
	}
	
	private void createMultiAlignPanels() {
		theMultiPanel = new MultipleAlignmentPanel(theParentFrame, theMultiAlignmentData, 10, 10, 10, 10);
		theMultiPanel.setAlignmentY(Component.LEFT_ALIGNMENT);
	}
	
	private void createMainPanel() {
		scroller = new JScrollPane ( );
		scroller.setHorizontalScrollBarPolicy( JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
		scroller.setVerticalScrollBarPolicy( JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED );		
		scroller.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				handleClick(e);
			}
		});	
		
		mainPanel = new JPanel();
		
		mainPanel.setLayout( new BoxLayout ( mainPanel, BoxLayout.Y_AXIS ) );
		mainPanel.setBackground( Color.WHITE );

		mainPanel.setAlignmentX(LEFT_ALIGNMENT);
		
		scroller.setViewportView( mainPanel );
		
		refreshPanels();
	}
	
	private void handleClick(MouseEvent e) {
		handleClickForMultiple(e);
	}
	
	private void handleClickForMultiple(MouseEvent e) {
		if(theMultiPanel != null) {
			// Convert to view relative coordinates
			int viewX = (int) (e.getX() + scroller.getViewport().getViewPosition().getX());
			int viewY = (int) (e.getY() + scroller.getViewport().getViewPosition().getY());
			
			// Get the panel and convert to panel relative coordinates
			int nPanelX = viewX - theMultiPanel.getX();
			int nPanelY = viewY - theMultiPanel.getY();
	
			if ( theMultiPanel.contains( nPanelX, nPanelY ) )
				// Click is in current panel, let the object handle it
				theMultiPanel.handleClick( e, new Point( nPanelX, nPanelY ) );
			else
				// Clear all selections in the panel unless shift or control are down
				if ( !e.isShiftDown() && !e.isControlDown() ) {
					theMultiPanel.selectNoRows();
					theMultiPanel.selectNoColumns();
				}
			//refreshMultiButtons();
		}
	}
	
	private SyMAPQueryFrame theParentFrame = null;
	private JScrollPane scroller = null;
	
	//Main panels for the tab
	private JPanel buttonPanel = null;
	private JPanel mainPanel = null;
	
	//UI controls for the button panel
	private JComboBox <MenuMapper> menuHorzRatio = null;
	private JButton btnShowType = null;
	private JButton btnExport = null;
	
	//Controls for progress/cancelling action
	private JTextField progressField = null;
	private JButton btnCancel = null;
	
	//Thread used for building the sequence data
	private Thread theThread = null;
	
	//Multiple alignment dataholders
	private MultiAlignmentData theMultiAlignmentData = null;
	private MultipleAlignmentPanel theMultiPanel = null;
}
