#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=3 \
    --size0=500
