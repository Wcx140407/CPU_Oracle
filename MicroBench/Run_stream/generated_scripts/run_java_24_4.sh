#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 24 -ntimes 10
