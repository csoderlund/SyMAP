package symapQuery;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.AbstractTableModel;

public class ResultSummaryPanel extends JPanel {
	private static final long serialVersionUID = -4532933089334778200L;

	public ResultSummaryPanel(SyMAPQueryFrame parentFrame, String [] columnLabels) {
		theParentFrame = parentFrame;
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBackground(Color.WHITE);
		colNames = columnLabels;
		
		theTable = new JTable();
		theTable.getTableHeader().setBackground(Color.WHITE);
		theTable.setColumnSelectionAllowed( false );
		theTable.setCellSelectionEnabled( false );
		theTable.setRowSelectionAllowed( true );
		theTable.setShowHorizontalLines( false );
		theTable.setShowVerticalLines( true );	
		theTable.setIntercellSpacing ( new Dimension ( 1, 0 ) );		
		rows = new Vector<String []>();
		theTable.setModel(new ResultsTableModel());
		theTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				updateButtons();
				if (e.getClickCount() == 2) {
					int row = theTable.getSelectedRow();
					theParentFrame.selectResult(row);
				}
			}
		});
		
		scroll = new JScrollPane(theTable);
		scroll.setBorder( null );
		scroll.setPreferredSize(java.awt.Toolkit.getDefaultToolkit().getScreenSize()); // force table to use all space
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		scroll.getViewport().setBackground(Color.WHITE);
				
		add(addButtonPanel());
		add(Box.createVerticalStrut(30));
		add(addLabelPanel());
		add(scroll);
	}
	
	public int getNumColumns() { return colNames.length; }
	
	public void addResult(String [] summary) {
		rows.add(summary);
		theTable.revalidate();
		updateButtons();
	}
	
	private void removeSelectedSummaries(int [] selections) {
		for(int x=selections.length-1; x>=0; x--) {
			theParentFrame.removeResult(selections[x]);
			rows.remove(selections[x]);
		}
		theTable.clearSelection();
		theTable.revalidate();
		updateButtons();
	}
	
	private void removeAllSummaries() {
		int numResults = rows.size();
		
		for(int x=0; x < numResults; x++) {
			theParentFrame.removeResult(0);
		}
		rows.clear();
		
		theTable.clearSelection();
		theTable.revalidate();
		updateButtons();
	}
	
	private void updateButtons() {
		if(theTable.getSelectedRows().length > 0)
			btnRemoveSelQueries.setEnabled(true);
		else
			btnRemoveSelQueries.setEnabled(false);						
		
		if(theTable.getRowCount() > 0)
			btnRemoveAllQueries.setEnabled(true);
		else
			btnRemoveAllQueries.setEnabled(false);
	}
	
	private JPanel addButtonPanel() {
		JPanel thePanel = new JPanel();
		thePanel.setLayout(new BoxLayout(thePanel, BoxLayout.LINE_AXIS));
		thePanel.setBackground(Color.WHITE);
		
		btnRemoveSelQueries = new JButton("Remove Selected Queries");
		btnRemoveSelQueries.setEnabled(false);
		btnRemoveSelQueries.setBackground(Color.WHITE);
		btnRemoveSelQueries.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				removeSelectedSummaries(theTable.getSelectedRows());
			}
		});
		
		btnRemoveAllQueries = new JButton("Remove All Queries");
		btnRemoveAllQueries.setEnabled(false);
		btnRemoveAllQueries.setBackground(Color.WHITE);
		btnRemoveAllQueries.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				removeAllSummaries();
			}
		});
		
		btnLoadQuery = new JButton("Load Saved Query");
		btnLoadQuery.setBackground(Color.WHITE);
		btnLoadQuery.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				theParentFrame.addResult(new ListDataPanel(theParentFrame, ListDataPanel.VIEW_MODE_LOCAL));
			}
		});
		
		thePanel.add(btnRemoveSelQueries);
		thePanel.add(Box.createHorizontalStrut(20));
		thePanel.add(btnRemoveAllQueries);
		thePanel.add(Box.createHorizontalStrut(20));
		thePanel.add(btnLoadQuery);
		
		thePanel.setMaximumSize(thePanel.getPreferredSize());
		thePanel.setAlignmentX(LEFT_ALIGNMENT);
		
		return thePanel;
	}
	
	private JPanel addLabelPanel() {
		JPanel thePanel = new JPanel();
		thePanel.setLayout(new BoxLayout(thePanel, BoxLayout.PAGE_AXIS));
		thePanel.setBackground(Color.WHITE);
		
		JLabel headerLine = new JLabel("List Queries");
		headerLine.setAlignmentX(LEFT_ALIGNMENT);
		JTextArea instructions = new JTextArea(	"Remove All Queries also removes them from the left panel\n" +
													"Except for description, the remaining columns no longer function");
				
		instructions.setEditable(false);
		instructions.setBackground(getBackground());
		instructions.setAlignmentX(LEFT_ALIGNMENT);
		
		thePanel.add(headerLine);
		thePanel.add(Box.createVerticalStrut(5));
		thePanel.add(instructions);
		thePanel.setMaximumSize(thePanel.getPreferredSize());
		thePanel.setAlignmentX(LEFT_ALIGNMENT);
		thePanel.add(Box.createVerticalStrut(10));
		
		return thePanel;
	}
	//Needed for summary updates
	Thread updateThread = null;

	private JButton btnRemoveSelQueries = null;
	private JButton btnRemoveAllQueries = null;
	private JButton btnLoadQuery = null;
	
    private JTable theTable = null;
	private JScrollPane scroll = null;

	private SyMAPQueryFrame theParentFrame = null;
	private String[] colNames = null;
	private Vector<String []> rows = null;

	private class ResultsTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 774460555629612058L;

		public int getColumnCount() {
            return colNames.length;
        }

        public int getRowCount() {
            return rows.size();
        }
        
        public Object getValueAt(int row, int col) {
            String [] r = rows.elementAt(row);
            return r[col];
        }
        
        public String getColumnName(int col) {
            return colNames[col];
        }
	}


}

