#!/bin/bash
java -cp . ParallelStreamBenchmark -size 20000000 -nthreads 15 -ntimes 10
