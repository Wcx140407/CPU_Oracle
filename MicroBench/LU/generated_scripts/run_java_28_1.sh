#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=28 \
    --size0=500
