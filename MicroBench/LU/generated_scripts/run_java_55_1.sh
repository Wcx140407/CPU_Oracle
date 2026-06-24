#!/bin/bash
java -cp . ParallelLU --datasets=1 --threads=55 \
    --size0=500
