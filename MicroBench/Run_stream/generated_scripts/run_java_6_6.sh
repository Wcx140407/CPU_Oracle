#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 6 -ntimes 10
