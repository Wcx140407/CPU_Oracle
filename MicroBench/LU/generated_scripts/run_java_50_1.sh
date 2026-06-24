#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=50 \
    --size0=500
