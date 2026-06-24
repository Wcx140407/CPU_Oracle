#!/bin/bash
#g++ -o matmul_gcc matmul_pthread.cpp -lpthread
#./matmul_gcc <matrix size> <thread> <replicate> <block size>
./matmul_gcc 1024 16 3 64
