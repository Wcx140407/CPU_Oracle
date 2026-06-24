#!/bin/bash
./fio_gcc_O1 --datasets=2 --threads=59 --per-dataset=29 \n    --name0=test1 --filename0=file1.dat --directory0=/tmp \n    --filesize0=100M --rw0=w --pattern0=seq --runtime0=5 \n    --name1=test2 --filename1=file1.dat --directory1=/tmp \n    --filesize1=100M --rw1=r --pattern1=rand --runtime1=5
