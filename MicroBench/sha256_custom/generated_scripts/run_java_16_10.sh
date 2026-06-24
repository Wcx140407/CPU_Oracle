#!/bin/bash
java -cp . CustomSHA256Parallel -size 104857600 -nthreads 16 -nreps 10
