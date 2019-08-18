package arranger.algo;

import java.awt.*;
import java.applet.*;
import java.awt.event.*;

/*
 * This class reads PARAM tags from its HTML host page and sets
 * the color and label properties of the applet. Program execution
 * begins with the init() method.
 */
@SuppressWarnings("serial") // Prevent compiler warning for missing serialVersionUID
public class GR_applet extends Applet implements MouseListener, KeyListener, ItemListener {
	final static int VERTICAL = 1;
	final static int HORIZONTAL = 2;

	final static int FIRST_COLUMN = 0;
	final static int INSTRUCTION_LINE = 1;
	final static int COLUMNS_NUMBER = 10;
	final static int INITIALIZATION_LINE = 2;
	final static int PARAMETERS_LINE = 3;
	final static int ORIGINAL_PERMUTATION_LINE = 4;
	final static int PREVIOUS_PERMUTATION_LINE = 5;
	final static int CURRENT_PERMUTATION_LINE = 6;
	final static int BUTTONS_LINE = 7;
	final static int LOG_LINE = 8;
	final static int CLEAR_LINE = 9;

//	final static int MAX_PERMUTATION_SPACE = 8;
	final static int MAX_REPORTS = 100;
	
	// General stages (for all modes)
	final static int INIT_STAGE = 1;
	final static int MID_STAGE = 3;
	final static int DONE_STAGE = 2;
	// General modes
	final static int REGULAR_MODE = 1;
	final static int STATISTICS_MODE = 2;
	final static int NO_MODE = 3;

	GridBagLayout m_layout = new GridBagLayout();

	// Data members
	GR m_gr;
	Permutation m_permutation;
	Permutation m_last_permutation = null; // For giving the user the option to work twice on the same permutation
	AnalysisFrame m_analysis_frame;
	int m_reversals_number = 0;
	int m_mode = REGULAR_MODE;
	boolean m_first_run = true;
	
	// Components
	// Instructions label
	Panel instructionPnl = new Panel(new FlowLayout(FlowLayout.LEFT, 15, 5));
	Label instructionLbl = new Label("This is the instruction field. All the instructions will appear here");
	// Initialization components
	Panel initPnl = new Panel(new FlowLayout(FlowLayout.LEFT, 20, 5));
	LabeledChoice modeChc = new LabeledChoice("Mode", this, this, HORIZONTAL);
	LabeledChoice permutationChc = new LabeledChoice("Permutation", this, this, HORIZONTAL);
	Panel parametersPnl = new Panel(new FlowLayout(FlowLayout.LEFT, 20, 5)); 
	LabeledTextField sizeTxt = new LabeledTextField("Size", 3, this, HORIZONTAL);
	LabeledTextField reversalsNumTxt = new LabeledTextField("Reversals", 3, this, HORIZONTAL);
	LabeledTextField instancesNumberTxt = new LabeledTextField("Instances", 3, this, HORIZONTAL);
		
	// Original permutation (signed)
	Panel originalPnl = new Panel(new FlowLayout(FlowLayout.LEFT, 20, 5));
	LabeledTextArea origPermutationTxt = new LabeledTextArea("Original permutation", 1, 47, TextArea.SCROLLBARS_HORIZONTAL_ONLY, this, VERTICAL);
	Button submitBtn = new Button("Submit");

	// Previous permutation information
	Panel previousPnl = new Panel(new FlowLayout(FlowLayout.LEFT, 20, 0));
	LabeledTextArea previousPermutationTxt = new LabeledTextArea("Previous permutation", 1, 45, TextArea.SCROLLBARS_HORIZONTAL_ONLY, this, VERTICAL);
	LabeledTextField lastReversalTxt = new LabeledTextField("Last reversal", 10, this, VERTICAL);

	// Current permutation (unsigned)
	Panel currentPnl = new Panel(new FlowLayout(FlowLayout.CENTER, 20, 0));
	LabeledTextArea currentPermutationTxt = new LabeledTextArea("Current permutation", 1, 62, TextArea.SCROLLBARS_HORIZONTAL_ONLY, this, VERTICAL);

	// General buttons
	Panel buttonsPnl = new Panel(new FlowLayout(FlowLayout.CENTER, 20, 0));
	// Buttons to deal with the current permutation
	Button nextBtn = new Button("Next  ");
	Button runBtn = new Button("Run  ");
	Button analyzeBtn = new Button("Analyze");
	// Submiting the next reversal
	Button userReversalBtn = new Button("User Reversal");
	LabeledChoice fromChc = new LabeledChoice("From ", this, this, VERTICAL);
	LabeledChoice toChc = new LabeledChoice("To      ", this, this, VERTICAL);

