#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=9 \
    --size0=500
