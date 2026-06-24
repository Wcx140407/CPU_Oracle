#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=16 \
    --size0=500
