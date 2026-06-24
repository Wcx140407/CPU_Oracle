#!/bin/bash
java -cp . ParallelSHA256 -size 10485760 -nthreads 40 -nreps 10
