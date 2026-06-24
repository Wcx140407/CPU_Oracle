#!/bin/bash
java -cp . ParallelStreamBenchmark -size 30000000 -nthreads 15 -ntimes 10
