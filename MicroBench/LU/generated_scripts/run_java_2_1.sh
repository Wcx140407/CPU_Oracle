#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=2 \
    --size0=500
