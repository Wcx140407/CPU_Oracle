#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=51 \
    --size0=500
