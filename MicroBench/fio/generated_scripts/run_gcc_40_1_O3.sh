#!/bin/bash
./fio_gcc_O3 --datasets=1 --threads=40 --per-dataset=40 \n    --name0=test1 --filename0=file1.dat --directory0=/tmp \n    --filesize0=100M --rw0=w --pattern0=seq --runtime0=5