	// Log text area
	Panel logPnl = new Panel(new FlowLayout(FlowLayout.CENTER, 20, 0));
	LabeledTextArea logTxt = new LabeledTextArea("History", 8, 62, TextArea.SCROLLBARS_VERTICAL_ONLY, this, VERTICAL);

	Panel clearPnl = new Panel(new FlowLayout(FlowLayout.CENTER, 20, 10));
	Button clearBtn = new Button("Clear");
	/*
	 * The entry point for the applet. 
	 */
	public void init() {
		initForm();
		//      usePageParams();
	}

	//  private final String labelParam = "label";
	//private final String backgroundParam = "background"; // mdb removed 6/29/07 #118
	//private final String foregroundParam = "foreground"; // mdb removed 6/29/07 #118

	/*
	 * Intializes values for the applet and its components
	 */
	void initForm() {
		this.setBackground(Color.lightGray);
		this.setForeground(Color.black);
		this.setLayout(m_layout);
		this.setSize(490, 550);

		arrangeComponents();
		fillPermutationsChc();
		fillModeChc();
		handleListeners();
		
		setMode(INIT_STAGE);
		updateMode();
	}
	
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseClicked(MouseEvent e) {
		Component comp = e.getComponent();
		try {
			if(comp.equals(submitBtn))
				runInitStage();
			if(comp.equals(nextBtn))
				runNextStage();
			if(comp.equals(userReversalBtn))
				reversalUserChoice();
			if(comp.equals(analyzeBtn))
				analyzeCurrentPermutation();
			if(comp.equals(runBtn))
				runToEnd();
			if(comp.equals(clearBtn))
				clearAll();
		} catch (Exception e1) { handleException(e1); }
	}
	
	/* *************** MAIN OPERATIONS ************** */
	
	private void runInitStage() throws Exception {
		String mode = modeChc.getSelectedItem();
		if(mode.equalsIgnoreCase("Statistics"))
		   runStatistics();
		// Initialization stage. Generate the permutation
		String permutation_kind = permutationChc.getSelectedItem();
		// Check the permutation kind
		if(permutation_kind.equalsIgnoreCase("User permutation"))
			usePermutation(userPermutation());
		if(permutation_kind.equalsIgnoreCase("Random permutation"))
			usePermutation(randomPermutation());
		if(permutation_kind.equalsIgnoreCase("Advanced random permutation"))
			usePermutation(advancedRandomPermutation());
		if(permutation_kind.equalsIgnoreCase("Last permutation"))
			usePermutation(m_last_permutation);
		this.analyzeBtn.setEnabled(true);
	}
	
	private void usePermutation(Permutation permutation) throws Exception {
		if(m_first_run) { // First permutation
			m_first_run = false;
			permutationChc.addItem("Last permutation");
		}
		m_last_permutation = new Permutation(permutation);
		this.showOriginalPermutation(permutation, origPermutationTxt);
		showPermutation(permutation, this.currentPermutationTxt);
		updateFromToChc(permutation);
		m_permutation = permutation;
		m_gr = new GR(permutation);
		setMode(MID_STAGE);
	}

	
	private Permutation advancedRandomPermutation() throws Exception {
		int size = getSizeParameter();
		int reversals_num = getReversalsNumberParameter();
		return new Permutation(size, reversals_num);
	}
	private int getReversalsNumberParameter() throws Exception {
		try { 
			int reversals_num = Integer.parseInt(reversalsNumTxt.getText()); 
			return reversals_num;
		} catch(NumberFormatException e) { throw new NumberFormatException("Invalid reversals number"); }			
	}
		
	private int getSizeParameter() throws Exception {		
		try { 
			int size = Integer.parseInt(sizeTxt.getText()); 
			return size;
		} catch(NumberFormatException e) { throw new NumberFormatException("Invalid size"); }			
	}
	private int getInstancesNumber() throws Exception {		
		try { 
			int instances_number = Integer.parseInt(instancesNumberTxt.getText()); 
			return instances_number;
		} catch(NumberFormatException e) { throw new NumberFormatException("Invalid size"); }			
	}
		
