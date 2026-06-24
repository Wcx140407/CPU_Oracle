#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 24 -ntimes 10
