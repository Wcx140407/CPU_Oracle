#!/bin/bash
java -cp . ParallelStreamBenchmark -size 10000000 -nthreads 8 -ntimes 10
