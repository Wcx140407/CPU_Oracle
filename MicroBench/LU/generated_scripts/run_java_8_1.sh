#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=8 \
    --size0=500
