#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 7 -ntimes 10
