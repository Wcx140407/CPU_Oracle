#!/bin/bash
java -cp . ParallelSHA256 -size 20971520 -nthreads 30 -nreps 10
