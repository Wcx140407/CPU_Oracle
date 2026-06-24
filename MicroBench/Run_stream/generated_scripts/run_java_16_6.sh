#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 16 -ntimes 10
