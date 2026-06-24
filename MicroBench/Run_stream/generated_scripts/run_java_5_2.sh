#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 5 -ntimes 10
