GGroup member:
	• Junyang Li
	A53210366
	• Shenghong Wang
	A53224867
CContribution: We wrote and tested all the code together.

PPart I
	• To keep track of all opened files of the process using descriptors
	1.  Create field OpenFile[] openFiles in UserProcess class. The array indexes serve as descriptors.
	Descriptor 0 and 1 always refer to stdin and stdout of the processor.
	2. Create method findDescriptor. Use it to find an available descriptor which can only be greater than 1 and smaller than 16.

	• Handle create system call
	In UserProcess class
	Method: handleCreat
	1. Use UserProcess.readVirtualMemory to read the filename from virtual memory.
	If fail, return -1.
	2. Find an available descriptor for this new file.
	If fail, return -1.
	3. Open the file from the file system. If it doesn’t exist, create one (by setting the create argument of method ThreadKernel.fileSystem.open true).
	If fail, return -1.
	4. Put the file in the right slot in openFiles based on its descriptor. Return its descriptor. 

	• Handle the open system call
	In UserProcess class
	Method: handleOpen
	Same thing as handling the create system call. The only difference is not creating the file if it does not exist (by setting the create argument of method ThreadKernel.fileSystem.open false).

	• Handle the read system call
	In UserProcess class
	Method: handleRead
	1. Check if the descriptor passed in is valid.
	If fail, return -1.
	2. Use OpenFile.read to read bytes from the file into a byte array. The file position is advanced by that method.
	If 0 bytes read, return 0.
	3. Use UserProcess.writeVirtualMemory to write the bytes in the array to the buffer in virtual memory. 
	If writing fails, return -1.
	Return the number of bytes have been written.

	• Handle the write system call
	In UserProcess class
	Method: handleWrite
	1. Check if the descriptor passed in is valid.
	If fail, return -1.
	2. Using UserProcess.readVirtualMemort to Read bytes from the passed in buffer in memory into a temporary array of bytes.
	If the number of bytes have been read is 0, return 0.
	3. Use OpenFile.write to write bytes into the file from the byte array. The file position is advanced by that method.
	If the number of bytes have been written is smaller than the number of bytes supposed to be write, return -1.

	• Handle the close system call
	In UserProcess class
	Method: handleClose
	1. Check if the descriptor passed in is valid.
	If fail, return -1.
	2. Use OpenFiles.close to close the file.
	3. Release the file's slot in the openFIle array.

	• Handle the unlink system call
	In UserProcess class
	Method: handleUnlink
	1. Use UserProcess.readVirtualMemory to read the filename from virtual memory.
	If fail, return 0 because that means the file doesn't exisit in the first place.
	2. Delete the file from the file system using ThreadKernel.fileSystem.remove.
	If fail, return -1.


Part II
In UserKernel class:
	• Create private field LinkedList<Integer> freePages to keep track on indexes of  free physical pages.
	• Create protected field Lock memoryLock so UserProcess objects can access freePages synchronously.
	• Create protected methods to for the freepages field so UserProcess objects can interact with it:
	freePageNum: return the number of availabel physical pages. Should acquire memoryLock before calling it.
	getPage: remove one free physical page from freePages and return the index of it. Should acquire memoryLock and check the number of free pages before calling it.
	addPage: add one physical page back to the freePages list.

