package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

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
		buffer = new LinkedList<Integer>();
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

		speakerCount++;
		buffer.add(word);
		speakersCome.wake();
		while (listenerCount < 1) {
			Lib.debug(mySignal, "No listener. Speaker goes to sleep.");
			listenersCome.sleep();
		}
		//Lib.debug(mySignal, "Speaker is returning.");
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
			Lib.debug(mySignal, "No speaker. Listener goes to sleep.");
			speakersCome.sleep();
		}
		//Lib.debug(mySignal, "Listener is returning");
		speakerCount--;

		int message = buffer.remove();
		mutex.release();

		return message;
	}

	/** Another implementation, only need one word buffer:
	 * -----speaker-----
	 * lock
	 * while (l == 0 || s != 0)
	 * 	sleep()
	 * s++
	 * l--
	 * msg->buffer
	 * signal listener
	 * unlock
	 *
	 * -----listener-----
	 * lock
	 * l++
	 * signal speaker
	 * while (s == 0)
	 * 	sleep
	 * s--
	 * buffer->msg
	 * unlock
	 * return msg
	 */



	private static class Speaker implements Runnable {
		Speaker(Communicator c) {
			this.c = c;
		}

		public void run() {
			for (int i = 0; i < 5; ++i) {
				System.out.println("speaker speaking: " + i);
				c.speak(i);
				System.out.println("Speaker spoke, word = " + i);
				//KThread.yield();
			}
		}

		private Communicator c;
	}

	/**
	 * Test if this module is working.
	 */
	public static void selfTest() {
		System.out.println("Testing Communicator");

		Communicator c = new Communicator();
		new KThread(new Speaker(c)).setName("Speaker").fork();

		for (int i = 0; i < 5; ++i) {
			System.out.println("listener listening: " + i);
			int x = c.listen();
			System.out.println("listener listened, word = " + x);
			//KThread.yield();
		}

		System.out.println("End of testing Communicator");
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
	private LinkedList<Integer> buffer;

	/**
	 * A state variable, showing the number of current speakers.
	 */
	private int speakerCount;

	/**
	 * A state variable, showing the number of current listeners.
	 */
	private int listenerCount;

	/**
	 * Debug flag
	 */
	private static final char mySignal = 'b';

}
