#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=13 \
    --size0=500
