#!/bin/bash
java -cp . ParallelLU --datasets=2 --threads=21 \
    --size0=500 \
    --size1=700
