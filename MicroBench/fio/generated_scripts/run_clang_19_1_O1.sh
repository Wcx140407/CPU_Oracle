#!/bin/bash
./fio_clang_O1 --datasets=1 --threads=19 --per-dataset=19 \n    --name0=test1 --filename0=file1.dat --directory0=/tmp \n    --filesize0=100M --rw0=w --pattern0=seq --runtime0=5
