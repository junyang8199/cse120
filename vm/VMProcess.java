package nachos.vm;

import nachos.machine.*;
import nachos.threads.ThreadedKernel;
import nachos.userprog.*;
import sun.misc.VM;

import java.io.EOFException;
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
	    Lib.debug(dbgVM, "\tsave state for process" + pid);
        flushTLB();
        super.saveState();
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
		Lib.debug(dbgVM, "\trestore state for process" + pid);

		// 1. synchronize TLB: set the entry as valid if this entry exist in inverted page table
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry TLBEntry= Machine.processor().readTLBEntry(i);
            if (VMKernel.pageInMemory(pid, TLBEntry.vpn)) {
                TranslationEntry entry = VMKernel.getEntry(pid, TLBEntry.vpn);
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
                PTEntry.valid = true;
                sync(PTEntry, entry);
            }
        }
	}

    /**
     * Used entry2 to synchronize entry1
     * @param entry1
     * @param entry2
     */
    private void sync(TranslationEntry entry1, TranslationEntry entry2) {
	    entry1.ppn = entry2.ppn;
	    entry1.used = entry2.used;
	    entry1.readOnly = entry2.readOnly;
	    entry1.dirty = entry2.dirty;
    }
    /**
    @Override
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        }
        catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        numPages++;


        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
            stringOffset += 1;
        }

        return true;
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
		UserKernel.memoryLock.acquire();
		pageTable = new TranslationEntry[super.numPages];
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
            flushTLB();

            // no need to free own page table, because we will reinitialize it next time
        }
        VMKernel.memoryLock.release();
	}
	private void flushTLB() {
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry entry = Machine.processor().readTLBEntry(i);
            entry.valid = false;
            Machine.processor().writeTLBEntry(i, entry);
        }
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

        if (vpn > numPages) {
            extendPageTable(vpn);
        }

        // entry must be in page table, let's check if it's valid
        TranslationEntry entry = pageTable[vpn];

        int index = Lib.random(Machine.processor().getTLBSize());
        // if entry is valid, then replace a TLB entry and leave
        if (entry.valid) {
            for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
                if (Machine.processor().readTLBEntry(i).valid == false) {
                    index = i;
                    break;
                }
            }
        }

        // ------demand paging------
        else {
			entry = handlePageFault(vpn);
			pageTable[vpn].valid = true;
			sync(pageTable[vpn], entry);
        }
        Machine.processor().writeTLBEntry(index, entry);
	}

	private TranslationEntry handlePageFault(int vpn) {

	    // 1. allocate a page in memory
        TranslationEntry entry = VMKernel.allocatePage(pid, vpn);

        // 2. fill out the page
        if (!VMKernel.pageInSwapFile(pid, vpn)) {
            if (vpn >= 0) {
                for (int i = 0; i > coff.getNumSections(); i++) {
                    CoffSection section = coff.getSection(i);
                    for (int j = 0; j < section.getLength(); j++) {
                        if (vpn == section.getFirstVPN() + j) section.loadPage(j, entry.ppn);
                    }
                }
                //CoffSection section = coff.getSection(vpn);
                //section.loadPage(vpn - section.getFirstVPN(), entry.ppn);
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
    private void extendPageTable(int vpn) {
	    TranslationEntry[] newTable = new TranslationEntry[vpn + 1];
	    for (int i = 0; i < numPages; i++) {
	        newTable[i] = pageTable[i];
        }
        pageTable = newTable;
	    numPages = vpn + 1;
    }

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';

	private static TranslationEntry[] pageTable;

	private static int numPages;

}
