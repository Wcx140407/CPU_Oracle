#!/bin/bash
#g++ -o LU_gcc LU_pthread.cpp -lpthread -lm
./LU_gcc --datasets=3 --threads=24 \
    --size0=500 \
    --size1=1000 \
    --size2=2000
