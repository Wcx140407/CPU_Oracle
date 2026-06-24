#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=33 \
    --size0=500
