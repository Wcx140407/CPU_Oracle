#!/bin/bash
java -cp . CustomSHA256Parallel -size 20971520 -nthreads 40 -nreps 10
