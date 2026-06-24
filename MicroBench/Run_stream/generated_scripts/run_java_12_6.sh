#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 12 -ntimes 10
