#!/bin/bash
#gcc -o fio_gcc fio_pthread.c -lpthread -lm
./fio_gcc --datasets=4 --threads=32 --per-dataset=8 \
    --name0=test1 --filename0=file1.dat --directory0=/tmp \
    --filesize0=100M --rw0=w --pattern0=seq --runtime0=10 \
    --name1=test2 --filename1=file2.dat --directory1=/tmp \
    --filesize1=200M --rw1=w --pattern1=rand --runtime1=10 \
    --name2=test3 --filename2=file1.dat --directory2=/tmp \
    --filesize2=100M --rw2=r --pattern2=seq --runtime2=10 \
    --name3=test4 --filename3=file2.dat --directory3=/tmp \
    --filesize3=200M --rw3=r --pattern3=rand --runtime3=10 \
