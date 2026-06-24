#!/bin/bash
java -cp . CustomSHA256Parallel -size 20971520 -nthreads 4 -nreps 10
