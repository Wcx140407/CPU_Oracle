#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 9 -ntimes 10
