package nachos.vm;

import nachos.machine.*;
import nachos.userprog.*;
import org.omg.CORBA.TRANSACTION_MODE;
import sun.misc.VM;


import java.util.Arrays;

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
     * What are needed to sync?
     * 1. TLB
     * 2. Process page table
	 */
	public void restoreState() {
	    /**
		Lib.debug(dbgVM, "\trestore state for process" + pid);
		//System.out.println("Context Switch HAPPENS!!!!!!!!!!!!!!!!!!!!!");
		// 1. synchronize TLB: set the entry as valid if this entry exist in inverted page table
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry TLBEntry= Machine.processor().readTLBEntry(i);
            if (VMKernel.pageInMemory(pid, TLBEntry.vpn)) {
                TranslationEntry entry = VMKernel.getEntry(pid, TLBEntry.vpn);
                //System.out.println("set TLBEntry " + TLBEntry.vpn + " as true");
                TLBEntry.valid = true;
                sync(TLBEntry, entry);
                Machine.processor().writeTLBEntry(i, entry);
            }
        }


        // 2. synchronize process' personal page table
        for (int i = 0; i < pageTable.length; i++) {
            TranslationEntry PTEntry = pageTable[i];
            if (VMKernel.pageInMemory(pid, i)) {
                TranslationEntry entry = VMKernel.getEntry(pid, i);
                //System.out.println("set PTEntry " + PTEntry.vpn + " as true");
                PTEntry.valid = true;
                sync(PTEntry, entry);
            }
        }
        */

	}

    /**
    private void sync(TranslationEntry entry1, TranslationEntry entry2) {
	    entry1.ppn = entry2.ppn;
	    entry1.used = entry2.used;
	    entry1.readOnly = entry2.readOnly;
	    entry1.dirty = entry2.dirty;
    }
     */

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
        /**
        for (TranslationEntry entry: pageTable) {

            // 1. free this page in memory
            if (VMKernel.pageInMemory(pid, entry.vpn)) VMKernel.freeThePage(entry.ppn);

            // 2. clear mapping in inverted page table, if exists
            VMKernel.removeMapping(pid, entry.vpn);

            // 3. clear swap file mapping, if exists
            VMKernel.removeSwapPage(pid, entry.vpn);

            // 4. clear TLB: just set the entry as invalid
            flushTLB();

            // no need to free own page table, because we will reinitialize it next time
        }
         */
        super.unloadSections();

	}

	@Override
    public int readVirtualMemory(int vaddr, byte[] data) {
	    return readVirtualMemory(vaddr, data, 0, data.length);
    }


	@Override
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
	    /**
	    int vpn = Processor.pageFromAddress(vaddr);
	    int total = Processor.pageFromAddress(vaddr + length);
        for (int i = vpn; i < total + 1; i++) {
            if (!VMKernel.pageInMemory(pid, i)) {
                //System.out.println("Begin from here!!!!!!!!!!!!!  " + i);
                handlePageFault(i);
            }
            sync(pageTable[i], VMKernel.getEntry(pid, i));
            //pageTable[i].dirty = pageTable[i].used = true;
            VMKernel.pin(VMKernel.getEntry(pid, i).ppn);
        }
        //return super.readVirtualMemory(vaddr, data, offset, length);
        */
        //Super():
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
	    /**
        int vpn = Processor.pageFromAddress(vaddr);
        int total = Processor.pageFromAddress(vaddr + length);
        System.out.println("WWWWWWWWWW try to write virtual memory!!!");
        for (int i = vpn; i < total + 1; i++) {
            if (!VMKernel.pageInMemory(pid, i)) {
                //System.out.println("Begin from here!!!!!!!!!!!!!  " + i);
                handlePageFault(i);
            }
            sync(pageTable[i], VMKernel.getEntry(pid, i));
            VMKernel.pin(VMKernel.getEntry(pid, i).ppn);
            pageTable[i].dirty = pageTable[i].used = true;
            int ppn = pageTable[i].ppn;
            VMKernel.physicalPages[ppn].entry.dirty = true;
            System.out.println("==============I'm dirty now, I'm ppn ------- " + ppn);
        }
        //return super.readVirtualMemory(vaddr, data, offset, length);
        */
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
        TranslationEntry entry = pageTable[vpn];
        if (entry.valid == false || entry.vpn != vpn) {
            return -1;
        }
        if (write) {
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
        VMKernel.physicalPages[entry.ppn].pinned = true;
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
        VMKernel.physicalPages[entry.ppn].pinned = false;
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

    /** Try to handle TLBMiss exception
     *  Firstly, find a vpn->ppn match from page table, then replace an entry in TLB by it
     *  If entry is invalid, then raise page fault, and handle it
     * @param vaddr
     * @return
     */
	private void handleTLBMiss(int vaddr) {


		Lib.debug(dbgVM, "\thandleTLBMissException: begin to handle exception");
        int vpn = Processor.pageFromAddress(vaddr);
        Lib.assertTrue(vpn >= 0 && vpn <= numPages);
        // entry must be in page table, let's check if it's valid
        TranslationEntry entry = pageTable[vpn];
        if (!entry.valid) handlePageFault(vpn);
        boolean full = true;
        int index = Lib.random(Machine.processor().getTLBSize());
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            if (Machine.processor().readTLBEntry(i).valid == false) {
                index = i;
                full = false;
                break;
            }
        }
        if (full) {
            for (int i = 0; i > Machine.processor().getTLBSize(); i++) {
                TranslationEntry TLBEntry = new TranslationEntry(Machine.processor().readTLBEntry(i));
                TranslationEntry PTEntry = pageTable[vpn];
                if (TLBEntry.valid) {
                    PTEntry.used = TLBEntry.used;
                    PTEntry.dirty = TLBEntry.dirty;
                }
            }
        }



        TranslationEntry newEntry = new TranslationEntry(entry);
        Machine.processor().writeTLBEntry(index, newEntry);
	}
	private void handlePageFault(int vpn) {
	    int ppn;
	    if (VMKernel.freePagesNum() != 0) {
	        ppn = VMKernel.getPage();
	        allocatePage(vpn, ppn, false);
        }
        else {
            for (int i = 0; i > Machine.processor().getTLBSize(); i++) {
                TranslationEntry TLBEntry = new TranslationEntry(Machine.processor().readTLBEntry(i));
                TranslationEntry PTEntry = pageTable[vpn];
                if (TLBEntry.valid) {
                    PTEntry.used = TLBEntry.used;
                    PTEntry.dirty = TLBEntry.dirty;
                }
            }
            do {
                ppn = clockAlgorithm();
            }
            while (VMKernel.physicalPages[ppn].pinned);
            allocatePage(vpn, ppn, true);
        }
    }

    protected int clockAlgorithm() {
	    while (pageTable[VMKernel.physicalPages[clock].vpn].used) {
	        pageTable[VMKernel.physicalPages[clock].vpn].used = false;
	        clock = (clock + 1) % VMKernel.physicalPages.length;
        }
        int ppn = clock;
	    clock = (clock + 1) % VMKernel.physicalPages.length;
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
        VMKernel.physicalPages[ppn].vpn = vpn;
        VMKernel.physicalPages[ppn].process = this;
    }
    protected void handleEvict(int vpn, int ppn) {
        int oldVPN = VMKernel.physicalPages[ppn].vpn;
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
        VMKernel.physicalPages[ppn].vpn = vpn;
        VMKernel.physicalPages[ppn].process = this;

        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry TLBEntry = new TranslationEntry(Machine.processor().readTLBEntry(i));
            if (TLBEntry.ppn == ppn) {
                TLBEntry.valid = false;
                pageTable[TLBEntry.vpn].valid = false;
            }
            Machine.processor().writeTLBEntry(i, TLBEntry);
        }

    }
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
    /**
	private TranslationEntry handlePageFault(int vpn) {

	    // 1. allocate a page in memory
        TranslationEntry entry = VMKernel.allocatePage(pid, vpn);

        for (int i = 0; i < pageTable.length; i++) {
            TranslationEntry myEntry = pageTable[i];

            if (myEntry.valid && myEntry.ppn == entry.ppn) {
                myEntry.valid = false;
                myEntry.readOnly = false;
            }

        }
        pageTable[vpn].valid = true;
        pageTable[vpn].ppn = entry.ppn;
        pageTable[vpn].used = true;
        pageTable[vpn].readOnly = false;

        if (!VMKernel.pageInSwapFile(pid, vpn)) {
            if (vpn >= 0) {
                System.out.println("&&&&&Wo jin lai le, wo shi: " + vpn);
                for (int i = 0; i < coff.getNumSections(); i++) {
                    CoffSection section = coff.getSection(i);
                    for (int j = 0; j < section.getLength(); j++) {
                        if (vpn == section.getFirstVPN() + j) {

                            section.loadPage(j, entry.ppn);
                            System.out.println(">>>>>>>>>>>>>>>load code in!!! " + "VPN: " + vpn + "PPN: " + entry.ppn);
                            if (section.isReadOnly()) {
                                entry.readOnly = true;
                                pageTable[vpn].readOnly = true;
                                VMKernel.getEntry(pid, vpn).readOnly = true;
                                VMKernel.physicalPages[entry.ppn].entry.readOnly = true;
                            }
                        }
                    }
                }
            }
            else {
                Arrays.fill(Machine.processor().getMemory(), entry.ppn * pageSize,
                        (entry.ppn + 1) * pageSize, (byte)0);
            }
        }
        else {
            VMKernel.swapSpace.swapIn(pid, vpn, entry.ppn);
        }
        return entry;
    }
     */

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	private static TranslationEntry[] pageTable;

	private static int clock = 0;

}
