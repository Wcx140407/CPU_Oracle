#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=14 \
    --size0=500
