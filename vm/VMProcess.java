package nachos.vm;

import nachos.machine.*;
import nachos.userprog.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

    @Override
	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {

        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry TLBEntry = new TranslationEntry(Machine.processor().readTLBEntry(i));
            TranslationEntry PTEntry = pageTable[TLBEntry.vpn];

            if (TLBEntry.valid) {
                PTEntry.used = TLBEntry.used;
                PTEntry.dirty = TLBEntry.dirty;
            }
            TLBEntry.valid = false;
            Machine.processor().writeTLBEntry(i, TLBEntry);
        }
    }
    @Override
	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {}

    @Override
	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
        // initialize the page table and set them as invalid

        pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < pageTable.length; i++) {
			pageTable[i] = new TranslationEntry(i, i,
					false, false, false, false);
		}

		return true;
	}

	@Override
	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
        super.unloadSections();
	}

	@Override
    public int readVirtualMemory(int vaddr, byte[] data) {
	    return readVirtualMemory(vaddr, data, 0, data.length);
    }


	@Override
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {

        //Check if arguments are valid.
        Lib.assertTrue(offset >= 0 && length >= 0
                && offset + length <= data.length);

        //Check if this reading exceeds the process's address space.
        //If it exceeds, the reading is illegal, return 0.
        int firstVPN = Processor.pageFromAddress(vaddr);
        int lastVPN = Processor.pageFromAddress(vaddr + length);
        if (firstVPN < 0 || lastVPN > numPages) {
            return 0;
        }
        //Get the reference of physical memory array.
        byte[] memory = Machine.processor().getMemory();

        //Read data from physical memory to the data array.
        //Virtual address in the transfer is continuous, physical is not.
        //Virtual memory -> page table -> physical memory -> memory array
        int readBytes = 0;
        while (readBytes < length) {
            int vaddrStart = vaddr + readBytes;
            //Compute vpm
            int VPN = Processor.pageFromAddress(vaddrStart);
            int ppn = pin(VPN, false);
            //Compute offset
            int pagePosition = Processor.offsetFromAddress(vaddrStart);
            int bytesToRead = Math.min(pageSize - pagePosition, length - readBytes);
            //Compute physical address
            int phyaddrStart = pageTable[VPN].ppn * pageSize + pagePosition;
            System.arraycopy(memory, phyaddrStart, data,
                    offset + readBytes, bytesToRead);
            readBytes += bytesToRead;
            unpin(VPN);
        }

        return readBytes;
    }


    @Override
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    @Override
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        //Check if arguments are valid.
        Lib.assertTrue(offset >= 0 && length >= 0
                && offset + length <= data.length);

        //Check if this writing exceeds the process's address space.
        //If it exceeds, the writing is illegal, return 0.
        int firstVPN = Processor.pageFromAddress(vaddr);
        int lastVPN = Processor.pageFromAddress(vaddr + length);
        if (firstVPN < 0 || lastVPN > numPages) {
            return 0;
        }

        //Get the reference of physical memory array.
        byte[] memory = Machine.processor().getMemory();

        //Write data from the data array to physical memory.
        //Virtual address in the transfer is continuous, physical is not.
        //Virtual memory -> page table -> physical memory -> memory array
        int writeBytes = 0;
        while (writeBytes < length) {
            int vaddrStart = vaddr + writeBytes;
            int VPN = Processor.pageFromAddress(vaddrStart);
            int ppn = pin(VPN, true);
            int pagePosition = Processor.offsetFromAddress(vaddrStart);
            int bytesToWrite = Math.min(pageSize - pagePosition, length - writeBytes);
            int phyaddrStart = pageTable[VPN].ppn * pageSize + pagePosition;
            System.arraycopy(data, offset + writeBytes, memory,
                    phyaddrStart, bytesToWrite);
            writeBytes += bytesToWrite;
            unpin(VPN);
        }

        return writeBytes;
    }


    private int pin(int vpn, boolean write) {

        // check if vpn is valid
        if (vpn < 0 || vpn > pageTable.length) {
            return -1;
        }

        // check if mapping is valid
        TranslationEntry entry = pageTable[vpn];
        if (entry.valid == false || entry.vpn != vpn) {
            return -1;
        }

        // if we pin the page because it's written
        if (write) {

            // we can not write a page that is read only
            if (entry.readOnly) {
                return -1;
            }
            entry.dirty = true;
        }
        entry.used = true;

        VMKernel.pinLock.acquire();
        while (VMKernel.pinnedPageNum >= numPages) {
            VMKernel.pinCond.sleep();
        }
        VMKernel.pinnedPageNum++;
        VMKernel.invertedPageTable[entry.ppn].pinned = true;
        VMKernel.pinLock.release();
        return entry.ppn;
    }

    protected void unpin(int vpn) {
        if (vpn < 0 || vpn > pageTable.length) {
            return;
        }
        TranslationEntry entry = pageTable[vpn];
        if (entry.valid == false || entry.vpn != vpn) {
            return;
        }

        VMKernel.pinLock.acquire();
        VMKernel.pinnedPageNum--;
        VMKernel.invertedPageTable[entry.ppn].pinned = false;
        VMKernel.pinCond.wake();
        VMKernel.pinLock.release();
    }

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case Processor.exceptionTLBMiss:
                handleTLBMiss(processor.readRegister(Processor.regBadVAddr));
                break;
			default:
				super.handleException(cause);
				break;
		}
	}

    /** Handle TLB miss exception
     * @param vaddr
     * @return
     */
	private void handleTLBMiss(int vaddr) {

		Lib.debug(dbgVM, "\thandleTLBMissException: begin to handle exception");
        int vpn = Processor.pageFromAddress(vaddr);
        Lib.assertTrue(vpn >= 0 && vpn <= numPages);

        // entry must be in page table, let's check if it's valid
        TranslationEntry entry = pageTable[vpn];

        if (!entry.valid) {
            handlePageFault(vpn);
        }

        // pick a victim entry in TLB, and check if TLB is full at the same time
        boolean full = true;
        int index = Lib.random(Machine.processor().getTLBSize());
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            if (Machine.processor().readTLBEntry(i).valid == false) {
                index = i;
                full = false;
                break;
            }
        }

        // if TLB is full, we need to sync TLB with page table
        if (full) {
            sync();
        }

        // over write the victim entry in TLB
        TranslationEntry newEntry = new TranslationEntry(entry);
        Machine.processor().writeTLBEntry(index, newEntry);
	}

    /**
     * Synchronize the bits in page table from TLB
     */
	private void sync() {
        for (int i = 0; i > Machine.processor().getTLBSize(); i++) {
            TranslationEntry TLBEntry = new TranslationEntry(Machine.processor().readTLBEntry(i));
            TranslationEntry PTEntry = pageTable[TLBEntry.vpn];
            if (TLBEntry.valid) {
                PTEntry.used = TLBEntry.used;
                PTEntry.dirty = TLBEntry.dirty;
            }
        }
    }

    /**
     * Handle page fault when there is no valid mapping in page table
     * @param vpn
     */
	private void handlePageFault(int vpn) {
	    int ppn;

	    // there has available page in memory, we can directly get one
	    if (VMKernel.freePagesNum() != 0) {
	        ppn = VMKernel.getPage();
	        allocatePage(vpn, ppn, false);
        }

        // there is no available page in memory, we need to pick one to kick out
        else {
            sync();
            do {
                ppn = clockAlgorithm();
            }
            while (VMKernel.invertedPageTable[ppn].pinned);
            allocatePage(vpn, ppn, true);
        }
    }

    /**
     * Use clock algorithm to pick a page to kick out
     * @return
     */
    protected int clockAlgorithm() {
	    while (pageTable[VMKernel.invertedPageTable[clock].vpn].used) {
	        pageTable[VMKernel.invertedPageTable[clock].vpn].used = false;
	        clock = (clock + 1) % VMKernel.invertedPageTable.length;
        }
        int ppn = clock;
	    clock = (clock + 1) % VMKernel.invertedPageTable.length;
	    return ppn;
    }

    protected void allocatePage(int vpn, int ppn, boolean evict) {
	    boolean allocated = false;
	    boolean readOnly = false;
	    TranslationEntry PTEntry = pageTable[vpn];

	    // physical pages are full, we need to evict one. Swap out is possible.
        if (evict) {
            handleEvict(vpn, ppn);
        }


        if (PTEntry.dirty && VMKernel.swapMap.containsKey(vpn)) {
            int spn = VMKernel.swapMap.get(vpn);

            if (VMKernel.swapSpace.processMap.containsKey(spn) &&
                    VMKernel.swapSpace.processMap.get(spn) == this) {
                if (VMKernel.swapSpace.swapIn(spn, ppn) > 0) {
                    VMKernel.swapMap.remove(vpn);
                    VMKernel.swapSpace.processMap.remove(spn);
                    allocated = true;
                }
            }
        }

        // page is not in swap file, then allocate from coff
        if (!allocated) {
            readOnly = lazyLoading(vpn, ppn);
        }
        PTEntry.ppn = ppn;
        PTEntry.valid = true;
        PTEntry.readOnly = readOnly;
        VMKernel.invertedPageTable[ppn].vpn = vpn;
        VMKernel.invertedPageTable[ppn].process = this;
    }
    protected void handleEvict(int vpn, int ppn) {
        int oldVPN = VMKernel.invertedPageTable[ppn].vpn;
        TranslationEntry oldEntry = pageTable[oldVPN];

        int inCoff = inCoff(vpn);

        if (inCoff >= 0) {
            boolean readOnly = (inCoff == 1);

            if (!readOnly && oldEntry.dirty) {
                VMKernel.swapSpace.swapOut(ppn);
            }
        }
        else {
            if (oldEntry.dirty) {
                VMKernel.swapSpace.swapOut(ppn);
            }
        }
        oldEntry.valid = false;
        VMKernel.invertedPageTable[ppn].vpn = vpn;
        VMKernel.invertedPageTable[ppn].process = this;

        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry TLBEntry = new TranslationEntry(Machine.processor().readTLBEntry(i));
            if (TLBEntry.ppn == ppn) {
                TLBEntry.valid = false;
                pageTable[TLBEntry.vpn].valid = false;
            }
            Machine.processor().writeTLBEntry(i, TLBEntry);
        }

    }

    /**
     * Check if vpn is valid, that is, we can find corresponding section in .coff file
     * And also check if section is read only
     * @param vpn
     * @return
     */
    protected int inCoff(int vpn) {
        boolean readOnly;
        for (int i = 0; i < coff.getNumSections(); i++) {
            CoffSection section = coff.getSection(i);
            if (vpn >= section.getFirstVPN() &&
                    vpn < (section.getFirstVPN() + section.getLength())) {
                readOnly = section.isReadOnly();
                return readOnly? 1: 0;
            }
        }
        return -1;
    }
    protected boolean lazyLoading(int vpn, int ppn) {
	    boolean readOnly = false;
	    for (int i = 0; i < coff.getNumSections(); i++) {
	        CoffSection section = coff.getSection(i);

	        if (vpn >= section.getFirstVPN() &&
                    vpn < (section.getFirstVPN() + section.getLength())) {
	            readOnly = section.isReadOnly();
	            section.loadPage(vpn - section.getFirstVPN(), ppn);
            }
        }
        return readOnly;
    }

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	private static TranslationEntry[] pageTable;

	private static int clock = 0;

}
