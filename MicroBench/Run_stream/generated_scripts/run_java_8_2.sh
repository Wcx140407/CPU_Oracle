#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 8 -ntimes 10
