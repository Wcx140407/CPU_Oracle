#!/bin/bash
java -cp . ParallelStreamBenchmark -size 80000000 -nthreads 15 -ntimes 10
