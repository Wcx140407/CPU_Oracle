#!/bin/bash
java -cp . ParallelStreamBenchmark -size 30000000 -nthreads 12 -ntimes 10
