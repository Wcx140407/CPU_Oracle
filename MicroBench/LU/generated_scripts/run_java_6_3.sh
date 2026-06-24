#!/bin/bash
java -cp . ParallelLU --datasets=3 --threads=6 \
    --size0=500 \
    --size1=700 \
    --size2=900
