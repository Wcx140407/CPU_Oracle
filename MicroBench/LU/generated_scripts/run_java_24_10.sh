#!/bin/bash
java -cp . ParallelLU --datasets=10 --threads=24 \
    --size0=500 \
    --size1=700 \
    --size2=900 \
    --size3=1100 \
    --size4=1300 \
    --size5=1500 \
    --size6=1700 \
    --size7=1900 \
    --size8=2100 \
    --size9=2300
