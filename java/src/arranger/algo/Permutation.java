package arranger.algo;

import java.util.*;

// Unsigned permutation only. Constructors get signed permutation and unsign it
public class Permutation {		
    private int[] m_original_permutation;
    private int[] m_permutation;
    private int[] m_reversed_permutation;
	
    public Permutation(int[] permutation) throws Exception {
	m_original_permutation = permutation;
	expend();
    }
		
    // Create a random permutation
    Permutation(int n) throws Exception {
	m_original_permutation = new int[n];
	Random rnd = new Random();
	for(int i=0 ;i<n; i++) {
	    int rand_int = rnd.nextInt();
	    rand_int = 1 + java.lang.Math.abs(rand_int % (n-i)); // 1..n-i
	    int rand_sign = java.lang.Math.abs(rnd.nextInt());
	    rand_sign = rand_sign % 2;
			
	    int j=1, k=0;
	    while((j < rand_int) || (m_original_permutation[j+k-1] != 0)) {
		if(m_original_permutation[j+k-1] != 0)
		    k++;
		else
		    j++;
	    }
	    if(rand_sign == 0)
		m_original_permutation[j+k-1] = i + 1;
	    else
		m_original_permutation[j+k-1] = -(i + 1);
	}
	expend();
    }
	
    public Permutation(int size, int reversals_num) throws Exception {
	if(size < 2) { throw new Exception("Permutation::Permutation - size is smaller than 2"); }
	m_original_permutation = new int[size];
	int temp;
	// Generate the unit permutation
	for(int i=0; i<size; i++)
	    m_original_permutation[i] = i + 1; // 1 2 3 4 5 ...
	// Make the reversals
	Random rnd = new Random();
	int pos1, pos2;
	for(int i=0; i<reversals_num; i++) {
	    pos1 = java.lang.Math.abs(rnd.nextInt()) % size;
	    pos2 = pos1;
	    while(pos1 == pos2) { pos2 = java.lang.Math.abs(rnd.nextInt()) % size; }
	    if(pos2 < pos1) { temp=pos1; pos1=pos2; pos2=temp; }
	    // Make the reversal
	    for(int j=pos1, k=pos2; j<=k; j++, k--) {
		temp = m_original_permutation[j];
		m_original_permutation[j] = -m_original_permutation[k];
		if(j != k) // We don't want to opposite the mid position twice
		    m_original_permutation[k] = -temp;
	    }
	}
	expend();		
    }		
	
    public Permutation(Permutation permutation) throws Exception {
	int[] original_permutation = permutation.getOriginalPermutation();
	m_original_permutation = new int[original_permutation.length];
	for(int i=0; i<original_permutation.length; i++)
	    m_original_permutation[i] = original_permutation[i];
	expend();
    }
	
    public Permutation(int size, String permutation_string) throws Exception {
	try {
	    m_original_permutation = new int[size];
		
	    int index = 0;
	    int start_index = -1;
	    int string_count = 0;
	    // Convert the text to characters array
	    char[] permutation_chars = permutation_string.toCharArray();
	    // Fetch the numbers
	    for(int i=0; index<m_original_permutation.length; i++) {
		if(i == permutation_chars.length) { // The string is over
		    if(start_index != -1) { // The string ended in the middle of a word (probably the last number)
			// Get the last number
			m_original_permutation[index++] = Integer.parseInt(new String(permutation_chars, start_index, string_count));
			start_index = -1; // Specify spaces part
			string_count = 0;
			continue;
		    } else // The string is over but there are not enough numbers
			throw(new Exception("Permutation::Permutation - not enough numbers were supplied"));					
		}
		if(permutation_chars[i] == ' ') {
		    if(start_index == -1) // spaces part - don't do anything
			continue;
		    // End of a new string
		    m_original_permutation[index++] = Integer.parseInt(new String(permutation_chars, start_index, string_count));
		    start_index = -1; // Specify spaces part
		    string_count = 0;
		} else {
		    if(start_index == -1)  // First digit in a number
			start_index = i; // Specify non-space part
		    string_count++; // Stay in a digits part
		}
	    }
				
	    expend();
	} catch(Exception e) { throw new Exception("Failed to create a permutation from the string"); }		
    }
	
    private void expend() throws Exception {
	m_permutation = this.unsign_permutation(this.m_original_permutation);
	this.m_reversed_permutation = this.reverse_permutation(this.m_permutation);
	if(check_permutation(m_permutation) == false)
	    throw(new Exception("Permutation::Permutation - check_permutation failed"));
	if(check_permutation(m_reversed_permutation) == false)
	    throw(new Exception("Permutation::Permutation - check_permutation failed"));
    }
	
    public int[] getOriginalPermutation() { return m_original_permutation; }
    public int[] getPermutation() { return m_permutation; }

    /* Define two unsigned vertices for every signed vertex and add two 
       extra vertices. The indexes change from 1..length to 0..length-1 */
    private int[] unsign_permutation(int[] permutation) throws Exception {
	int length = 2*permutation.length + 2;
	int[] new_permutation = new int[length];

	new_permutation[0] = 0;
	new_permutation[length-1] = length-1;
		
	for(int i=0; i<permutation.length; i++) {
	    if(permutation[i] > 0) { // i=1(second), val=3 -- need 3,4
		new_permutation[2*i + 1] = 2 * permutation[i] - 1;
		new_permutation[2*i + 2] = 2 * permutation[i];
	    } else {
		new_permutation[2*i + 1] = 2 * (-permutation[i]);
		new_permutation[2*i + 2] = 2 * (-permutation[i]) - 1;
	    }
	}
	return new_permutation;
    }

