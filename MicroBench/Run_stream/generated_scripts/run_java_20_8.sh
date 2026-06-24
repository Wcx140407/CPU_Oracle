#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 20 -ntimes 10
