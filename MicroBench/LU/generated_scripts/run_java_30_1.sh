#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=30 \
    --size0=500
