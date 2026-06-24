#!/bin/bash
java -cp . ParallelLU --datasets=4 --threads=38 \
    --size0=500 \
    --size1=700 \
    --size2=900 \
    --size3=1100
