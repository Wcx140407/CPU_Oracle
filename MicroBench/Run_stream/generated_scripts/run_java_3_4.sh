#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 3 -ntimes 10
