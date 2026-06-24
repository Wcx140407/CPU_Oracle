#!/bin/bash
java -cp . ParallelStreamBenchmark -size 30000000 -nthreads 9 -ntimes 10
