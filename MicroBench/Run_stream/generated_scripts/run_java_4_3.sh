#!/bin/bash
java -cp . ParallelStreamBenchmark -size 30000000 -nthreads 4 -ntimes 10
