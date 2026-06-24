#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=63 \
    --size0=500
