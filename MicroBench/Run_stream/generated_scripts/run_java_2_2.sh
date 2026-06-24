#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 2 -ntimes 10
