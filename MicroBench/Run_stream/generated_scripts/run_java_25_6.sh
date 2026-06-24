#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 25 -ntimes 10
