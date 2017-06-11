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
        pinCond = new Condition(memoryLock);

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

    /**
    public static TranslationEntry allocatePage(int pid, int vpn) {
        //System.out.println("allocating page!!!!!!!!!!!!!!!!!!");
        //System.out.println("allocate page for: " + vpn + " for process: " + pid);
        Lib.debug(dbgVM, "\tallocate a page in physical memory for process: " + pid);
        TableKey key = new TableKey(pid, vpn);
        MemoryPage page;

        int freePageIndex = getFreePage();

        // there still has free page in memory
        if (freePageIndex != -1) {
            System.out.println("Good new!!!!! We have page to use!!!! " + freePageIndex);
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
            page.entry.used = true;

            page.entry.readOnly = false;

            System.out.println("Bad news....... We have allocated " + page.entry.ppn);
        }

        memoryLock.acquire();
        invertedPageTable.put(key, page);
        physicalPages[page.entry.ppn] =  page;
        //Machine.processor().writeTLBEntry(vpn, page.entry);
        memoryLock.release();
        //System.out.println("page number is" + page.entry.ppn);

        return page.entry;
    }
     */

    /**
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
        invertedPageTable.remove(new TableKey(page.pid, page.entry.vpn));
        System.out.println(page.entry.vpn + " has been removed|||||||||||||||||||");

        // make sure this page is unpinned
        page.pinned = false;

        return page;
    }
	protected static void pin(int ppn) {

	    // Only pin the page that is already allocated in physical memory
        memoryLock.acquire();
        MemoryPage page = physicalPages[ppn];
        if (!page.pinned) {
            pinnedPageNum++;
        }
        page.pinned = true;
		memoryLock.release();
	}
	protected static void unpin(int ppn) {
		memoryLock.acquire();
		MemoryPage page = physicalPages[ppn];
		if (page.pinned) {
			pinnedPageNum--;
		}
		page.pinned = false;
        pinCond.wake();  // wake up the process who wants to evict a page
		memoryLock.release();
	}
     */

    /**
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


    protected static boolean pageInSwapFile(int pid, int vpn) {
        TableKey key = new TableKey(pid, vpn);
        if (swapSpace.swapPageTable.containsKey(key)) return true;
        else return false;
    }

    protected static void removeSwapPage(int pid, int vpn) {
        TableKey key = new TableKey(pid, vpn);
        swapSpace.swapPageTable.remove(key);
    }
    protected static int getFreePage() {
        for (int i = 0; i < physicalPages.length; i++) {
            if (physicalPages[i] == null) return i;
        }
        return -1;
    }
    protected static void freeThePage(int ppn) {
        physicalPages[ppn] = null;
    }
    protected static int freePagesNum() {
        return super.freePagesNum();
    }
    protected static int getPage() {
        return super.getPage();
    }
    */
	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	protected static SwapFile swapSpace;

	//protected static Lock memoryLock;

	protected static int pinnedPageNum = 0;

	protected static Lock pinLock;
	protected static Condition pinCond;

	// a global page table matching <pid, vpn> to physical page in memory
	//protected static Hashtable<TableKey, MemoryPage> invertedPageTable = new Hashtable<>();

	// an array of physical pages in memory indexed by ppn
    protected static MemoryPage[] physicalPages = new MemoryPage[Machine.processor().getNumPhysPages()];

    protected static HashMap<Integer, Integer> swapMap;

    //protected static LinkedList<Integer> freeList;

	public static class MemoryPage {
	    UserProcess process;
	    int vpn;
	    boolean pinned = false;
        MemoryPage() {}
    }

    /**
    public static class TableKey {
	    int pid;
	    int vpn;

	    TableKey(int pid, int vpn) {
	        this.pid = pid;
	        this.vpn = vpn;
        }


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
     */


    /**
     * This class represents the swap space in disk
     * and can perform swap in and swap out operation
     */
    protected static class SwapFile {



        // the file system for swap space
        static OpenFile swapFile;

        // track the available position at tail
        //static int tail;

        // track the empty position in middle
        //static HashSet<Integer> availablePosition;

        static LinkedList<Integer> freeList;

        static Lock swapLock;

        static Condition swapFull;

        static HashMap<Integer, UserProcess> processMap;

        SwapFile() {
            swapFile = ThreadedKernel.fileSystem.open("swapFile", true);
            swapLock = new Lock();
            swapFull = new Condition(swapLock);
            processMap = new HashMap<>();
            for (int i = 0; i < 100; i++) {
                freeList.add(i);
            }
        }

        /**
        static void swapIn(int pid, int vpn, int ppn) {
            System.out.println("I swap " + vpn + " in!!!!!!");
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
         */
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


        /**
        static void swapOut(MemoryPage page) {
            System.out.println("I swap " + page.entry.vpn + " out");
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

        static int getPosition() {
            Iterator iterator = availablePosition.iterator();
            int position = (int)iterator.next();
            availablePosition.remove(position);
            return position;
        }
         */
    }
    /**
    private static class SwapPage {

	    int swapPositionIndex;
	    TranslationEntry translationEntry;

	    SwapPage(int swapPositionIndex, TranslationEntry entry) {
	        this.swapPositionIndex = swapPositionIndex;
	        translationEntry = entry;
        }
    }
     */
}
