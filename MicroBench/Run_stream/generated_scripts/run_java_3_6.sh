#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 3 -ntimes 10