	private Permutation randomPermutation() throws Exception {
		int size = this.getSizeParameter();
		return new Permutation(size);
	}
	private Permutation userPermutation() throws Exception {
		int size = this.getSizeParameter();
		try { return getPermutation(this.origPermutationTxt, size); }
		catch(Exception e) { throw new Exception("Invalid permutation"); }
	}
	
	public  void runNextStage() throws Exception {
		// Create a copy of te permutation to print the previous permutation
		this.showPermutation(m_permutation, this.previousPermutationTxt);
		// Run one step
		Reversal reversal = m_gr.runStep();
		if(reversal == null) { // Done
			if(m_analysis_frame != null)
				m_analysis_frame.nextBtn.setEnabled(false);
			log("Done");			
			this.setMode(DONE_STAGE);
			return;
		}
		// else
		m_reversals_number++;
		log("Reversal " + m_reversals_number + " " + reversal.toString(m_permutation, true));
		lastReversalTxt.setText(reversal.toString(m_permutation, true));
		showPermutation(m_permutation, currentPermutationTxt);
		updateFromToChc(m_permutation);
		if(m_analysis_frame != null)
			m_analysis_frame.repaint();
	}
	
	private void reversalUserChoice() throws Exception { userReversal(); }
	private void userReversal() throws Exception {
		this.showPermutation(m_permutation, this.previousPermutationTxt);
		Reversal reversal = getUserReversal();
		this.m_gr.makeReversal(reversal);
		m_reversals_number++;
		this.lastReversalTxt.setText(reversal.toString(m_permutation, true));
		showPermutation(m_permutation, currentPermutationTxt);
		log("User reversal " + m_reversals_number + " " + reversal.toString(m_permutation, true));
		this.updateFromToChc(m_permutation);
		if(m_analysis_frame != null)
			this.m_analysis_frame.repaint();
	}
		
	private void analyzeCurrentPermutation() throws Exception {
		analyzeBtn.setEnabled(false);
		m_analysis_frame = new AnalysisFrame("Permutation Analysis", m_gr, this);
	}
	
	public void analysisFrameClosed() { analyzeBtn.setEnabled(true); }

	private void runToEnd() throws Exception {
		// Delete irelevant data
		this.previousPermutationTxt.setText("");
		this.lastReversalTxt.setText("");
		
		Reversal[] reversals = m_gr.run();
		for(int i=0; i<reversals.length; i++) {
			m_reversals_number++;
			log("Reversal " + m_reversals_number + " " + reversals[i].toString());
		}
		log("Done");
		this.setMode(DONE_STAGE);
	}
	
	private void runStatistics() throws Exception {
		int instances_number = this.getInstancesNumber();
		int permutation_size = getSizeParameter();
		Statistics statistics = new Statistics();
		int show_frequency = instances_number / MAX_REPORTS;
		
		if(permutationChc.getSelectedItem().equalsIgnoreCase("Random permutation")) {
			for(int i=1; i<=instances_number; i++)
				runStatisticsOnOnePermutation(statistics, new Permutation(permutation_size), (i == 1) || ((i%show_frequency)==0));
		} else {
			int reversals_number = this.getReversalsNumberParameter();
			for(int i=0; i<instances_number; i++)
				runStatisticsOnOnePermutation(statistics, new Permutation(permutation_size, reversals_number), (i == 1) || ((i%show_frequency)==0));
		}
		// Done running
		log("\nTotal summary:");
		log(statistics.getSummary());
	}
	
	private void runStatisticsOnOnePermutation(Statistics statistics, Permutation permutation, boolean show) throws Exception {
		String report = statistics.getGraphStatistics(new OVGraph(permutation));
		if(show) log(report);
		
	}
	
	private void clearAll() { setMode(INIT_STAGE); }

	void showPermutation(Permutation permutation, LabeledTextArea text) { text.setText(permutation.toString()); }
	
	void showOriginalPermutation(Permutation permutation, LabeledTextArea text) {
		text.setText(permutation.origToString());
	}
	

	//  void showPermutation(Permutation permutation, LabeledTextArea text, int start_mark, int end_mark)   {
	//      showPermutation(permutation, text);
	//      int start_pos = permutation.getSpaceSize(0, start_mark);
	//      int end_pos = permutation.getSpaceSize(0, end_mark);
	//  }

	Permutation getPermutation(LabeledTextArea text, int size) throws Exception {
		return new Permutation(size, text.getText());
	}

