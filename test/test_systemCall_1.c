#include "stdlib.h"
#include "syscall.h"
#include "stdio.h"

#define BUFFER_LENGTH 100
#define ERROR -1

void check_fd(int fd){
	assert(fd != 0 && fd != 1 && fd > 1 && fd <16);
}

void check_equality(int arg1, int arg2){
	assert(arg1 == arg2);
}

int main(){

	int i, fd0, fd1, fd_invalid, count_all, count_half;
	int toRet0, toRet1;

	int close0_status;
	int close1_status;
	int close_invalid;

	char* write_buffer0 = "This is the first thing that I want to write.\n";
	char* write_buffer1 = "Oops, still got something.\n";
	char* write_buffer_comp = "Boring";

	char read_buffer0[BUFFER_LENGTH];
	char read_buffer1[BUFFER_LENGTH];

	int toCompare;

    // create two files
	fd0 = creat("Shenghong.txt");
	fd1 = creat("Junyang.txt");
	fd_invalid = 5;

    // check the file decriptors
    check_fd(fd0);
    check_fd(fd1);

	count_all = strlen(write_buffer0);
	count_half = strlen(write_buffer1)/2;


    // write contents to two files
	toRet0 = write(fd0, write_buffer0, count_all);
	toRet1 = write(fd1, write_buffer1, count_half);

    // check the number of characters that have just been written
	check_equality(toRet0, count_all);
	check_equality(toRet1, count_half);

    // close the file
	close(fd0);
	close(fd1);

    // open the files again
	open("Shenghong.txt");
	open("Junyang.txt");

    // read the contents that have just been written
	toRet0 = read(fd0, read_buffer0, count_all);
	toRet1 = read(fd1, read_buffer1, count_half);

    // do we read them all?
	check_equality(toRet0, count_all);
	check_equality(toRet1, count_half);

	read_buffer0[toRet0]= '\0';
	read_buffer1[toRet1] = '\0';

    // compare the read buffer and write buffer
	toCompare = (int)(strcmp(read_buffer0,write_buffer0));
	assert(toCompare == 0);
	toCompare = (int)(strcmp(read_buffer1,write_buffer_comp));
	assert(toCompare == 0);

	close0_status = close(fd0);
	close1_status = close(fd1);
	close_invalid = close(fd_invalid);

	check_equality(close0_status, 0);
	check_equality(close1_status, 0);
	check_equality(close_invalid, ERROR);


    // we cannot close non-existent files
	for(i = 4; i < 17; i++){
		close_invalid = close(i);
		check_equality(close_invalid, ERROR);
	}

	//Attempt to write into a closed file
	toRet0 = write(fd0, write_buffer0, count_all);
	toRet1 = write(fd1, write_buffer1, count_half);

	check_equality(toRet0, ERROR);
	check_equality(toRet1, ERROR);


	//Open some files that are closed
	fd0 = open("Shenghong.txt");
	fd1 = open("Junyang.txt");

	check_fd(fd0);
	check_fd(fd1);

    // try to unlink the file
    int unlink1_status, unlink2_status;
    unlink1_status = unlink("Shenghong.txt");
    unlink2_status = unlink("Junyang.txt");
    check_equality(unlink1_status, 0);
    check_equality(unlink2_status, 0);

    // try to create more than 14 files
    creat("2.txt");
    creat("3.txt");
    creat("4.txt");
    creat("5.txt");
    creat("6.txt");
    creat("7.txt");
    creat("8.txt");
    creat("9.txt");
    creat("10.txt");
    creat("11.txt");
    creat("12.txt");
    creat("13.txt");
    creat("14.txt");
    creat("15.txt");

    fd_invalid = creat("16.txt");
    check_equality(fd_invalid, -1);


	printf("ALL TASK %d TESTS PASSED!!!! We can sleep. zzzzzzzzz\n\n",1);

	halt();
}
