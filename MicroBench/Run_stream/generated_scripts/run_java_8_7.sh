#!/bin/bash
java -cp . ParallelStreamBenchmark -size 70000000 -nthreads 8 -ntimes 10
