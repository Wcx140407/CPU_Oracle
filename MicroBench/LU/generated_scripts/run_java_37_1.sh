#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=37 \
    --size0=500
