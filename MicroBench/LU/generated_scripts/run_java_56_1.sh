#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=56 \
    --size0=500
