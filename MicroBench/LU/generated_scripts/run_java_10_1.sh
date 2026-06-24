#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=10 \
    --size0=500
