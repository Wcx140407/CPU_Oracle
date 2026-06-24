#!/bin/bash
java -cp . ParallelSHA256 -size 20971520 -nthreads 9 -nreps 10
