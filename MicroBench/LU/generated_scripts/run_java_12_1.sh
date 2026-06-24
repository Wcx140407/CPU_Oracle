#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=12 \
    --size0=500
