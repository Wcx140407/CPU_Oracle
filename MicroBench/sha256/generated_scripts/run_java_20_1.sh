#!/bin/bash
java -cp . ParallelSHA256 -size 10485760 -nthreads 20 -nreps 10