	Reversal getUserReversal() throws Exception {
		int start_index = getReversalStartParameter();
		int end_index = getReversalEndParameter();
		return new Reversal(m_permutation.getPosition(start_index), m_permutation.getPosition(end_index));
	}
	
	private int getReversalStartParameter() throws Exception {
		try {
			int from = Integer.parseInt(this.fromChc.getSelectedItem());
			return from;
		} catch(NumberFormatException e) { throw new NumberFormatException("Invalid from parameter"); }
	}

	private int getReversalEndParameter() throws Exception {
		try {
			int to = Integer.parseInt(this.toChc.getSelectedItem());
			return to;
		} catch(NumberFormatException e) { throw new NumberFormatException("Invalid to parameter"); }
	}
										   

				
	

	void handleException(Exception e) {
		System.out.println(e.getMessage());
		e.printStackTrace();
		new ExceptionDialog("Exception", true, e);
		this.clearAll();
	}
	
	
	
	
	public void keyPressed(KeyEvent e) {}
	public void keyReleased(KeyEvent e) {}
	/* This method is for trying to help the user by responsing him typing 
	Enter key. We try to guess his meaning and if there is an exception 
	(might be because the user did not fill the needed parameters) we ignore 
	the operation with no harm */
	public void keyTyped(KeyEvent e) {}
	
	public void itemStateChanged(ItemEvent e) {
		Object comp = e.getSource();
		if(comp.equals(permutationChc.m_choice))
			updatePermutationKind();
		if(comp.equals(modeChc.m_choice))
			updateMode();
		if(comp.equals(toChc.m_choice))
			updateFromChc();
		if(comp.equals(fromChc.m_choice))
			updateToChc();
	}
	
	private void updateFromChc() {
		try { 
			int to = m_permutation.getPosition(Integer.parseInt(toChc.getSelectedItem()));
			this.fillFromChc(m_permutation, 1, to-1); 
		} catch(Exception e) {}
	}

	private void updateToChc() {
		try { 
			int from = m_permutation.getPosition(Integer.parseInt(fromChc.getSelectedItem()));
			this.fillToChc(m_permutation, from+1, m_permutation.getSize()-2); 
		} catch(Exception e) {}
	}

	private void updateMode() {
		String mode = modeChc.getSelectedItem();
		String permutation = permutationChc.getSelectedItem();
		if(mode.equalsIgnoreCase("Statistics") && (m_mode != STATISTICS_MODE)) {
			permutationChc.clear();
			permutationChc.addItem("Random Permutation");
			permutationChc.addItem("Advanced Random Permutation");
			permutationChc.select("Random Permutation"); // default
			instancesNumberTxt.relevant(true, "100");
			this.instruction("Choose permutation kind and enter instances number");
			m_mode = STATISTICS_MODE;
		} 
		if(mode.equalsIgnoreCase("Regular") && (m_mode != REGULAR_MODE)) {
			permutationChc.clear();
			permutationChc.addItem("User permutation");
			permutationChc.addItem("Random Permutation");
			permutationChc.addItem("Advanced Random Permutation");
			if(m_first_run == false)
				permutationChc.addItem("Last permutation");
			permutationChc.select("User Permutation");
			instancesNumberTxt.relevant(false, "");
			m_mode = REGULAR_MODE;
		}
		// Try to select the previous permutation kind
		try { permutationChc.select(permutation); } catch(Exception e) {}		
		updatePermutationKind();
	}		
		
	private void updatePermutationKind() {
		String permutation_kind = permutationChc.getSelectedItem();
		// Initialize the text fields
		sizeTxt.relevant(false, "");
		reversalsNumTxt.relevant(false, "");
		origPermutationTxt.relevant(false, "");
		
		if(permutation_kind.equalsIgnoreCase("Last permutation")) {
			instruction("Press Submit");
			return;
		}
		sizeTxt.relevant(true, "20");
		if(permutation_kind.equalsIgnoreCase("Random permutation")) {
			instruction("Enter size (possitive integer) and press Submit");
			return;
		}
		if(permutation_kind.equalsIgnoreCase("User permutation")) {
			instruction("Enter size (possitive integer) and your permutation (+-1..+-size) and press Submit");
			// Default permutation
			this.origPermutationTxt.relevant(true, "5 1 3 2 4 6 11 7 9 8 10 12 17 13 15 14 16 18 -19 20");
			return;
		}
		if(permutation_kind.equalsIgnoreCase("Advanced random permutation")) {
			instruction("Enter size and reversals number (possitive integers) and press Submit");
			this.reversalsNumTxt.relevant(true, "10");
		}
	}
				
