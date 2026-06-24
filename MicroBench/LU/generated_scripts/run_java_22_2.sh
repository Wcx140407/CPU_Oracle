#!/bin/bash
java -cp . ParallelLU --datasets=2 --threads=22 \
    --size0=500 \
    --size1=700
