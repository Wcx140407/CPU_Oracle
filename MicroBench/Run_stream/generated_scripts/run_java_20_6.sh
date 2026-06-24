#!/bin/bash
java -cp . ParallelStreamBenchmark -size 60000000 -nthreads 20 -ntimes 10
