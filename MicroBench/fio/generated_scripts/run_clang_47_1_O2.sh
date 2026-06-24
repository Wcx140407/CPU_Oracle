#!/bin/bash
./fio_clang_O2 --datasets=1 --threads=47 --per-dataset=47 \n    --name0=test1 --filename0=file1.dat --directory0=/tmp \n    --filesize0=100M --rw0=w --pattern0=seq --runtime0=5
