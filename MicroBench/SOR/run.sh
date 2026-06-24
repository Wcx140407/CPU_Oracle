#!/bin/bash
#gcc -o SOR_gcc SOR_pthread.c -lpthread -lm
#./SOR_gcc <size> <thread> <min_measure_time> <random_seed>
./SOR_gcc -s 1000 -t 32 -m 5 -r 2024
