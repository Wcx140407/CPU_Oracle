#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=45 \
    --size0=500
