#!/bin/bash
java -cp . ParallelStreamBenchmark -size 70000000 -nthreads 64 -ntimes 10
