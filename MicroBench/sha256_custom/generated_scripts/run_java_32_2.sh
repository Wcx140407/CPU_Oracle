#!/bin/bash
java -cp . CustomSHA256Parallel -size 20971520 -nthreads 32 -nreps 10
