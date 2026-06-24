#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=7 \
    --size0=500
