#include "syscall.h"
#include "stdlib.h"
#include "stdio.h"

int main() {

    /*
     * Test create system call.
     */

    //T1: simple create.
    char* file1 = "file1.txt";
    int creat1;
    creat1 = creat(file1);
    if (creat1 == 2) {
    	printf ("Successfully create file %c\n", file1);
    } else {
        printf ("Failed to create file %c\n", file1);
        exit (-1);
    }

    //T2: process can only have 16-2 = 14 files.
    char* files;
    int i = 0;
    int descriptors;
    while (i < 14) {
        descriptors = creat(sprintf(files,"%d",i));
        if (descriptors < 2 || descriptors > 15) {
            printf ("Wrong descriptor %d\n", descriptors);
            exit (-1);
        }
        i++;
    }
    char* file15 = "file15.txt";
    int errCreat;
    errCreat = creat(file15);
    if (errCreat != -1) {
        printf ("Failed to stop creating %d\n", file15);
        exit (-1);
    }


     /*
      * Test close system call.
      */




     /*
      * Test open system call.
      */

     //T1: opens an existing file and returns a file descriptor for it.
     int open1;
     open1 = open(file1);
     if (open1 != 2) {
        printf ("Failed to open file %c\n", file1);
        exit (-1);
     }
     while (i < 14) {
             descriptors = creat(sprintf(files,"%d",i));
             if (descriptors < 2 || descriptors > 15) {
                 printf ("Wrong descriptor %d\n", descriptors);
                 exit (-1);
             }
             i++;
         }







    halt();
}
