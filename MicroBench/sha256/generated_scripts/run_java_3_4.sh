#!/bin/bash
java -cp . ParallelSHA256 -size 41943040 -nthreads 3 -nreps 10
