#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=31 \
    --size0=500
