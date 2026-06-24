#!/bin/bash
java -cp . ParallelSHA256 -size 10485760 -nthreads 60 -nreps 10
