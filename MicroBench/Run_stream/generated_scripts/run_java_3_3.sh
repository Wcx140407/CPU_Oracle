#!/bin/bash
java -cp . ParallelStreamBenchmark -size 30000000 -nthreads 3 -ntimes 10
