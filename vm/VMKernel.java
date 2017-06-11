package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.lang.*;
import java.util.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
    }

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swapSpace = new SwapFile();
		pinLock = new Lock();
        pinCond = new Condition(pinLock);
        physicalPages = new MemoryPage[Machine.processor().getNumPhysPages()];
        for (int i = 0; i < physicalPages.length; i++) {
            physicalPages[i] = new MemoryPage();
        }
        swapMap = new HashMap<>();

	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swapSpace.swapFile.close();
		ThreadedKernel.fileSystem.remove(swapSpace.swapFile.getName());
        super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	// a swap space in disk
	protected static SwapFile swapSpace;

    // number of pages in memory that has been pinned
	protected static int pinnedPageNum = 0;

	protected static Lock pinLock;

	protected static Condition pinCond;

	// an array of physical pages in memory indexed by ppn
    protected static MemoryPage[] physicalPages = new MemoryPage[Machine.processor().getNumPhysPages()];

    // vpn ---> index of page in swap space
    protected static HashMap<Integer, Integer> swapMap;

	public static class MemoryPage {
	    UserProcess process;
	    int vpn;
	    boolean pinned = false;
        MemoryPage() {}
    }

    /**
     * This class represents the swap space in disk
     * and can perform swap in and swap out operation
     */
    protected static class SwapFile {

        // the file for swap space
        static OpenFile swapFile;

        // a page list to track the free page
        static LinkedList<Integer> freeList;

        // a lock making sure there won't have two process modifying swap space at the same time
        static Lock swapLock;

        // a condition variable to track if swap space is full
        static Condition swapFull;

        // index of swap space ---> UserProcess
        static HashMap<Integer, UserProcess> processMap;

        SwapFile() {
            swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
            swapLock = new Lock();
            swapFull = new Condition(swapLock);
            processMap = new HashMap<>();
            freeList = new LinkedList<>();
            for (int i = 0; i < 100; i++) {
                freeList.add(i);
            }
        }

        static int swapIn(int spn, int ppn) {
            if (freeList.get(spn) != -1) {
                return -1;
            }
            int swapResult = swapFile.read(spn * Processor.pageSize,
                    Machine.processor().getMemory(),
                    ppn * Processor.pageSize, Processor.pageSize);
            if (swapResult != -1) {
                freeList.add(spn);
                swapLock.acquire();
                swapFull.wake();
                swapLock.release();
            }
            return swapResult;
        }

        /**
         * Swap out a physical page from memory to swap space
         * @param ppn
         * @return length of content written from memory to swap file
         */
        static int swapOut(int ppn) {
            swapLock.acquire();
            while(freeList.size() == 0) {
                swapFull.sleep();
            }
            swapLock.release();

            int spn = freeList.remove();
            int swapResult = swapFile.write(spn * Processor.pageSize,
                    Machine.processor().getMemory(), ppn * Processor.pageSize,
                    Processor.pageSize);

            if (swapResult >= 0) {
                processMap.put(spn, physicalPages[ppn].process);
                swapMap.put(physicalPages[ppn].vpn, spn);
                return spn;
            }
            return swapResult;
        }
    }
}
