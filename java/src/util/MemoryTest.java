package util;

import java.io.PrintStream;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Class <code>MemoryTest</code> can be used to print out the memory usage every so often.
 *
 * @author "Austin Shoemaker" <austin@genome.arizona.edu>
 * @see TimerTask
 */
public class MemoryTest extends TimerTask {
    
    private static Timer timer = null;

    /**
     * Method <code>run</code> creates a new timer and schedules it to run every <code>timeBetweenTests</code>
     * milliseconds. If this method has already been called without the stop method being called, nothing is done.
     *
     * @param timeBetweenTests an <code>int</code> value of time in milliseconds between tests
     * @param out a <code>PrintStream</code> value of ouput PrintStream to print the usage to
     */
    public synchronized static void run(int timeBetweenTests, PrintStream out) {
	if (timer == null) {
	    timer = new Timer(true);
	    timer.schedule(new MemoryTest(out), new Date(), timeBetweenTests);
	}
    }

    /**
     * Method <code>stop</code> stops the timer if it was running.
     *
     */
    public synchronized static void stop() {
	if (timer != null) {
	    timer.cancel();
	    timer = null;
	}
    }

    private PrintStream out;

    private MemoryTest(PrintStream out) {
	super();
	this.out = out;
    }
    
    /**
     * Method <code>run</code> acquires the free and total memory and prints it out to
     * the given PrintStream in the form: "<free>\t<total>\t<free/total>"
     */
    public void run() {
	long free = Runtime.getRuntime().freeMemory();
	long total = Runtime.getRuntime().totalMemory();
	//double usage = free / (double) total;
	out.println(free + "\t" + total + "\t" + (free / (double) total));
    }
}
