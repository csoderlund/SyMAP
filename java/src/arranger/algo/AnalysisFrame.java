package arranger.algo;

import java.awt.*;
import java.awt.event.*;

@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class AnalysisFrame extends Frame implements MouseListener {
	// Controll components
	GR_applet m_caller;
	DrawingPanel drawPnl = new DrawingPanel();
	Button closeBtn = new Button("Close");
	Button nextBtn = new Button("Next");
	
	public AnalysisFrame(String title, GR gr, GR_applet caller) throws Exception { 
		super(title);		
		m_caller = caller;
		initForm();
		showAnalysis(gr);
		setVisible(true);
		//show(); // mdb removed 7/2/07 #118
		setVisible(true); // mdb added 7/2/07 #118
		this.pack();
		this.setSize(500, 500);
	}
	
	private void initForm() {
		add(drawPnl);
		closeBtn.addMouseListener(this);				
		nextBtn.addMouseListener(this);				
	}

	public void showAnalysis(GR gr) throws Exception {
		drawPnl.setGR(gr);
		drawPnl.add(closeBtn);
		drawPnl.add(nextBtn);
		drawPnl.repaint();
	}
	
	public void paint(Graphics g) { drawPnl.repaint(); }
	
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {
		try {
			Component comp = e.getComponent();
			if(comp.equals(closeBtn)) {
				dispose();
				m_caller.analysisFrameClosed();
			}
			if(comp.equals(nextBtn))
				m_caller.runNextStage();
		} catch(Exception e1) { System.out.println(e1.getMessage());}
	}

	class DrawingPanel extends Panel {
				
		GR m_gr;
		Report m_report;		
		
		int m_report_min_x = 10;
		int m_report_min_y = 300;
		int m_report_space = 15;
		
		double m_alfa = 3 * java.lang.Math.PI / 4; // The angle of the arc
		int m_distance;
		int m_radius;
		Font m_font;
		int m_left_border = 40;
		int m_low_border = 70;
		
		int m_mikra_x = 600;
		int m_mikra_y = 420;
				
		public DrawingPanel() { super(); }
		public void setGR(GR gr) throws Exception { 
			m_gr = gr; 
			m_report = new Report(m_gr.getGraph());
			int size = gr.getPermutation().getSize();
			m_distance = 900 / size;
			m_radius = m_distance/3;
		}
		
		public void paint(Graphics graphics) {
			// Double buffering variables
			Image offscreenImage = createImage(getSize().width, getSize().height); 
			Graphics offscreenGraphics = offscreenImage.getGraphics(); 
			
			// Draw the background
			offscreenGraphics.setColor(Color.white);
			offscreenGraphics.fillRect(0, 0, getSize().width, getSize().height ); 
			
			Font font = offscreenGraphics.getFont();
			Font draw_font = new Font(font.getName(), font.getStyle(), 5 * m_radius / 2);
			try {
				m_report = new Report(m_gr.getGraph());
				showReport(m_report.getReport(), offscreenGraphics);
			} catch(Exception e) { System.out.println(e.getMessage()); }
			if(m_gr.getPermutation().getSize() > 82) {
				offscreenGraphics.drawString("Can't show graph because it is too big", 10, 100);
				graphics.drawImage(offscreenImage, 0, 0, this);
				return;
			}
			OVGraph graph = m_gr.getGraph();
			Permutation permutation = graph.getPermutation();
			// The number of vertices in the overlap graph (include the oriented components)
			int vertices_number = graph.getNumOfVertices();
			try {
				for(int i=0; i<vertices_number; i++) {
					// Draw the edges (one black and one gray)
					if(graph.isBreakpoint(i)) // Gray edge from the INDEXES 2i, 2i+1 (and not the positions ew just drawed)
						this.drawGrayEdge(offscreenGraphics, graph.getLeftEndpoint(i), graph.getRightEndpoint(i), graph.isOriented(i));
						
					// Black edge
					if(graph.isBreakpoint(graph.getVertexIndexByIndex(graph.getIndex(2*i))))
						this.drawBlackEdge(offscreenGraphics, 2*i);
				}
				Color color;
				for(int i=0; i<vertices_number; i++) {
					// Draw the vertices					
					if(graph.isBreakpoint(graph.getVertexIndexByIndex(graph.getIndex(2*i))))
						color = Color.blue;
					else
						color = Color.green;
					drawVertex(offscreenGraphics, 2*i, permutation.getIndex(2*i), color, draw_font);
					drawVertex(offscreenGraphics, 2*i + 1, permutation.getIndex(2*i + 1), color, draw_font);
				}
				C_component[] comps = graph.getComponents();
				int[] vertices;
				// Paint the unoriented components in 
				int[] unoriented = graph.getUnorientedIndexes();
				for(int i=0; i<unoriented.length; i++) {
					vertices = comps[unoriented[i]].getVerticesIndexes();
					for(int j=0; j<vertices.length; j++) {
						drawVertex(offscreenGraphics, graph.getPosition(2*vertices[j]), 2*vertices[j], Color.orange, draw_font);
						drawVertex(offscreenGraphics, graph.getPosition(2*vertices[j]+1), 2*vertices[j]+1, Color.orange, draw_font);
					}
				}
				// Paint the hurdles in red
				int[] hurdles = graph.getHurdlesIndexes();
				for(int i=0; i<hurdles.length; i++) {
					vertices = comps[hurdles[i]].getVerticesIndexes();
					for(int j=0; j<vertices.length; j++) {
						drawVertex(offscreenGraphics, graph.getPosition(2*vertices[j]), 2*vertices[j], Color.red, draw_font);
						drawVertex(offscreenGraphics, graph.getPosition(2*vertices[j]+1), 2*vertices[j]+1, Color.red, draw_font);
					}
				}
				// Paint the super hurdles in black
				int[] super_hurdles = graph.getSuperHurdlesIndexes();
				for(int i=0; i<super_hurdles.length; i++) {
					vertices = comps[super_hurdles[i]].getVerticesIndexes();
					for(int j=0; j<vertices.length; j++) {
						drawVertex(offscreenGraphics, graph.getPosition(2*vertices[j]), 2*vertices[j], Color.black, draw_font);
						drawVertex(offscreenGraphics, graph.getPosition(2*vertices[j]+1), 2*vertices[j]+1, Color.black, draw_font);
					}
				}
				offscreenGraphics.setFont(font);
				addDescription(offscreenGraphics);
			} catch(Exception e) { System.out.println(e.getMessage()); }
			graphics.drawImage(offscreenImage, 0, 0, this);
		}
	    // Overide the update method to reduce flicker.   
		public void update(Graphics g) { paint(g); }
		
		private void addDescription(Graphics g) {
			Font f = g.getFont();
			Font nf = new Font(f.getName(), f.getStyle(), 10);
			g.setFont(nf);
			int x = m_mikra_x;
			int y = m_mikra_y;
			g.setColor(Color.black);
			g.drawString("The different kinds of vertices/edges are specified by colors:", x, y);
			
			y += m_report_space;
			g.setColor(Color.black);
			g.drawString("Edges:", x, y);
			
			y += m_report_space;
			g.setColor(Color.gray);
			g.drawLine(x, y-5, x+30, y-5);
			g.setColor(Color.black);
			g.drawString("Oriented edge", x+40, y);

			y += m_report_space;
			g.setColor(Color.blue);
			g.drawLine(x, y-5, x+30, y-5);
			g.setColor(Color.black);
			g.drawString("Unoriented edge", x+40, y);
			
			y += m_report_space;
			g.setColor(Color.black);
			g.drawString("Vertices:", x, y);

			y += m_report_space;
			g.setColor(Color.red);
			g.fillOval(x, y-10, 10, 10);
			g.setColor(Color.black);
			g.drawString("Hurdle", x+20, y);

			y += m_report_space;
			g.setColor(Color.black);
			g.fillOval(x, y-10, 10, 10);
			g.setColor(Color.black);
			g.drawString("Super hurdle", x+20, y);
			
			y += m_report_space;
			g.setColor(Color.green);
			g.fillOval(x, y-10, 10, 10);
			g.setColor(Color.black);
			g.drawString("Adjancy", x+20, y);
			
			y += m_report_space;
			g.setColor(Color.orange);
			g.fillOval(x, y-10, 10, 10);
			g.setColor(Color.black);
			g.drawString("Unoriented", x+20, y);

			y += m_report_space;
			g.setColor(Color.blue);
			g.fillOval(x, y-10, 10, 10);
			g.setColor(Color.black);
			g.drawString("Oriented", x+20, y);
		}
			
			
		
		
		private void drawVertex(Graphics g, int position, int index, Color color, Font font) {
			int x = x_location(position) - m_radius;
			int y = y_location(position) - m_radius;
			g.setColor(Color.black);
			g.drawOval(x, y, 2*m_radius, 2*m_radius);
			g.setColor(Color.black);
			g.setFont(font);
			if(index > 9)
				g.drawString(""+index, x-(m_radius/2), y - m_radius);
			else
				g.drawString(""+index, x+(m_radius/2), y - m_radius);
			
			g.setColor(color);
			g.fillOval(x+1, y+1, 2*m_radius-1, 2*m_radius-1);
		}

		private void drawGrayEdge(Graphics g, int position1, int position2, boolean oriented) {
			int x1 = x_location(position1);
			int x2 = x_location(position2);
			int y12 = y_location(position1);
			int distance = x2 - x1;			
			double radius = distance / (2 * Math.sin(m_alfa/2));
			int int_radius =(int)radius;
			int x = ((x1 + x2) / 2); // The coordinates of the circle center
			int y = (int)(y12 - (radius * Math.cos(m_alfa/2)));
			int start_angle = (int)(270 - (180/Math.PI * (m_alfa/2)));
			int angle = (int)(m_alfa * (180/Math.PI));
			if(oriented) g.setColor(Color.black);
			else g.setColor(Color.blue);
			g.drawArc(x - int_radius, y - int_radius, 2 * int_radius, 2 * int_radius, start_angle, angle);
		}
		
		private void drawBlackEdge(Graphics g, int start_position) {
			g.setColor(Color.black);
			int end_position = start_position + 1;
			g.drawLine(x_location(start_position) + m_radius, y_location(start_position), x_location(end_position) - m_radius, y_location(end_position));
		}
		// Specify the center of the vertex circle
		private int x_location(int vertex_position) { return (this.m_left_border + m_radius + ((vertex_position-1) * m_distance)); }
		private int y_location(int vertex_position) { return(this.m_low_border + m_radius); }
		
		public void showReport(String[] lines, Graphics g) {
			g.setColor(Color.black);
			for(int i=0; i<lines.length; i++)
				if(lines[i] != null) g.drawString(lines[i], m_report_min_x, m_report_min_y + m_report_space*i);
		}
		
	}
	class Report {
		OVGraph m_graph;
		Permutation m_permutation;
		C_component[] m_components;
		int m_cycles_number;
		int m_breakpoints_number;
		int m_hurdles_number;
		boolean m_fortress;
		int m_reversal_distance;
		
		Report(OVGraph graph) throws Exception {
			m_graph = graph;
			m_permutation = m_graph.getPermutation();
			m_cycles_number = m_permutation.getCyclesNumber();
			m_breakpoints_number = m_permutation.getBreakpointsNumber();
			m_hurdles_number = m_graph.getHurdlesNumber();
			m_fortress = m_graph.isFortress();
			m_reversal_distance = m_breakpoints_number + m_hurdles_number - m_cycles_number;			
			if(m_fortress)
				m_reversal_distance++;
			m_components = m_graph.getComponents();
		}
		
		public String[] getReport() throws Exception {
			String result[] = new String[10+m_components.length];
			int next_line = 0;
			result[next_line++] = new String("Breakpoints number is " + m_breakpoints_number);
			result[next_line++] = new String("Cycles number is " + m_cycles_number);
			result[next_line++] = new String("Hurdles number is " + m_hurdles_number);
			if(m_fortress) result[next_line++] = new String("The graph is a fortress");
			else result[next_line++] = new String("The graph is not a fortress");
			result[next_line++] = new String("The reversal distance is " + m_reversal_distance);
			
			int[] hurdles_indexes = m_graph.getHurdlesIndexes();
			int comps_index = 0;
						
			for(int i=0; i<m_components.length; i++) {
				if(m_components[i].isAdjancy())
					// Nothing to show
					continue;
				comps_index++;
				if(m_components[i].isOriented()) 
					result[next_line] = new String("Component (oriented) " + (comps_index) + ": ");
				if(m_components[i].isUnoriented()) 
					result[next_line] = new String("Component (unoriented) " + (comps_index) + ": ");
				if(m_components[i].isSimpleHurdle())
					result[next_line] = new String("Component (simple hurdle) " + (comps_index) + ": ");
				if(m_components[i].isSuperHurdle())
				   result[next_line] = new String("Component (super hurdle) " + (comps_index) + ": ");
				int[] vertices_indexes = m_components[i].getVerticesIndexes();
				for(int j=0; j<vertices_indexes.length; j++) {
					if(j > 0)
						result[next_line] = result[next_line] + ", ";
					result[next_line] = result[next_line] + "(" + 2*vertices_indexes[j] + "," + (2*vertices_indexes[j]+1) + ")";
				}
				next_line++;
			}
			result[next_line++] = new String("The overlap graph has " + comps_index + " connected components:");

			next_line++;
			if(hurdles_indexes.length == 0)
				result[next_line++] = "There are no hurdles. Next reversal will be according to a happy clique";
			else
				result[next_line++] = "Next reversal will be merging of two reversals (non conscutive if possible)";
			return result;
		}
	}	
}
