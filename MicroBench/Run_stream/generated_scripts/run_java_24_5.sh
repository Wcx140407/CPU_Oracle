#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 24 -ntimes 10
