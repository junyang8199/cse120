#include "syscall.h"
#include "stdlib.h"
#include "stdio.h"

int main() {

    //Test create system call
    char* file1 = "file1.txt";

    int creat1, creat2;

    creat1 = creat(file1);
    if (creat1 == 2) {
    	printf ("Successfully create file %c\n", file1);
    } else {
        printf ("failed to write character (r = %d)\n", file2);
        exit (-1);
    }

}
