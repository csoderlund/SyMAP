package arranger.algo;

// A list of integers
public class List {
	
	// Data members
	int m_size;
	ListElem m_elements;

	// Methods
	
	// constructor
	public List() {
		m_size = 0;
		m_elements = null;
	}
	
	public void add(int value) {
		m_elements = new ListElem(value, m_elements);
		m_size++;
	}
	
	public boolean ordered() { return m_elements.ordered(); }
	public ListElem getElements() { return m_elements; }
	
	public int[] getElementsInArrayByInsertionOrder() {
		ListElem temp = this.getElements();
		int[] result = new int[m_size];
		for(int i=0; i<m_size; temp=temp.getNext(), i++)
			result[m_size-1-i] = temp.getValue();
		return result;
	}
	public int[] getElementsInArrayNotByInsertionOrder() {
		ListElem temp = this.getElements();
		int[] result = new int[m_size];
		for(int i=0; i<m_size; temp=temp.getNext(), i++)
			result[i] = temp.getValue();
		return result;
	}
}

