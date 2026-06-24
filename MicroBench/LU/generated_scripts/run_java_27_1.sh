#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=27 \
    --size0=500