In UserPorcess class:
	• Method loadSections:
	1. Require free physical pages from the memory:
	Acquire UserKernel.memoryLock;
	Check if there are sufficient free physical pages, if not, return false;
	Remove those pages from UserKernel.freePages using UserKernel.getPage and get ppns. Use the ppns to create vpn-ppn translation entries by creating TranslationEntry objects to each slot of array pageTable;
	Release UserKernel.memoryLock.
	2. Lock coff sections:
	For the UserProcess.coff, load all its Coff sections;
	For each Coff section, load all its virtual pages into physical pages using CoffSection.getFirstVPN to have vpns and pageTable[vpn].ppn to have ppns, and using CoffSection.loadPage to load content. If any section is read-only(use CoffSection.isReadOnly to check), mark it as read-only in pageTable.

	• Method unloadSections:
	1. Relocate all physical pages:
	Acquire UserKernel.memoryLock;
	Add pages this process occupied back to UserKernel.freePages using UserKernel.addPage;
	Set entries in pageTable to null;
	Release UserKernel.memoryLock.

	• Method readVirtualMemory(intvaddr,byte[]data,intoffset,intlength)
	1. Check if the offset and length arguments passed in are valid
	2. Check if this reading action would exceeds the process's address space by computing the first and last vpn using Processor.pageFromAddress. If the first vpn < 0 or the last vpn > numPages, return 0.
	3. Get the reference of the physical memory array by using Machine.processor().getMemory.
	4. Read data from physical memory to the data array passed in:
	Keep track of where we have read to by using readBytes to record how many bytes have been read;
	Keep reading while readBytes < length, each time we won't let the reading exceeds one page;
	Compute vpn by:
		vpn = Processor.pageFromAddress(vaddr+readBytes);
	Compute offset by:
		Offset = Processor.offsetFromAddress(vaddr+readBytes);
	Compute physical address by:
		phyaddr  = pageTable[vpn].ppn*pageSize+pagePosition;
	Compute how many bytes to read this time by:
		bytesToRead=Math.min(pageSize-offset, length-readBytes);
	Copy content from memory to the buffer passed in using System.arraycopy
	Increase readBytes by bytesToRead and go to the next iteration of reading;
	Return readBytes when readBytes  = length.

	• Method writeVirtualMemory(intvaddr,byte[]data,intoffset,intlength)
	Same logic with readVirtualMemory but use System.arraycopy to copy data from the array passed in into momory.


Part III
In UserKernel class:
	• Create field nextPID as a counter of IDs assigned to processes.
	• Create field numProcess to keep track of the number of live processes.
	• Create a lock numProsLock so UserProcess objects can use it to access the number of  nextPID and numProcess synchronously.
	• Create methods:
	getNextPID(): Get the next PID for a new process.
	increPros():Increase the number of live processes.
	ecrePros(): Decrease the number of live processes.
	getNumPros(): Get the number of live processes.

In UserPorcess class:
	• Create fields:
	Int pid: the process ID.
	UserProcess parent: the parent process.
	LinkedList <UserProcess>children: a list of child processes.
	Uthread thread: the thread this process owns to execute .coff file.
	Int exitStatus
	Boolean exitNormally

	• When a process is created, get a process ID by calling UserKernel.getNextPID.
	Increase the number of live processes in the kernel by calling  UserKernel.incresePros.

	• Creat methods killMyself to clean up a process who fails to laod coff files and to execute.

	• Handle exec system call
	Method: handleExec
	1. Check is arguments are valid
	2. Read the file name and check if it's valid
	3. Read all arguments into a buffer array  from virtual memory
	4. Create a new process using UserProcess.newUserProcess
	5. Make the child process execute the file passed in using  UserProcess.execute. If the execution is successful, add the new process into this process's child list, set this child's parent process as this process, return the child process's PID. If it fails, kill the new process using UserProcess.killMyself().

	• Handle join system call
	Method: handleJoin
	1. Find the child process by PID in the children list. If it cannot be found, return -1.
	2. Let the child process join using Uthread.join();
	3. Write the child's exit state to memory. If it fails, return -1.
	4. Return 1 if child exits normally and 0 otherwise.

	• Handle the exit system call
	Method: handleEixt
	1. Close coff using Coff.close;
	2. Go throught the openFiles list and close every file using OpenFile.close and clean up slots in openFiles.
	3. Set exit status.
	4. Clean up sections it takes using unloadSections.
	5. Aqcuire numProsLock, check if it's the last process, if it is, call Kernel.kermel.terminate. Decrese the number of live processes in the kernel by calling UserKernel.decrePros. Release numProsLock.
	6. Call UThread.finish.

