#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 5 -ntimes 10
