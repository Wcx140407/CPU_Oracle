#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=54 \
    --size0=500
