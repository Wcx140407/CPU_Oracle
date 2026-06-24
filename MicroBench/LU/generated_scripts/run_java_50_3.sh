#!/bin/bash
java -cp . ParallelLU --datasets=3 --threads=50 \
    --size0=500 \
    --size1=700 \
    --size2=900