	// High level function to handle the status of the applet
	private void setMode(int stage) {
		if(stage == INIT_STAGE) {
			m_permutation = null;
			m_gr = null;
			m_reversals_number = 0;
			if(m_analysis_frame != null) {
				m_analysis_frame.dispose();
				m_analysis_frame = null;
			}
			m_mode = NO_MODE;			
		}
		setComponents(stage);
	}

	
	
	private void log(String message) { logTxt.append(message + "\n"); }
	private void instruction(String message) { this.instructionLbl.setText(message); }
	
	
	
	
	
	
	
	/* **************** UNINTERESTING COMPONENTS STAFF ************** */
		
	private void fillPermutationsChc() {
		permutationChc.clear();
		permutationChc.addItem("User permutation");
		permutationChc.addItem("Random permutation");
		permutationChc.addItem("Advanced random permutation");			
	}
	
	private void fillModeChc() {
		modeChc.clear();
		modeChc.addItem("Regular");
		modeChc.addItem("Statistics");
	}

	private void handleListeners() {    
		modeChc.addItemListener(this);
		permutationChc.addItemListener(this);
		fromChc.addItemListener(this);
		toChc.addItemListener(this);
		submitBtn.addMouseListener(this);
		nextBtn.addMouseListener(this);
		runBtn.addMouseListener(this);
		analyzeBtn.addMouseListener(this);
		userReversalBtn.addMouseListener(this);
		clearBtn.addMouseListener(this);		
	}
	
	private void fillFromChc(Permutation permutation, int from, int to) throws Exception {
		int size = permutation.getSize();
		if((from >= size) ||
		   (to >= size) ||
		   (from > to) ||
		   ((to % 2) == 0) ||
		   ((from % 2) == 0)) throw new Exception("GR_applet::fillFromChc - invalid parameters");
		String item = fromChc.getSelectedItem();
		fromChc.clear();
		for(int i=from; i<=to; i+=2)
			fromChc.addItem("" + permutation.getIndex(i));
		try { fromChc.select(item); } catch(Exception e) {}
	}

	private void updateFromToChc(Permutation permutation) throws Exception {
		fillFromChc(permutation, 1, permutation.getSize()-3);
		fillToChc(permutation, 2, permutation.getSize()-2);
	}

	private void fillToChc(Permutation permutation, int from, int to) throws Exception {
		int size = permutation.getSize();
		if((from >= size) ||
		   (to >= size) ||
		   (from > to) ||
		   ((to % 2) == 1) ||
		   ((from % 2) == 1)) throw new Exception("GR_applet::fillToChc - invalid parameters");
		String item = toChc.getSelectedItem();
		toChc.clear();
		for(int i=from; i<=to; i+=2)
			toChc.addItem("" + permutation.getIndex(i));
		try { toChc.select(item); } catch(Exception e) {}
	}
	
	
	private void setComponents(int stage) {
		modeChc.setEnabled(stage == INIT_STAGE);
		permutationChc.setEnabled(stage == INIT_STAGE);
		sizeTxt.setEnabled(stage == INIT_STAGE);
		this.reversalsNumTxt.setEnabled(stage == INIT_STAGE);
		this.instancesNumberTxt.setEnabled((stage == INIT_STAGE) && (m_mode == STATISTICS_MODE));
		submitBtn.setEnabled(stage == INIT_STAGE);
		this.origPermutationTxt.setEnabled(true);
		
		this.currentPermutationTxt.setEnabled(true);
		nextBtn.setEnabled(stage == MID_STAGE);
		runBtn.setEnabled(stage == MID_STAGE);
		userReversalBtn.setEnabled(stage == MID_STAGE);

		switch(stage) {
		case INIT_STAGE:
			this.analyzeBtn.setEnabled(false);
			this.sizeTxt.setText("");
			this.reversalsNumTxt.setText("");
			this.instancesNumberTxt.setText("");
			this.fromChc.clear();
			this.toChc.clear();
			this.origPermutationTxt.setText("");
			this.previousPermutationTxt.setText("");
			this.lastReversalTxt.setText("");
			this.currentPermutationTxt.setText("");
			this.logTxt.setText("");
			instruction("Choose a mode and permutation kind and press Submit");
			this.updateMode();
			break;
		case MID_STAGE:
			instruction("Choose the next operation - Next / Run / Analyze / user Reversal (your reversal)");
			break;
		case DONE_STAGE:
			instruction("Press clear to start a new permutation");
			break;
		}
	}
	
