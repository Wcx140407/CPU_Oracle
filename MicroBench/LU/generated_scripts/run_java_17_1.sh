#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=17 \
    --size0=500
