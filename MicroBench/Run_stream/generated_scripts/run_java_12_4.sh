#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 12 -ntimes 10
