#!/bin/bash
java -cp . ParallelLU --datasets=3 --threads=53 \
    --size0=500 \
    --size1=700 \
    --size2=900
