#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 9 -ntimes 10
