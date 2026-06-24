#!/bin/bash
java -cp . ParallelStreamBenchmark -size 30000000 -nthreads 2 -ntimes 10
