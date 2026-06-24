#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 20 -ntimes 10
