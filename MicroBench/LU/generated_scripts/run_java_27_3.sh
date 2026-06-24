#!/bin/bash
java -cp . ParallelLU --datasets=3 --threads=27 \
    --size0=500 \
    --size1=700 \
    --size2=900
