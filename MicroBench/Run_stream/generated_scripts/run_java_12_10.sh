#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 12 -ntimes 10
