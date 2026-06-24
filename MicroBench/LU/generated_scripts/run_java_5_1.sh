#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=5 \
    --size0=500
