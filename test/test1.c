##include "syscall.h"

int main() {

    // check create file
    // return new file descriptor if success, return -1 if fail
    printf("1. create file check \n");
    char *filename = "file1";
    int descriptor = creat(filename);
    if (descriptor == -1) {
        printf("create failed");
        return -1;
    }
    else printf("create successful: file descriptor: " + descriptor);

    // check open file
    // can only open existed file
    // return file descriptor if success, return -1 if fail
    printf("2. open file check \n")
    int openResult = open(filename);
    if (openResult == -1) {
        printf("open failed");
        return -1;
    }
    else if (openResult == descriptor) {
        printf("open successful: file descriptor: " + descriptor_try);
    }

    // check write file
    // return number of bytes written if success, return -1 if fail
    printf("3. write file check \n")
    int* p = new int(1);
    void *toWrite = p;
    int writeResult = write(descriptor, toWrite, 1);
    if (writeResult != 1) {
        printf("write failed");
        return -1;
    }
    else if (writeResult == 1) printf("write successful");

    // check read file
    // return number of bytes read if success, return -1 if fail
    printf("4. read file check \n")
    void *toRead;
    int readResult = read(descriptor, toRead, 1);
    if (readResult != 1) {
        printf("read failed");
        return -1;
    }
    else if (readResult == 1) (printf("read successful");

    // check close
    // return 0 if success, return -1 if fail
    printf("5. close file check \n")
    int closeResult = close(descriptor);
    if (closeResult != 0) {
        printf("close failed");
        return -1;
    }
    else printf("close successful");

    // check uplink
    // return 0 if success, return -1 if fail
    printf("6. uplink file check \n")
    int uplinkResult = uplink(filename);
    if (uplinkResult == -1) {
        printf("uplink failed");
        return -1;
    }
    else printf("uplink successful");

    return 0;



}