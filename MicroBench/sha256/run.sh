#!/bin/bash
#g++ -o sha256_gcc sha256_pthread.cpp -lpthread -lssl -lcrypto
./sha256_gcc -size 10485760 -nthreads 32 -nreps 10
