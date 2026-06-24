#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 60 -ntimes 10
