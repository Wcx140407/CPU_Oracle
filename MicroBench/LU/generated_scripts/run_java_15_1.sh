#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=15 \
    --size0=500
