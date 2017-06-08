package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.lang.*;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
        Arrays.fill(physicalPages, null);
        //memoryLock = new Lock();
        pinCond = new Condition(memoryLock);
    }

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swapSpace = new SwapFile();
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
		swapSpace.clear();
		ThreadedKernel.fileSystem.remove(swapSpace.getName());
        super.terminate();
	}

    /**
     * Allocate a page in memory
     * If memory has available space, directly return page
     * If memory is full, evict a page
     * If page is dirty, swap out
     * If page is in swap file, swap in
     * @return TranslationEntry of newly allocated page
     */
    public static TranslationEntry allocatePage(int pid, int vpn) {
        System.out.println("allocating page!!!!!!!!!!!!!!!!!!");
        Lib.debug(dbgVM, "\tallocate a page in physical memory for process: " + pid);
        TableKey key = new TableKey(pid, vpn);
        MemoryPage page;

        int freePageIndex = getFreePage();

        // there still has free page in memory
        if (freePageIndex != -1) {
            int ppn = freePageIndex;
            TranslationEntry entry = new TranslationEntry(vpn, ppn,
                    true, false, false, false);
            page = new MemoryPage(pid, entry, false);
        }

        // memory is full
        else {

            while (pinnedPageNum == physicalPages.length) {
                pinCond.sleep();
            }

            page = clockAlgorithm();

            // if victim page is dirty, then swap it out to swap file
            if (page.entry.dirty) {
                swapSpace.swapOut(page);
            }

            // update the victim page
            page.entry.vpn = vpn;
            page.entry.valid = true;
            page.pid = pid;
        }

        memoryLock.acquire();
        invertedPageTable.put(key, page);
        physicalPages[page.entry.ppn] =  page;
        //Machine.processor().writeTLBEntry(vpn, page.entry);
        memoryLock.release();
        System.out.println("page number is" + page.entry.ppn);

        return page.entry;
    }

    /**
     * Pick a victim page to evict based on clock algorithm
     * when the memory is full
     * @return victim page in memory to evict
     */
    public static MemoryPage clockAlgorithm() {

        MemoryPage page;

        // perform the clock algorithm
        while (true) {
            clock = (clock + 1) % physicalPages.length;
            page = physicalPages[clock];

            // skip the pinned page
            if (page.pinned) continue;

            // prefer invalid page
            if (page.pid == -1 || page.entry.valid == false) {
                break;
            }

            // skip recently used page
            if (page.entry.used) {
                page.entry.used = false;
            }

            // else we found the victim
            else break;
        }

        // remove the mapping from inverted page table
        invertedPageTable.remove(new TableKey(page.entry.vpn, page.pid));

        // make sure this page is unpinned
        page.pinned = false;

        return page;
    }

	/**
	 * Pin the page in memory so that this page cannot be evicted
	 * @param ppn
	 */
	private void pin(int ppn) {

	    // Only pin the page that is already allocated in physical memory
        memoryLock.acquire();
        MemoryPage page = physicalPages[ppn];
        if (!page.pinned) {
            pinnedPageNum++;
        }
        page.pinned = true;
		memoryLock.release();
	}

	/**
	 * Unpin the page in memory
	 * @param ppn
	 */
	private void unpin(int ppn) {
		memoryLock.acquire();
		MemoryPage page = physicalPages[ppn];
		if (page.pinned) {
			pinnedPageNum--;
		}
		page.pinned = false;
        pinCond.wake();  // wake up the process who wants to evict a page
		memoryLock.release();
	}

    /**
     * Check if given page is already existed in memory
     * @param pid
     * @param vpn
     * @return
     */
	protected static boolean pageInMemory(int pid, int vpn) {
	    TableKey key = new TableKey(pid, vpn);
	    if (invertedPageTable.containsKey(key)) return true;
	    else return false;
    }
    protected static TranslationEntry getEntry(int pid, int vpn) {
        TableKey key = new TableKey(pid, vpn);
        return invertedPageTable.get(key).entry;
    }
    protected static void removeMapping(int pid, int vpn) {
	    TableKey key = new TableKey(pid, vpn);
	    invertedPageTable.remove(key);
    }

    /**
     * Check if given page is already existed in swap file
     * @param pid
     * @param vpn
     * @return
     */
    protected static boolean pageInSwapFile(int pid, int vpn) {
        TableKey key = new TableKey(pid, vpn);
        if (swapSpace.swapPageTable.containsKey(key)) return true;
        else return false;
    }

    protected static void removeSwapPage(int pid, int vpn) {
        TableKey key = new TableKey(pid, vpn);
        swapSpace.swapPageTable.remove(key);
    }

    /**
     * Get a free page
     * @return ppn, or -1 if we don't have free page
     */
    protected static int getFreePage() {
        for (int i = 0; i < physicalPages.length; i++) {
            if (physicalPages[i] == null) return i;
        }
        return -1;
    }

    /**
     * Free a physical page in memory
     * @param ppn
     */
    protected static void freeThePage(int ppn) {
        physicalPages[ppn] = null;
    }

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	protected static SwapFile swapSpace;

	//protected static Lock memoryLock;

	private static int pinnedPageNum = 0;

	private static Condition pinCond;

	private static int clock = 0;

	// a global page table matching <pid, vpn> to physical page in memory
	protected static Hashtable<TableKey, MemoryPage> invertedPageTable = new Hashtable<>();

	// an array of physical pages in memory indexed by ppn
    private static MemoryPage[] physicalPages = new MemoryPage[Machine.processor().getNumPhysPages()];


	public static class MemoryPage {
	    TranslationEntry entry;
	    int pid;
	    boolean pinned = false;
        MemoryPage(int pid, TranslationEntry entry, boolean pinned) {
	        this.pid = pid;
	        this.entry = entry;
	        this.pinned = false;
        }
    }

    /**
     * This class has format as <pid, vpn> to map the PPN in global page table
     */
    public static class TableKey {
	    int pid;
	    int vpn;

	    TableKey(int pid, int vpn) {
	        this.pid = pid;
	        this.vpn = vpn;
        }

		/**
         * Use address as hash value can make sure there is no collision
		 * @return
		 */
		@Override
		public int hashCode() {
		    int hash = 23;
		    hash = hash * 31 + pid;
		    hash = hash * 31 + vpn;
		    return hash;
		}

		@Override
		public boolean equals(Object x) {
			if (!(x instanceof TableKey)) return false;
			else if (x == this) return true;
			else {
				TableKey cast = (TableKey)x;
				return cast.pid == this.pid && cast.vpn == this.vpn;
			}
		}
    }

    /**
     * This class represents the swap space in disk
     * and can perform swap in and swap out operation
     */
    protected static class SwapFile {


        // this hashtable is used to check if certain page has ever been swapped out to disk
        static Hashtable<TableKey, SwapPage> swapPageTable;

        // the file system for swap space
        static OpenFile swapFile;

        // track the available position at tail
        static int tail;

        // track the empty position in middle
        static HashSet<Integer> availablePosition;
        SwapFile() {
            swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
            swapPageTable = new Hashtable<>();
            tail = 0;
            availablePosition = new HashSet<>();
        }

        /**
         * Swap File ------> Physical Memory
         * 1. get the position
         * 2. read page
         * 3. free the position
         * @param pid
         * @param vpn
         * @param ppn
         */
        static void swapIn(int pid, int vpn, int ppn) {

            // get the corresponding page from swap file
            SwapPage page = swapPageTable.get(new TableKey(pid, vpn));

            // If the page exists in disk, then swap into memory, or just leave
            if (page == null) return;

            // read the bytes from swap space to memory
            int swapResult = swapFile.read(page.swapPositionIndex * Processor.pageSize, Machine.processor().getMemory(),
                    ppn * Processor.pageSize, Processor.pageSize);

            // check if we have read a whole page
            Lib.assertTrue(swapResult == Processor.pageSize);

            // free the position
            availablePosition.add(page.swapPositionIndex);
        }

        /**
         * Physical memory ------> swap file
         * 1. find available position
         * 2. write page
         * 3. invalid the page in memory
         * @param page
         */
        static void swapOut(MemoryPage page) {
            if (!page.entry.valid) return;

            // find available position
            int position = tail;
            if (availablePosition.size() != 0) {
                position = getPosition();
            }
            else tail++;

            SwapPage pageInSwapFile = new SwapPage(position, page.entry);

            int writeResult = swapFile.write(pageInSwapFile.swapPositionIndex * Processor.pageSize,
                    Machine.processor().getMemory(), page.entry.ppn * Processor.pageSize,
                    Processor.pageSize);
            Lib.assertTrue(writeResult == Processor.pageSize);

            swapPageTable.put(new TableKey(page.pid, page.entry.vpn), pageInSwapFile);
        }

        /**
         * Iterate the entry in available position, and get one
         * This method is called only when there has at least available position
         * @return
         */
        static int getPosition() {
            Iterator iterator = availablePosition.iterator();
            int position = (int)iterator.next();
            availablePosition.remove(position);
            return position;
        }
        static String getName() {
            return swapFile.getName();
        }

        /**
         * Clear the swap file when kernel terminates
         */
        static void clear() {
            swapFile.close();
        }
    }

    private static class SwapPage {

	    int swapPositionIndex;
	    TranslationEntry translationEntry;

	    SwapPage(int swapPositionIndex, TranslationEntry entry) {
	        this.swapPositionIndex = swapPositionIndex;
	        translationEntry = entry;
        }
    }
}
