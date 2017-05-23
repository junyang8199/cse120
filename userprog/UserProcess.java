package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];

		openFiles = new OpenFile[maxOpenFile];

		//Stand input and stand output of this process.
		stdin = UserKernel.console.openForReading();
		stdout = UserKernel.console.openForWriting();

		//A process's file descriptors 0 and 1 must refer to stdin and stdout.
		openFiles[0] = stdin;
		openFiles[1] = stdout;

		//Gdt a pid from UserKernel.
		pid = UserKernel.getNextPID();

		//Set parent and children processes.
		parent = null;
		children = new LinkedList<UserProcess>();

		//Increase the number of current live processes.
		UserKernel.increPros();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {

		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
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
			int vpn = Processor.pageFromAddress(vaddrStart);
			int pagePosition = Processor.offsetFromAddress(vaddrStart);
			int bytesToRead = Math.min(pageSize - pagePosition, length - readBytes);
			int phyaddrStart = pageTable[vpn].ppn * pageSize + pagePosition;
			System.arraycopy(memory, phyaddrStart, data,
					offset + readBytes, bytesToRead);
			readBytes += bytesToRead;
		}

		return readBytes;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {

		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
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
			int vpn = Processor.pageFromAddress(vaddrStart);
			int pagePosition = Processor.offsetFromAddress(vaddrStart);
			int bytesToWrite = Math.min(pageSize - pagePosition, length - writeBytes);
			int phyaddrStart = pageTable[vpn].ppn * pageSize + pagePosition;
			System.arraycopy(data, offset + writeBytes, memory,
					phyaddrStart, bytesToWrite);
			writeBytes += bytesToWrite;
		}

		return writeBytes;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
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

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		//Access global resource--free pages list, acquire the lock
		UserKernel.memoryLock.acquire();

		//Check if there are sufficient free physical pages.
		if (numPages > UserKernel.freePagesNum()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		//Available physical pages are sufficient, fill in the  page table.
		//Each vpn of this process is assigned with a ppn.
		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, UserKernel.getPage(),
					true, false, false, false);
		}

		//End of managing the free pages list, release the lock.
		UserKernel.memoryLock.release();

		//Load sections
		//For the Coff object, load all its Coff Sections.
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			//For each CoffSection object, load all its pages.
			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				//Mark the page as read-only if this section is ready-only.
				pageTable[vpn].readOnly = section.isReadOnly();

				//Load content in that physical page.
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.memoryLock.acquire();

		for (int i = 0; i < pageTable.length; i++) {
			TranslationEntry entry = pageTable[i];

			if (entry != null && entry.valid) {
				UserKernel.addPage(entry.ppn);
			}
			pageTable[i] = null;
		}

		UserKernel.memoryLock.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		//Only root process can halt.
		if (pid == 0) {
			Machine.halt();
		}

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the creat(char *name) system call.
	 */
	private int handleCreat(int vaddr_nameStart) {
		//Read the name, transfer virtual address to physical address.
		String fileName = readVirtualMemoryString(vaddr_nameStart, maxArgLen);

		//Check if the file name is valid.
		if (fileName == null) {
			Lib.debug(dbgProcess,
					"Create fail: could not read file name from virtual memory.");
			return -1;
		}

		//Find a descriptor for this new file.
		int descriptor = findDescriptor();
		if (descriptor == -1) {
			Lib.debug(dbgProcess,
					"Create fail: file descriptors are insufficient.");
			return -1;
		}

		//Open the file. If it doesn't exist, create one.
		OpenFile newFile = ThreadedKernel.fileSystem.open(fileName, true);

		//Check if the creation is successful.
		if (newFile == null) {
			Lib.debug(dbgProcess,
					"Create fail: could not open file name from file system.");
			return -1;
		} else {
			openFiles[descriptor] = newFile;
			return descriptor;
		}
	}

	/** Find an descriptor. If no more available, return -1. **/
	private int findDescriptor() {
		for (int i = 2; i < maxOpenFile; i++) {
			if (openFiles[i] == null) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Handle the open(char *name) system call.
	 */
	private int handleOpen(int vaddr_nameStart) {
		//Read the name, transfer virtual address to physical address.
		String fileName = readVirtualMemoryString(vaddr_nameStart, maxArgLen);

		//Check if the file name is valid.
		if (fileName == null ) {
			Lib.debug(dbgProcess,
					"Open fail: file descriptors are insufficient.");
			return -1;
		}

		//Find a descriptor for this new file.
		int descriptor = findDescriptor();
		if (descriptor == -1) {
			Lib.debug(dbgProcess,
					"Open fail: file descriptors are insufficient.");
			return -1;
		}

		//Open the file. If it doesn't exist, return null.
		OpenFile newFile = ThreadedKernel.fileSystem.open(fileName, false);

		//Check if the creation is successful.
		if (newFile == null) {
			Lib.debug(dbgProcess,
					"Open fail: could not open file name from file system.");
			return -1;
		} else {
			openFiles[descriptor] = newFile;
			return descriptor;
		}
	}

	/**
	 * Handle the read(int fileDescriptor, void *buffer, int count) system call.
	 */
	private int handleRead(int desp, int vaddr_bufStart, int count) {
		//Check if the file descriptor is valid.
		if (desp < 0 || desp > 15 || openFiles[desp] == null) {
			return -1;
		}

		//Read bytes from the file into a temporary array.
		// The file position is advanced by the read method.
		byte temp[] = new byte[count];
		int numByteRead = openFiles[desp].read(temp, 0, count);

		//If reading fails.
		if (numByteRead < 0) {
			return -1;
		}
		if (numByteRead == 0) {
			return 0;
		}

		//Write those bytes to the buffer in memory.
		int numByteWrite = writeVirtualMemory(vaddr_bufStart, temp, 0, numByteRead);

		//If writing fails.
		if (numByteWrite < numByteRead) {
			return -1;
		}

		return numByteWrite;
	}

	/**
	 * Handle the write(int fileDescriptor, void *buffer, int count) system call.
	 */
	private int handleWrite(int desp, int vaddr_bufStart, int count) {
		//Check if the file descriptor is available.
		if (desp < 0 || desp > 15 || openFiles[desp] == null) {
			return -1;
		}

		//Read bytes from the buffer in memory into a temporary array.
		byte temp[] = new byte[count];
		int readNumber = readVirtualMemory(vaddr_bufStart, temp);
		//If there's nothing to read in the buffer.
		if(readNumber <= 0)
			return 0;

		//Write those bytes into the file.
		//The file position is advanced by the write method.
		int numByteWrite = openFiles[desp].write(temp, 0, count);

		//Error. Did not finish writing.
		if(numByteWrite < count)
			return -1;

		return numByteWrite;
	}

	/**
	 * Handle the close(int fileDescriptor) system call.
	 */
	private int handleClose(int desp) {
		//Check if the file descriptor is available.
		if (desp < 0 || desp > 15 || openFiles[desp] == null) {
			return -1;
		}

		//Close this file and release any associated system resources.
		openFiles[desp].close();
		openFiles[desp] = null;

		return 0;
	}

	/**
	 * Handle the unlink(char *name) system call.
	 */
	private int handleUnlink(int vaddr_nameStart) {
		//Read the name, transfer virtual address to physical address.
		String fileName = readVirtualMemoryString(vaddr_nameStart, maxArgLen);

		if (fileName == null) {
			return 0;
		}

		//Delete the file from file system.
		if(ThreadedKernel.fileSystem.remove(fileName)) {
			return 0;
		} else {
			return -1;
		}
	}

	/**
	 * Handle the exec(char *file, int argc, char *argv[]) system call.
	 */
	private int handleExec(int fileAddr, int argc, int argAddr) {
		// args must be non-negative.
		if (argc < 0) {
			return -1;
		}

		//Read the file name.
		String fileName = readVirtualMemoryString(fileAddr, maxArgLen);
		if (fileName == null) {
			return -1;
		}
		//File name must include the ".coff" extension.
		String extension = fileName.substring(fileName.length()-5, fileName.length());
		if (!extension.equals(".coff")) {
			return -1;
		}

		//Read arguments.
		String[] args = new String[argc];
		for (int i = 0; i < argc; i++) {
			//Read the address of the argument.
			byte[] address = new byte[4];
			if (readVirtualMemory(argAddr+i*4, address) == 4) {
				//Read the argument value.
				args[i] = readVirtualMemoryString
						(Lib.bytesToInt(address, 0), maxArgLen);
			} else {
				return -1;
			}
		}

		//Create a new process.
		UserProcess childProcess = UserProcess.newUserProcess();
		children.add(childProcess);
		childProcess.setParent(this);

		//Execute the child program.
		if (childProcess.execute(fileName, args)) {
			return childProcess.getPID();
		} else {
			return -1;
		}
	}

	/**
	 * Handle the join(int processID, int *status) system call.
	 */
	private int handleJoin(int childPID, int statusAddr) {
		//Find the child process by PID.
		UserProcess childProcess = null;
		for (UserProcess child : children) {
			if (child.getPID() == childPID) {
				childProcess = child;
				break;
			}
		}
		if (childProcess == null) {
			return -1;
		}

		//Let the child process join.
		if (childProcess.thread == null) {
			return -1;
		} else {
			childProcess.thread.join();
		}

		//Store the exit status of the child process.
		int childExitStatus = childProcess.exitStatus;
		byte[] status = new byte[4];
		Lib.bytesFromInt(status, 0, childExitStatus);
		int statWriteBytes = writeVirtualMemory(statusAddr, status);
		//Writing fails
		if (statWriteBytes != 4) {
			return -1;
		}
		//Child exited normally.
		if (childProcess.exitNormally) {
			return 1;
		} else {
		//Child exited as a result of an unhandled exception.
			return 0;
		}
	}

	/**
	 * Handle the exit(int status) system call.
	 */
	private int handleExit(int status) {
		//Close Coff.
		coff.close();

		//Close opened files.
		for (int i = 0; i < openFiles.length; i++) {
			if (openFiles[i] != null) {
				openFiles[i].close();
				openFiles[i] = null;
			}
		}

		//Set exit status.
		exitStatus = status;
		exitNormally = true;

		unloadSections();
		UThread.finish();

		//The last exiting process should terminate the kernel.
		//Checking if it's the last one and decreasing the process number
		//should be done synchronously.
		UserKernel.numProsLock.acquire();

		int leftProsNum = UserKernel.getNumPros();
		if (leftProsNum == 1) {
			Kernel.kernel.terminate();
		}
		UserKernel.decrePros();

		UserKernel.numProsLock.release();

		return 0;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
			    System.out.println("handling halt");
				return handleHalt();

			case syscallCreate:
			    System.out.println("handling create");
				return handleCreat(a0);

			case syscallOpen:
                System.out.println("handling open");
				return handleOpen(a0);

			case syscallRead:
                System.out.println("handling read");
				return handleRead(a0, a1, a2);

			case syscallWrite:
                System.out.println("handling write");
				return handleWrite(a0, a1, a2);

			case syscallClose:
                System.out.println("handling close");
				return handleClose(a0);

			case syscallUnlink:
                System.out.println("handling unlink");
				return handleUnlink(a0);

			case syscallExec:
                System.out.println("handling exec");
				return handleExec(a0, a1, a2);

			case syscallJoin:
                System.out.println("handling join");
				return handleJoin(a0, a1);

			case syscallExit:
                System.out.println("handling exit");
				return handleExit(a0);

			default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}

		return 0;
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
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** Get the pid of this process. */
	protected int getPID() {
		return pid;
	}

	/** Set parent for this process. */
	protected void setParent(UserProcess parentProcess) {
		parent = parentProcess;
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	/** The maximum length of strings passed as arguments to system calls
	 * is 256 bytes.
	 */
	private static final int maxArgLen = 256;

	/** A Process can have at most 16 files opened concurrently. **/
	private static final int maxOpenFile = 16;

	/** Files opened by this process. **/
	private OpenFile[] openFiles;

	/** Stand input and stand output of this process. **/
	private OpenFile stdin;

	private OpenFile stdout;

	/** PID of this process. */
	private int pid;

	/** Parent process. */
	private UserProcess parent;

	/** A list of child processes. */
	private LinkedList<UserProcess> children;

	/** The thread of this process. */
	private UThread thread;

	/** The exit status of this process. */
	private int exitStatus;

	/** If the process has exited normally. */
	private boolean exitNormally;
}
