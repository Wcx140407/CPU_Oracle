#!/bin/bash
java -cp . ParallelSHA256 -size 104857600 -nthreads 4 -nreps 10
