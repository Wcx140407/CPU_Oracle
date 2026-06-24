#!/bin/bash
java -cp . ParallelSHA256 -size 20971520 -nthreads 4 -nreps 10
