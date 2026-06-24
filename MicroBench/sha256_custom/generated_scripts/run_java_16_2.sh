#!/bin/bash
java -cp . CustomSHA256Parallel -size 20971520 -nthreads 16 -nreps 10
