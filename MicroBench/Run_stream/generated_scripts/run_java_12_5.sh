#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 12 -ntimes 10
