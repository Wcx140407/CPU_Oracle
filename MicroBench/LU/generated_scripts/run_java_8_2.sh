#!/bin/bash
java -cp . ParallelLU --datasets=2 --threads=8 \
    --size0=500 \
    --size1=700
