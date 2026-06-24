#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=60 \
    --size0=500
