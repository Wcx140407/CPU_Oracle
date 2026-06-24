#!/bin/bash
#g++ -o stream_gcc stream_pthread.cpp -lpthread
./stream_gcc -size 20000000 -nthreads 32 -ntimes 10
