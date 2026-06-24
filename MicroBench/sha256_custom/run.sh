#!/bin/bash
#g++ -o sha256_custom_gcc sha256_custom_pthread.cpp -lpthread -std=c++17
./sha256_custom_gcc -size 10485760 -nthreads 32 -nreps 10
