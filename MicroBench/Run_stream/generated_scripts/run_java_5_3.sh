#!/bin/bash
java -cp . ParallelStreamBenchmark -size 30000000 -nthreads 5 -ntimes 10
