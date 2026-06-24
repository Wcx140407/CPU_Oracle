#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=48 \
    --size0=500
