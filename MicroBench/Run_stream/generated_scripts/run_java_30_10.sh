#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 30 -ntimes 10
