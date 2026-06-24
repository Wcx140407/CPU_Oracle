#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 9 -ntimes 10