	static void constrain(Container container, GridBagLayout layout, Component component, int x, int y, int width, int height, int weight_x, boolean fill) {
		GridBagConstraints cons = new GridBagConstraints();
		cons.gridx = x;
		cons.gridy = y;
		cons.gridheight = height;
		cons.gridwidth = width;
		cons.weightx = weight_x;
		
		cons.anchor = /*cons*/GridBagConstraints.WEST; 			// mdb changed 6/29/07 #118
		if(fill) cons.fill = /*cons*/GridBagConstraints.BOTH; 	// mdb changed 6/29/07 #118
		else cons.fill = /*cons*/GridBagConstraints.NONE; 		// mdb changed 6/29/07 #118
		container.add(component);
		layout.setConstraints(component, cons);
	}

	private void arrangeComponents() {
		/*this*/GR_applet.constrain(this, m_layout, instructionPnl, FIRST_COLUMN, INSTRUCTION_LINE, COLUMNS_NUMBER, 1, 1, true); // mdb changed 6/29/07 #118
		instructionPnl.add(instructionLbl);

		/*this*/GR_applet.constrain(this, m_layout, initPnl, FIRST_COLUMN, INITIALIZATION_LINE, COLUMNS_NUMBER, 1, 1, true); // mdb changed 6/29/07 #118
		initPnl.add(modeChc);
		initPnl.add(permutationChc);
		
		/*this*/GR_applet.constrain(this, m_layout, parametersPnl, FIRST_COLUMN, PARAMETERS_LINE, COLUMNS_NUMBER, 1, 1, true); // mdb changed 6/29/07 #118
		parametersPnl.add(sizeTxt);
		parametersPnl.add(reversalsNumTxt);
		parametersPnl.add(instancesNumberTxt);
		
		/*this*/GR_applet.constrain(this, m_layout, originalPnl, FIRST_COLUMN, ORIGINAL_PERMUTATION_LINE, 5, 1, 1, true); // mdb changed 6/29/07 #118
		originalPnl.add(origPermutationTxt);
		originalPnl.add(submitBtn);
		
		/*this*/GR_applet.constrain(this, m_layout, previousPnl, FIRST_COLUMN, PREVIOUS_PERMUTATION_LINE, 8, 1, 2, true); // mdb changed 6/29/07 #118
		previousPnl.add(this.previousPermutationTxt);
		previousPnl.add(this.lastReversalTxt);
		
		/*this*/GR_applet.constrain(this, m_layout, currentPnl, FIRST_COLUMN, CURRENT_PERMUTATION_LINE, COLUMNS_NUMBER, 1, 1, true); // mdb changed 6/29/07 #118
		currentPnl.add(currentPermutationTxt);
		
		/*this*/GR_applet.constrain(this, m_layout, buttonsPnl, FIRST_COLUMN, BUTTONS_LINE, COLUMNS_NUMBER, 1, 1, true); // mdb changed 6/29/07 #118
		// Fill the panel components
		buttonsPnl.add(nextBtn);
		buttonsPnl.add(runBtn);
		buttonsPnl.add(analyzeBtn);
		buttonsPnl.add(userReversalBtn);
		buttonsPnl.add(fromChc);
		buttonsPnl.add(toChc);
		buttonsPnl.add(new Label(""));
		
		/*this*/GR_applet.constrain(this, m_layout, logPnl, FIRST_COLUMN + 1, LOG_LINE, COLUMNS_NUMBER, 1, 1, true); // mdb changed 6/29/07 #118
		logPnl.add(logTxt);

		/*this*/GR_applet.constrain(this, m_layout, clearPnl, FIRST_COLUMN + 1, CLEAR_LINE, COLUMNS_NUMBER, 1, 1, true); // mdb changed 6/29/07 #118
		clearPnl.add(clearBtn);
	}
	
	/* ******************* HELPFUL COMPONENTS ****************** */
	
	class LabeledTextField extends Panel {
		Label m_label;
		public TextField m_text;
		
