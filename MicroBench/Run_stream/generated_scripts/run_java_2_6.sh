#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 2 -ntimes 10
