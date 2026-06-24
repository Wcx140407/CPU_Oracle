#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 4 -ntimes 10
