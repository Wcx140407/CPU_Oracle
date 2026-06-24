#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=4 \
    --size0=500
