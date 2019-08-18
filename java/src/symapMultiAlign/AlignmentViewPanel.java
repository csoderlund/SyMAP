package symapMultiAlign;

import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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

public class AlignmentViewPanel extends JPanel {
	private static final long serialVersionUID = -2090028995232770402L;

	public AlignmentViewPanel(SyMAPQueryFrame parentFrame, String [] names, String [] sequences, String fileName) {
		theParentFrame = parentFrame;
		
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBackground(Color.WHITE);
		buildMultiAlignments(names, sequences, fileName);
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
						bRunThread = true;
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
						e.printStackTrace();
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
			bRunThread = false;
		}
	}
	
	private void setMultiSequenceData(String [] names, String [] sequences, String fileName) {
		theMultiAlignmentData = new MultiAlignmentData(fileName);
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
		add(Box.createVerticalStrut(10));
		add(getMultiButtonRow());
		
		buttonPanel.setAlignmentX(LEFT_ALIGNMENT);
	}
	
	private JPanel getButtonRow1() {
		JPanel theRow = new JPanel();
		theRow.setLayout(new BoxLayout(theRow, BoxLayout.LINE_AXIS));
		theRow.setAlignmentX(LEFT_ALIGNMENT);
		theRow.setBackground(Color.WHITE);
		
		menuHorzRatio = new JComboBox ();
		//menuHorzRatio.setPreferredSize( dim2 );
		//menuHorzRatio.setMaximumSize ( dim2 );
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
	
	private JPanel getMultiButtonRow() {
		JPanel theRow = new JPanel();
		theRow.setLayout(new BoxLayout(theRow, BoxLayout.LINE_AXIS));
		theRow.setBackground(Color.WHITE);
		theRow.setAlignmentX(LEFT_ALIGNMENT);
		
		btnCopySeq = new JButton("Copy Sequence");
		btnCopySeq.setEnabled(false);
		btnCopySeq.setBackground(Color.WHITE);
		btnCopySeq.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
				cb.setContents(new StringSelection(theMultiPanel.getSelectedSequence()), null);

			}
		});

		theRow.add(btnCopySeq);
		
		theRow.setMaximumSize(theRow.getPreferredSize());
		return theRow;
	}
	
	private void refreshPanels() {
		refreshMultiPanels();
	}
	
	private void refreshMultiButtons() {
		if(theMultiPanel.getNumSequencesSelected() == 1)
			btnCopySeq.setEnabled(true);
		else
			btnCopySeq.setEnabled(false);
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

				mainPanel.add(Box.createVerticalStrut(40));
				LegendPanel lPanel = new LegendPanel();
				lPanel.setIsPair(true);
				mainPanel.add(lPanel);
			} else {
				mainPanel.add(new JLabel("No Sequences"));
			}
			
			mainPanel.revalidate();
			mainPanel.repaint();
		} catch (Exception e) {
			e.printStackTrace();
		}
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
			
			refreshMultiButtons();
		}
	}

	private SyMAPQueryFrame theParentFrame = null;
	//Display panels
	private JScrollPane scroller = null;
	
	//Main panels for the tab
	private JPanel buttonPanel = null;
	private JPanel mainPanel = null;
	
	//UI controls for the button panel
	private JComboBox menuHorzRatio = null;
	private JButton btnShowType = null;
	
	private JButton btnCopySeq = null;
	private JButton btnShowAll = null;
	private JButton btnShowAllPairs = null;
	private JButton btnShowOnlyPairs = null;
	private JButton btnShowHelp = null;
	
	private JButton btnExport = null;
	
	//Controls for progress/cancelling action
	private JTextField progressField = null;
	private JButton btnCancel = null;
	
	//Thread used for building the sequence data
	private Thread theThread = null;
	private boolean bRunThread = false;
	
	//Multiple alignment dataholders
	private MultiAlignmentData theMultiAlignmentData = null;
	private MultipleAlignmentPanel theMultiPanel = null;
}
