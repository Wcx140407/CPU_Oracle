#!/bin/bash
#g++ -o aes_gcc aes_pthread.cpp -lpthread -lm
#./aes_concurrent <测试轮数> <线程数> <配置文件> <运行模式>
./aes_gcc 2 32 aes_data.csv con
