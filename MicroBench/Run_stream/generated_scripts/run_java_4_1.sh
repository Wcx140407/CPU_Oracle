#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 4 -ntimes 10
