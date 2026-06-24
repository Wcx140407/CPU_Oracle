#!/bin/bash
#g++ -o compress_gcc compress_pthread.cpp -lpthread -lm
#./compress_gcc <thread> <input_config> <round>
./compress_gcc 32 dataset.conf 2
