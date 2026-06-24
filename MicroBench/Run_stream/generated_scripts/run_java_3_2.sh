#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 3 -ntimes 10
