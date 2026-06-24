#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=11 \
    --size0=500
