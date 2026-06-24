#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 15 -ntimes 10
