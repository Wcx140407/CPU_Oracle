#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=36 \
    --size0=500
