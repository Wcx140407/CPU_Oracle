#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 48 -ntimes 10
