#!/bin/bash
./fio_gcc_O2 --datasets=1 --threads=64 --per-dataset=64 \n    --name0=test1 --filename0=file1.dat --directory0=/tmp \n    --filesize0=100M --rw0=w --pattern0=seq --runtime0=5
