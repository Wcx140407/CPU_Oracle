#!/bin/bash
java -cp . ParallelSHA256 -size 104857600 -nthreads 24 -nreps 10
