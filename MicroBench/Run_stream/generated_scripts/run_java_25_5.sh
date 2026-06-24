#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 25 -ntimes 10
