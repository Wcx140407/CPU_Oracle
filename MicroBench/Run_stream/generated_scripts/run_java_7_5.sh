#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 7 -ntimes 10
