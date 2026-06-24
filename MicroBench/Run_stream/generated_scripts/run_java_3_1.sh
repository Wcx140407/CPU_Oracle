#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 3 -ntimes 10
