#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=64 \
    --size0=500
