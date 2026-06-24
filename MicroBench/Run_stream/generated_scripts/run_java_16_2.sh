#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 16 -ntimes 10
