#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=40 \
    --size0=500
