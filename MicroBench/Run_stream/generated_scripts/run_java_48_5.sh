#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 48 -ntimes 10
