package nachos.threads;

import nachos.machine.*;

import java.util.PriorityQueue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		waitQueue = new PriorityQueue<timeWaiter>();

		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		long currentTime = Machine.timer().getTime();

		timeWaiter nextWaiter = waitQueue.peek();
		while (nextWaiter != null && nextWaiter.timeToWake <= currentTime) {
			waitQueue.poll();
			nextWaiter.waiter.ready();

			Lib.debug(mySignal,
					"It's " + nextWaiter.waiter.toString()+ "'s time to wake up ");

			nextWaiter = waitQueue.peek();
		}

		KThread.currentThread().yield();

	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		/**
		 * Original implementation--busy waiting:
		 *
		 * // for now, cheat just to get something working (busy waiting is bad)
		 * long wakeTime = Machine.timer().getTime() + x;
		 * while (wakeTime > Machine.timer().getTime())
		 * 		KThread.yield();
		 */

		boolean intStatus = Machine.interrupt().disable();

		long wakeTime = Machine.timer().getTime() + x;
		KThread thread = KThread.currentThread();
		waitQueue.add(new timeWaiter(thread, wakeTime));

		Lib.debug(mySignal,
				KThread.currentThread().toString()+ " sleeps util " + wakeTime);

		KThread.sleep();

		Machine.interrupt().restore(intStatus);


	}

	/**
	 * A queue to store threads waiting on the waitUntil() method.
	 * Objects putting into the queue are of timeWaiter type.
	 * Objects in the queue are sorted by their wakeup time.
	 */
	private PriorityQueue<timeWaiter> waitQueue;

	private static final char mySignal = 'm';

	/**
	 * A data type to store information of threads waiting on the waitUntil() method.
	 * It implements Comparable so objects of it can be sorted by the timeToWake field.
	 */
	private class timeWaiter implements Comparable<timeWaiter>{
		/**
		 * Create a new time waiter.
		 * Specify the thread who's waiting and the time it should be waken up.
		 */
		timeWaiter(KThread thread, long time) {
			waiter = thread;
			timeToWake = time;
		}

		/**
	     * The timeWaiter object with larger number of timeToWake is considered larger.
		 */
		public int compareTo(timeWaiter o) {
			if (this.timeToWake > o.timeToWake)
				return 1;

			if (this.timeToWake < o.timeToWake)
				return -1;

			return 0;
		}

		/**
		 * The thread waiting on waitUntil() method.
		 */
		KThread waiter;

		/**
		 * The time after which it should be waken.
		 */
		long timeToWake;

	}
}
