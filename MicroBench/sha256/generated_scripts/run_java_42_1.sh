#!/bin/bash
java -cp . ParallelSHA256 -size 10485760 -nthreads 42 -nreps 10
