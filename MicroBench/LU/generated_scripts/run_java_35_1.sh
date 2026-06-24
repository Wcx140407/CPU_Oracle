#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=35 \
    --size0=500
