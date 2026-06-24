#!/bin/bash
java -cp . ParallelSHA256 -size 20971520 -nthreads 63 -nreps 10
