#!/bin/bash
#g++ -o sort_fixed_gcc sort_fixed_pthread.cpp -pthread -std=c++11
#./sort_fixed_gcc <input> <thread> 
./sort_fixed_gcc -i input_data1.in -t 32 
