#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=34 \
    --size0=500
