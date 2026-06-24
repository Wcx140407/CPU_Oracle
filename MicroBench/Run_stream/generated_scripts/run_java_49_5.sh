#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 49 -ntimes 10
