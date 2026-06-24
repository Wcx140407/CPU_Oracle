#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=32 \
    --size0=500
