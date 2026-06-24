#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 4 -ntimes 10
