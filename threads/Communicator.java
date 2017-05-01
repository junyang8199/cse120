package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		mutex = new Lock();
		listenersCome = new Condition(mutex);
		speakersCome = new Condition(mutex);
		buffer = 0;
		speakerCount = 0;
		listenerCount = 0;
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		mutex.acquire();

		while (listenerCount < 1) {
			listenersCome.sleep();
		}
		/**
		 * Multiple speakers may come and wait, so only when a speaker has woken
		 * by a listening can it put bits into the buffer.
		 */
		speakerCount++;
		buffer = word;
		speakersCome.wake();
		listenerCount--;

		mutex.release();
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		mutex.acquire();

		listenerCount++;
		listenersCome.wake();
		while (speakerCount < 1) {
			listenersCome.sleep();
		}
		speakerCount--;

		mutex.release();

		return buffer;
	}

	private Lock mutex;

	/**
	 * Speakers wait on this condition and Listens signal this condition.
	 */
	private Condition listenersCome;

	/**
	 * Listeners wait on this condition and Speakers signal this condition.
	 */
	private Condition speakersCome;

	/**
	 * A buffer to pass content from speakers to listeners.
	 */
	private int buffer;

	/**
	 * A state variable, showing the number of current speakers.
	 */
	private int speakerCount;

	/**
	 * A state variable, showing the number of current listeners.
	 */
	private int listenerCount;
}
