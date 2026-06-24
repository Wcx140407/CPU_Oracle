#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 12 -ntimes 10
