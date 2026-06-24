#!/bin/bash
java -cp . ParallelLU --datasets=5 --threads=50 \
    --size0=500 \
    --size1=700 \
    --size2=900 \
    --size3=1100 \
    --size4=1300
