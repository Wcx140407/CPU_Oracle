#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=6 \
    --size0=500
