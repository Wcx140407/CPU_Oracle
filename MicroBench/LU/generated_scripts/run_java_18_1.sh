#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=18 \
    --size0=500
