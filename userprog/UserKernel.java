package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();
	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());

		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});

		freePages = new LinkedList<Integer>();
		int numPhysPages = Machine.processor().getNumPhysPages();
		for (int i = 0; i < numPhysPages; i++) {
			freePages.add(i);
		}

		memoryLock = new Lock();

		nextPID = 0;
		numProcess = 0;

		numProsLock = new Lock();
	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();

		System.out.println("Testing the console device. Typed characters");
		System.out.println("will be echoed until q is typed.");

		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');

		System.out.println("");



	}

	/**
	 * Returns the current process.
	 * 
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 * 
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of
	 * the exception (see the <tt>exceptionZZZ</tt> constants in the
	 * <tt>Processor</tt> class). If the exception involves a bad virtual
	 * address (e.g. page fault, TLB miss, read-only, bus error, or address
	 * error), the processor's BadVAddr register identifies the virtual address
	 * that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 * 
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		Lib.assertTrue(process.execute(shellProgram, new String[] {}));

		//System.out.println("Shell process created~~~~~~~~~~~~~");
		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/**
	 * Get the number of available physical pages.
	 */
	protected static int freePagesNum() {
		//moemoryLock should be held here.
		return freePages.size();
	}

	/**
	 * Remove one page from the free pages list.
	 */
	protected static int getPage() {
		//moemoryLock should be held here.
		return freePages.remove();
	}

	/**
	 * Add one page back to the free pages list.
	 */
	protected static void addPage(int pageNumber) {
		//moemoryLock should be held here.
		freePages.add(pageNumber);
	}

	/**
	 * Get the next PID for a new process.
	 */
	protected static int getNextPID() {
		int newPID = 0;
		boolean inStatus=Machine.interrupt().disable();

		newPID = nextPID++;

		Machine.interrupt().restore(inStatus);

		return newPID;
	}

	/**
	 * Increase the number of live processes.
	 */
	protected static void increPros() {
		boolean inStatus = Machine.interrupt().disable();

		numProcess++;
		System.out.println("Number of process has increased to " + numProcess);

		Machine.interrupt().restore(inStatus);
	}

	/**
	 * Decrease the number of live processes.
	 */
	protected static void decrePros() {
		boolean inStatus=Machine.interrupt().disable();

		numProcess--;
		System.out.println("Number of process has decreased to " + numProcess);

		Machine.interrupt().restore(inStatus);
	}

	/**
	 * Get the number of live processes.
	 */
	protected static int getNumPros() {
		//numProsLock should be held here.
		return numProcess;
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;

	/** A global linked list of free physical pages. */
	private static LinkedList<Integer> freePages;

	/** A lock used to access the page list synchronically. */
	protected static Lock memoryLock;

	/** A counter of process IDs. */
	private static int nextPID;

	/** Number of live processes. */
	private static int numProcess;

	/** A lock used to access the number of live processes. */
	protected static Lock numProsLock;
}
