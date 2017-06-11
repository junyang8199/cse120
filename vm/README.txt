README

Shenghong Wang  A53224867
Junyang Li               A53210366

Shenghong Wang mainly wrote the code for project 3 and Junyang Li is mainly responsible for testing the code. We firstly used swap4.c to test and didn?t limit the number of available pages in memory to test basic function, that is, initialization, load section? After we began to limit the number of pages, we found that we loaded wrong content to pages which makes system keeping. After carefully check the lazy loading and swap mechanism, we finally solve this problem. But our code is still buggy in multi-process, it may because some race conditions happen. And we didn?t have time to solve this anymore because we had too much things to review at the final week?sorry?

1. Handle TLB Miss
When TLBMiss exception happens, we need to first check if mapping exists in process? page table. Because all entry in TLB will be set to invalid when context switches happens, but the correct mapping may still exist in process? page table. If mapping exists, then everything is OK, we just need to update the entry in TLB by picking a victim entry to overwrite. If mapping doesn?t exist in page table, it must mean that there is no corresponding page in memory for this vpn. Then page fault will be raised

2. Handle Page Fault
Page fault will only happen when a process demands a page that doesn?t exist in physical memory. So the first step is to allocate a page in memory. There are three possibilities. First, there still has available space in memory, then we simply allocate one. Second, all space has been occupied, we have to pick a victim to kick out, so we use clock algorithm to pick one and return this page. Third, all space has been occupied, but some of them are dirty page, which means other process has written these pages before and just hasn?t written back to disk. Then we should ?swap out? this page to swap space in disk to reserve them so that the data will not be lost.

3. Lazy Loading
After allocating a page in physical memory, we need to load the contents that we need into this page. We can track if the page has been swapped out before by querying the hash table in VMKernel. If it?s in swap space in disk, we just let it swap in, or we need to load contents from .coff file. To load contents from .coff file, we need to loop through to find corresponding section like we did in project 2.

4. Design 
- VMKernel
I create an inverted page table array to track all valid page in physical memory. The index for this array is ppn, and what are stored are a newly created class called MemoryPage, which represent vpn, UserProcess and if pages is pinned.
Besides, I also created a class called swapSpace representing the swap file in disk. In this class, I use hash table to track position (in swap space) --> UserProcess mapping. It also contains swap out and swap in method, as well as lock and condition to make sure mutex when more than one process need to swap in or swap out.
These are all changed I made in this project. I keep using free list storing ppn to get free page from memory.

- VMProcess
I didn?t initialize new class for VMProcess, but I put some method, like handle page fault, clock algorithm and allocate pages in VMProcess class. Logically, I think they should be in VMKernel because process should only be able to get access to TLB. But, it?s more convenient to load contents in page. 													

