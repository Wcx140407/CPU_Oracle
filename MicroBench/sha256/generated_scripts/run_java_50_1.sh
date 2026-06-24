#!/bin/bash
java -cp . ParallelSHA256 -size 10485760 -nthreads 50 -nreps 10
