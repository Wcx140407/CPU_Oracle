#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=42 \
    --size0=500