    public int[] reverse_permutation(int[] permutation) throws Exception {
	int[] reversed = new int[permutation.length];
	for(int i=0; i<permutation.length; i++)
	    reversed[permutation[i]] = i;
	return reversed;
    }
	

    // Check the validity of the permutation
    private boolean check_permutation(int[] permutation) throws Exception {
	boolean[] check_array = new boolean[permutation.length];
		
	for(int i=0; i<permutation.length; i++) {
	    check_array[i] = false;
	    if(	(permutation[i] < 0) ||
		(permutation[i] >= permutation.length)	)
		return false;
	}
		
	for(int i=0; i<permutation.length; i++) {
	    if(check_array[permutation[i]]) // Someone already have this number
		return false;
	    check_array[permutation[i]] = true;
	}
	return true;
    }
	
	
    public boolean isSorted() {
	for(int i=0; i<m_permutation.length; i++)
	    if(m_permutation[i] != i)
		return false;
	return true;
    }
	
    //	// Maybe it can not be done and we should use the reversal method
    //	public boolean safeReversal(int v) {
    //		return true;	
    //	}
	
    public void reversal(Reversal reversal) throws Exception { 
	int start = reversal.getStart();
	int end = reversal.getEnd();
	int temp;
	if(	(0 > start) ||
		(start > end) ||
		(end >= m_permutation.length) ||
		((start % 2) == 0) ||
		((end % 2) == 1)) // Invalid reversal
	    throw new Exception("Permutation::reversal - Invalid reversal");

	// Handle the original permutation
	int orig_start_index = start / 2; // 1->0,3->1 , ...
	int orig_end_index = (end-1) / 2; // 2->0,4->1 , ...
	for(int i=orig_start_index, j=orig_end_index; i<=j; i++, j--) {
	    if(i == j)
		m_original_permutation[i] *= (-1);
	    else {
		temp = m_original_permutation[i];
		m_original_permutation[i] = (-1) * m_original_permutation[j];
		m_original_permutation[j] = (-1) * temp;
	    }
	}
	for(int i=start, j=end; i<j; i++, j--) {
	    // Update the unsigned permutation
	    temp = m_permutation[i];
	    m_permutation[i] = m_permutation[j];
	    m_permutation[j] = temp;		

	    // Update the reversed permutation
	    m_reversed_permutation[m_permutation[j]] = j;
	    m_reversed_permutation[m_permutation[i]] = i;			
	}
    }

    // 0 - length-1
    public int get(int i) throws Exception {
	if(	(i < 0) ||
		(i >= m_permutation.length))
	    throw new Exception("Permutation::get - asked for invalid index");
	return this.m_permutation[i];
    }
    public int getSize() {
	return this.m_permutation.length;
    }
	
    int[] getPositions(int i, int j) throws Exception {
	if(	(i >= m_permutation.length) ||
		(j >= m_permutation.length))
	    throw new Exception("Permutation::getRange - invalid arguments, out of range");
	int[] result = new int[2];
	result[0] = getPosition(i);
	result[1] = getPosition(j);
	if(result[0] > result[1]) {
	    int temp = result[0];
	    result[0] = result[1];
	    result[1] = temp;
	}
	return result;
    }
	
    int[] getIndexes(int pos1, int pos2) throws Exception {
	if(	(pos1 >= m_permutation.length) ||
		(pos2 >= m_permutation.length))
	    throw new Exception("Permutation::getRange - invalid arguments, out of range");
	int[] result = new int[2];
	result[0] = getIndex(pos1);
	result[1] = getIndex(pos2);
	if(result[0] > result[1]) {
	    int temp = result[0];
	    result[0] = result[1];
	    result[1] = temp;
	}
	return result;
    }

    public int getPosition(int index) throws Exception { return m_reversed_permutation[index]; }
    public int getIndex(int position) throws Exception { return m_permutation[position]; }
	
    public int getBreakpointsNumber() {
	int result = 0;
	for(int i=0; i<=this.m_permutation.length-2; i++)
	    if(java.lang.Math.abs(m_permutation[i] - m_permutation[i+1]) > 1)
		result++;
	return result;
    }
	
    public int getCyclesNumber() throws Exception {
	int result;
	int size = getSize();
	boolean[] check_array = new boolean[size];
	int checked = 0;
	int next_index = 0;
	int first_index = 0;
	boolean even = true;
	int cycle_size;
	for(result=0; checked<size; checked+=cycle_size) { // in each iteration we locate one cycle
	    // Locate an unchecked element as the first element of the cycle
	    cycle_size = 0;
	    for(first_index=0; check_array[first_index]; first_index++);
	    check_array[first_index] = true;
	    cycle_size++;
	    next_index = next(first_index, even);
	    //			if(first_index == next_index) // cycle with size 1
	    //				continue;
	    for(; first_index!=next_index ; cycle_size++, next_index=next(next_index, even)) {
		if(check_array[next_index])
		    throw new Exception("Something is wrong or cycle of one");
		check_array[next_index] = true;
		even = !even;
	    }
	    if(cycle_size != 2)
		result++;
	}
	return result;
    }
	
    int next(int index, boolean black) throws Exception {
	int position = getPosition(index);
	if(black)
	    return getIndex((4*(position/2))+1-position);
	// 3 --> return 2
	// 4 --> return 5
	else
	    return((4*(index/2))+1-index);
    }
	
    public String origToString() { return makeString(m_original_permutation); }
    public String toString() { return makeString(m_permutation); }
	
    private String makeString(int[] permutation) {
	String result = new String("");
	for(int i=0; i<permutation.length; i++) {
	    result = new String(result + permutation[i]+ " ");
	}
	return result;
    }
							   
}	