		LabeledTextField(String label, int size, KeyListener k, int order) {
			super(new BorderLayout());
			m_label = new Label(label);
			m_text = new TextField("", size);
			if(order == VERTICAL) {
				this.add("North", m_label);
				this.add("South", m_text);
			} else {
				this.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
				this.add(m_label);
				this.add(m_text);
			}				
		}
		void setText(String txt) { m_text.setText(txt); }
		String getText() { return m_text.getText(); }
		public void relevant(boolean b, String str) {			
			m_text.setText(str);
			m_text.setEnabled(b);
		}
		public void setEnabled(boolean b) { m_text.setEnabled(b); }
	}       

	class LabeledTextArea extends Panel {
		Label m_label;
		public TextArea m_text;
		
		LabeledTextArea(String label, int rows, int columns, int scrollbars, KeyListener k, int order) {
			super(new BorderLayout());
			m_label = new Label(label);
			m_text = new TextArea("", rows, columns, scrollbars);
			if(order == VERTICAL) {
				this.add("North", m_label);
				this.add("South", m_text);
			} else {
				this.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
				this.add(m_label);
				this.add(m_text);
			}
			m_text.addKeyListener(k);
		}
		void setText(String txt) { m_text.setText(txt); }
		String getText() { return m_text.getText(); }
		void append(String str) { m_text.append(str); }
		public void relevant(boolean b, String str) {
			m_text.setText(str);
			m_text.setEnabled(b);
		}
		public void setEnabled(boolean b) { m_text.setEnabled(b); }
		public void setEditable(boolean b) { m_text.setEditable(b); }
	}       

	class LabeledChoice extends Panel {
		Label m_label;
		public Choice m_choice;
		LabeledChoice(String label, KeyListener k, MouseListener m, int order) {
			super(new BorderLayout());
			m_label = new Label(label);
			m_choice = new Choice();
			if(order == VERTICAL) {
				this.add("North", m_label);
				this.add("South", m_choice);
			} else {
				this.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
				this.add(m_label);
				this.add(m_choice);
			}
			m_choice.addKeyListener(k);
			m_choice.addMouseListener(m);
		}
		void addItem(String item) { m_choice.addItem(item); }
		void removeItem(String item) { m_choice.remove(item); }
		public void setEnabled(boolean b) { m_choice.setEnabled(b); }
		int getItemCount() { return m_choice.getItemCount(); }
		String getSelectedItem() { return m_choice.getSelectedItem(); }
		void remove(String item) { m_choice.remove(item); }
		void clear() { m_choice.removeAll(); }
		void addItemListener(ItemListener I) { m_choice.addItemListener(I); }
		void select(String item) { m_choice.select(item); }
	}
	
	class ExceptionDialog extends Dialog implements MouseListener {
		Button closeBtn = new Button("Close");
		//MultiLineLabel messageLbl;  // mdb 2/14/07

		ExceptionDialog(String title, boolean modal, Exception exception) {
			super(new Frame(), title, true);
			Frame f = (Frame)this.getParent();
			f.setSize(400, 200);
			f.setVisible(true);
			
			setLayout(new BorderLayout(15, 15));			
			//String message = getMessage(exception); // mdb removed 6/29/07 #118
			//messageLbl = new MultiLineLabel(message, 20, 20); // mdb 2/14/07
			//add("Center", messageLbl);			            // mdb 2/14/07
			Panel buttonPnl = new Panel(new FlowLayout(FlowLayout.CENTER, 15, 15));
			buttonPnl.add(this.closeBtn);
			closeBtn.addMouseListener(this);
			add("South", buttonPnl);
			
			setSize(400, 200);
			setVisible(true);
		}
		
		public void mouseExited(MouseEvent e) {}
		public void mousePressed(MouseEvent e) {}
		public void mouseReleased(MouseEvent e) {}
		public void mouseEntered(MouseEvent e) {}
		public void mouseClicked(MouseEvent e) {
			if(e.getComponent() == closeBtn) { 
				Frame f = (Frame)this.getParent();
				f.setVisible(false);
				setVisible(false);
				dispose();
				f.dispose();				
			} 
		}

// mdb removed 6/29/07 #118
//		private String getMessage(Exception exception) {
//			String message = exception.getMessage();
//			if((message == null) || message.equalsIgnoreCase("")) message = new String("No message");
//			return new String(
//							  "An exception was thrown\n" + 
//							  "The exception type is " + exception.getClass().getName() + "\n" +
//							  "The exception message is " + message + "\n" +
//							  "Clearing permutation information");
//		}
	}
}

/* fortress
5 1 3 2 4 6 11 7 9 8 10 12 17 13 15 14 16 18
*/
