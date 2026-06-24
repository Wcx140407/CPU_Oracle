#!/bin/bash
java -cp . ParallelStreamBenchmark -size 50000000 -nthreads 15 -ntimes 10
