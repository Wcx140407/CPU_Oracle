#!/bin/bash
java -cp . ParallelStreamBenchmark -size 40000000 -nthreads 25 -ntimes 10
