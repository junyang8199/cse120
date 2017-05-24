#include "stdio.h"
#include "stdlib.h"
#include "syscall.h"

#define INVALID_PID -1
#define ERROR -1

int main(int argc, const char* argv[]){
	
	char* invalid_file;
	char* invalid_file2;
	char* valid_file;
	char* matmult_file;
	
	int* status;
	int exec_return_val_1;
	int exec_return_val_2;
	int join_return_val;
	
	invalid_file = "Invalid.coff";
	invalid_file2 = "test_systemCall_1";
	valid_file = "test_systemCall_1.coff";
	matmult_file = "matmult.coff";

	char* arg[5];	

	exec_return_val_1 = exec(invalid_file, argc, arg);
	assert(exec_return_val_1 == ERROR);

	exec_return_val_1 = exec(invalid_file2, argc, arg);
	assert(exec_return_val_1 == ERROR);

	exec_return_val_1 = exec(valid_file, argc, arg);
	assert(exec_return_val_1 >= 0);

	exec_return_val_2 = exec(matmult_file, argc, arg);
    assert(exec_return_val_2 >= 0);

    join_return_val = join(INVALID_PID, status);
    	assert(join_return_val == ERROR);

	join_return_val = join(exec_return_val_1, status);
	assert(join_return_val == 1);

	join_return_val = join(exec_return_val_2, status);
    assert(join_return_val == 2);

	join_return_val = join(exec_return_val_2, status);
    assert(join_return_val == ERROR);

    printf("ALL TASK %d TESTS over, machie should be halt!!!!!!!!!!!!\n\n",2);

	return 0;
}
