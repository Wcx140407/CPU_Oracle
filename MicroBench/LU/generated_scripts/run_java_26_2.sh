#!/bin/bash
java -cp . ParallelLU --datasets=2 --threads=26 \
    --size0=500 \
    --size1=700
