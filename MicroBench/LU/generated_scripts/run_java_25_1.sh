#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=25 \
    --size0=500
