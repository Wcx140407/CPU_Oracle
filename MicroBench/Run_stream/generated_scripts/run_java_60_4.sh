#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 60 -ntimes 10
