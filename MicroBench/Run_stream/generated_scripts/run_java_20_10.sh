#!/bin/bash
java -cp . ParallelStreamBenchmark -size 100000000 -nthreads 20 -ntimes 10
