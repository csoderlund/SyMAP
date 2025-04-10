package symapQuery;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import java.util.Vector;

import util.Jcomp;

/**********************************************
 * Table of results
 */

public class ResultsPanel extends JPanel {
	private static final long serialVersionUID = -4532933089334778200L;

	protected ResultsPanel(QueryFrame parentFrame) {
		theQueryFrame = parentFrame;
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		setBackground(Color.WHITE);
		
		rows = new Vector<String []>();
	
		theTable = new JTable();	
		theTable.getTableHeader().setBackground(Color.WHITE);
		theTable.setColumnSelectionAllowed( false );
		theTable.setCellSelectionEnabled( false );
		theTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);// CAS562
		theTable.setRowSelectionAllowed( true );
		theTable.setShowHorizontalLines( false );
		theTable.setShowVerticalLines( true );	
		theTable.setIntercellSpacing ( new Dimension ( 1, 0 ) );		
		
		theTable.setModel(new ResultsTableModel());
		
		theTable.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				updateButtons();
				if (e.getClickCount() == 2) {
					int row = theTable.getSelectedRow();
					theQueryFrame.selectResult(row);		// 
				}
			}
		});
	      
		scroll = new JScrollPane(theTable);
		scroll.setBorder( null );
		scroll.setPreferredSize(java.awt.Toolkit.getDefaultToolkit().getScreenSize()); // force table to use all space
		scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		scroll.getViewport().setBackground(Color.WHITE);
		
		add(addButtonPanel());
		add(Box.createVerticalStrut(10));
		add(addLabelPanel());
		add(scroll);
	}
	
	protected void addResultText(String [] summary) {
		rows.add(summary);
		theTable.revalidate();
		updateButtons();
	}
	
	private void removeSelectedSummaries(int [] selections) {
		for(int x=selections.length-1; x>=0; x--) {
			theQueryFrame.removeResult(selections[x]);
			rows.remove(selections[x]);
		}
		theTable.clearSelection();
		theTable.revalidate();
		updateButtons();
		
		if (rows.size()==0) theQueryFrame.resetCounter(); 
	}
	
	private void removeAllSummaries() {
		int numResults = rows.size();
		
		for(int x=0; x < numResults; x++) 
			theQueryFrame.removeResult(0);
		rows.clear();
		
		theTable.clearSelection();
		theTable.revalidate();
		updateButtons();
		
		theQueryFrame.resetCounter(); 
	}
	
	private void updateButtons() {
		boolean b = (theTable.getSelectedRows().length > 0);
		btnRemoveSelQueries.setEnabled(b);
							
		b = (theTable.getRowCount() > 0);
		btnRemoveAllQueries.setEnabled(b);
	}
	
	private JPanel addButtonPanel() {
		JPanel thePanel = Jcomp.createRowPanel();
		
		btnRemoveSelQueries = Jcomp.createButton("Remove Selected", false);
		btnRemoveSelQueries.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				removeSelectedSummaries(theTable.getSelectedRows());
			}
		});
		btnRemoveAllQueries = Jcomp.createButton("Remove All", false);
		btnRemoveAllQueries.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				removeAllSummaries();
			}
		});
		thePanel.add(btnRemoveSelQueries); thePanel.add(Box.createHorizontalStrut(20));
		thePanel.add(btnRemoveAllQueries); thePanel.add(Box.createHorizontalStrut(20));
		
		thePanel.setMaximumSize(thePanel.getPreferredSize());
		thePanel.setAlignmentX(LEFT_ALIGNMENT);
		
		return thePanel;
	}
	
	private JPanel addLabelPanel() {
		JPanel thePanel = Jcomp.createPagePanel();

		JTextArea instructions = new JTextArea(
				"Select one or more rows, then 'Remove Selected'; this removes the result from the left panel."
				+ "\nDouble click a row to view the result.");	
		instructions.setEditable(false);
		instructions.setBackground(getBackground());
		instructions.setAlignmentX(LEFT_ALIGNMENT);
		
		thePanel.add(instructions);
		thePanel.setMaximumSize(thePanel.getPreferredSize());
		thePanel.setAlignmentX(LEFT_ALIGNMENT);
		thePanel.add(Box.createVerticalStrut(10));
		
		return thePanel;
	}
	
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
	
	private JButton btnRemoveSelQueries = null;
	private JButton btnRemoveAllQueries = null;
	
    private JTable theTable = null;
	private JScrollPane scroll = null;
	private Vector<String []> rows = null;
	private String [] colNames = { "Result", "Filters" };
	
	private QueryFrame theQueryFrame = null;
}

