#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 24 -ntimes 10
