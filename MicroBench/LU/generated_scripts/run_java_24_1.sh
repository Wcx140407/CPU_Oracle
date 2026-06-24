#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=24 \
    --size0=500
