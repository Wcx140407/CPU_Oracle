#!/bin/bash
java ParallelLU --datasets=3 --threads=24 \
    --size0=500 \
    --size1=1000 \
    --size2=2000
