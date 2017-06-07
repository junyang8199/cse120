package nachos.vm;

import nachos.machine.*;
import nachos.userprog.*;
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

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {

        // flush the TLB to handle context switches
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry entry = Machine.processor().readTLBEntry(i);
            entry.valid = false;
            Machine.processor().writeTLBEntry(i, entry);
        }

        // TODO: need to synchronize TLB???
        super.saveState();
    }

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Lib.debug(dbgVM, "\t");
        /**
         * What are needed to sync?
         * 1. other process may evict the page that belongs to me
         * 2.
         */

	}

    /**
     * Synchronize TLB using process' page table indexed by vpn
     * @param vpn
     */
	public void syncTLB(int vpn) {
        Machine.processor().writeTLBEntry(vpn, pageTable[vpn]);
    }

    private void syncPageTable(TranslationEntry entry) {
        pageTable[entry.vpn] = entry;
    }

    @Override
	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
        // initialize the page table and set them as invalid
		UserKernel.memoryLock.acquire();
		this.pageTable = new TranslationEntry[Machine.processor().getNumPhysPages()];
		for (int i = 0; i < pageTable.length; i++) {
			pageTable[i] = new TranslationEntry(i, i,
					false, false, false, false);
		}
		UserKernel.memoryLock.release();
		return true;
	}

	@Override
	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {

	    VMKernel.memoryLock.acquire();
        for (TranslationEntry entry: pageTable) {

            // 1. free this page in memory
            if (VMKernel.pageInMemory(pid, entry.vpn)) VMKernel.freeThePage(entry.ppn);

            // 2. clear mapping in inverted page table, if exists
            VMKernel.removeMapping(pid, entry.vpn);

            // 3. clear swap file mapping, if exists
            VMKernel.removeSwapPage(pid, entry.vpn);

            // 4. clear TLB: just set the entry as invalid
            entry.valid = false;
            Machine.processor().writeTLBEntry(entry.vpn, entry);

            // no need to free own page table, because we will reinitialize it next time
        }
        VMKernel.memoryLock.release();
	}

	@Override
    public int readVirtualMemory(int vaddr, byte[] data) {
	    return readVirtualMemory(vaddr, data, 0, data.length);
    }
	@Override
    /**
     * Check if virtual memory is valid
     * That is, if there is correspond mapping in inverted page table
     * If there isn't, then handle page fault.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
	    int vpn = Processor.pageFromAddress(vaddr);
	    int total = Processor.pageFromAddress(vaddr + length);

	    syncTLB(vpn);
        for (int i = vpn; i < total + 1; i++) {
            int pid = super.pid;
            if (VMKernel.pageInMemory(pid, i))
                handlePageFault(vpn);
        }
        return super.readVirtualMemory(vaddr, data, offset, length);
    }

    @Override
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    @Override
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        int vpn = Processor.pageFromAddress(vaddr);
        int total = Processor.pageFromAddress(vaddr + length);

        syncTLB(vpn);
        for (int i = vpn; i < total + 1; i++) {
            int pid = super.pid;
            if (VMKernel.pageInMemory(pid, i))
                handleTLBMiss(vaddr);
        }
        return super.readVirtualMemory(vaddr, data, offset, length);
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

        // entry must be in page table, let's check if it's valid
        TranslationEntry entry = pageTable[vpn];
        int index = Lib.random(Machine.processor().getTLBSize());
        // if entry is valid, then replace a TLB entry and leave
        if (entry.valid) {
            // query invalid entry, or just randomly pick one
            for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
                if (Machine.processor().readTLBEntry(i).valid == false) {
                    index = i;
                    break;
                }
            }
            Machine.processor().writeTLBEntry(index, entry);
        }

        // ------demand paging------
        else {
			entry = handlePageFault(vpn);
			syncPageTable(entry);
			//syncTLB(entry.vpn);
        }
        Machine.processor().writeTLBEntry(index, entry);
	}

	private TranslationEntry handlePageFault(int vpn) {

	    // 1. allocate a page in memory
        TranslationEntry entry = VMKernel.allocatePage(pid, vpn);

        // 2. fill out the page
        if ((entry.readOnly || vpn < pageTable.length - 8) && VMKernel.pageInSwapFile(pid, vpn)) {
            for (int i = 0; i < coff.getNumSections(); i++) {
                CoffSection section = coff.getSection(i);
                for (int j = 0; i < section.getLength(); i++) {
                    if (vpn == section.getFirstVPN() + j) {
                        section.loadPage(i, entry.ppn);
                    }
                }
            }
        }
        else if (VMKernel.pageInSwapFile(pid, vpn)) {
            VMKernel.swapSpace.swapIn(pid, vpn, entry.ppn);
        }
        else {
            int pageStartAddr = Processor.makeAddress(entry.ppn, 0);
            Arrays.fill(Machine.processor().getMemory(), pageStartAddr,
                    pageStartAddr + pageSize, (byte)0);
        }
        // 3. sync the page table and TLB
        syncPageTable(entry);
        //syncTLB(vpn);
        return entry;
    }

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	private static TranslationEntry[] pageTable;

}
