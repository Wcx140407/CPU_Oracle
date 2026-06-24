#!/bin/bash
java -cp . CustomSHA256Parallel -size 41943040 -nthreads 64 -nreps 10
