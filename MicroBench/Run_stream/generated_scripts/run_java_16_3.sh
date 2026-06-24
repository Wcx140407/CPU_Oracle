#!/bin/bash
java -cp . ParallelStreamBenchmark -size 30000000 -nthreads 16 -ntimes 10
