# CPUOracle v1.0

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## Overview

**CPUOracle** is an extended dataset generation suite designed for CPU benchmarking and hardware/software Design Space Exploration (DSE). It enables users to flexibly customize datasets and concurrency (thread/process counts) for comprehensive performance evaluation.

The suite consists of two main components:
1. **SPEC CPU2017 Dataset Generators:** Tools to generate custom and extended datasets for all 43 workloads in the SPEC CPU2017 benchmark suite.
2. **MicroBench:** 10 highly customizable microbenchmarks (implemented in C and Java) with built-in dataset generators.

> **⚠️ IMPORTANT DISCLAIMER:** 
> This repository **ONLY** contains dataset generation tools. It **DOES NOT** include the source code or binaries for the SPEC CPU2017 benchmark suite. To use the SPEC generators, you must acquire the official SPEC CPU2017 suite from [SPEC's official website](https://www.spec.org/cpu2017/).

---

## Directory Structure & Features

### 1. `MicroBench/`
A collection of 10 microbenchmarks supporting flexible dataset and thread count customization.

*   `*.cpp` / `*.java`: Source code for the microbenchmarks.
*   `run.sh`: Reference script for compiling and running C workloads.
*   `generate_scripts.sh`: Batch generator for C workloads (customize datasets and thread counts).
*   `generate_java_scripts.sh`: Batch generator for Java workloads.
*   `generated_scripts/`: Default output directory for generated run scripts.
*   `batch_run.sh`: Bash script to execute all microbenchmarks sequentially.
*   `fast_parse_data.py`: Python script to parse and aggregate the execution results.

**Usage:** Modify parameters inside `generate_scripts.sh` and `generate_java_scripts.sh` to generate customized execution scripts, then run them.

### 2. `SPECCPU2017_dataset/`
Contains dataset generation tools for all 43 SPEC CPU2017 workloads (Int/FP, Rate/Speed). Each directory corresponds to a specific workload. Official workload details can be found in the [SPEC CPU2017 Docs](https://www.spec.org/cpu2017/Docs/benchmarks/).

Below is the configuration guide for customizing the datasets for each workload group:

#### Integer & Floating Point Workloads
*   **500.perlbench_r / 600.perlbench_s:** 
    *   Run `generate-n.py`. Modify `number of messages to generate` via runtime arguments.
*   **502.gcc_r / 602.gcc_s:**
    *   Run `generate-n.py`. Accepts any successfully compilable `*.c` file as input. Compilation flags can be modified.
*   **503.bwaves_r / 603.bwaves_s:**
    *   Run `generate-n.py`. Modify grid size parameters (`nx`, `ny`, `nz`) in the `*.in` file.
*   **505.mcf_r / 605.mcf_s:**
    *   Run `505_generate_dataset.py`. Inputs: `timetabled_trip` and `deadhead_trip`. 
    *   *Constraint:* `deadhead_trip` must be `<= timetabled_trip * (timetabled_trip - 1) / 2`.
    *   Run `generate-n.py` to use `custom_inp*.in` as custom datasets.
*   **507.cactuBSSN_r / 607.cactuBSSN_s:**
    *   Run `generate-n.py`. Modify 3D grid size (`PUGH::local_nsize`) and iterations (`Cactus::cctk_itlast`) in the `*.par` file.
*   **508.namd_r:**
    *   Run `generate-n.py`. Modify the `iterations` parameter.
*   **510.parest_r:**
    *   Run `generate-n.py`. Modify `Number of experiments` and `Maximal number of iterations` in the `*.prm` file.
*   **511.povray_r:**
    *   Run `generate-n.py`. Modify `Width` and `Height` parameters in the `*.ini` file.
*   **519.lbm_r / 619.lbm_s:**
    *   Run `generate-n.py`. Modify dataset size via runtime arguments (recommend changing `time steps`).
*   **520.omnetpp_r / 620.omnetpp_s:**
    *   Run `generate-n.py`. Modify the `sim-time-limit` parameter in the `*.ini` file.
*   **521.wrf_r / 621.wrf_s:**
    *   Run `generate-n.py`. Modify `run_days`, `run_hours`, `run_minites`, `run_seconds` in `*.input` (Note: Time range is currently limited to <= 1 day).
*   **523.xalancbmk_r / 623.xalancbmk_s:**
    *   Run `523_generate_dataset.py` (Input: Number of child nodes under the root node).
    *   Run `generate-n.py` to select `dataset_*.xml` and `dataset_*.xsl` files.
*   **525.x264_r / 625.x264_s:**
    *   Run `generate-n.py`. Modify dataset size via runtime arguments (recommend changing `seek` and `frames`).
*   **526.blender_r:**
    *   Run `generate-n.py`. Modify dataset size via runtime arguments (recommend changing `simulation` and `emulation`).
*   **527.cam4_r / 627.cam4_s:**
    *   Run `generate-n.py`. Modify `nhtfrq` in `atm_in` and `stop_n` in `drv_in`.
*   **628.pop2_s:**
    *   Modify `stop_n` and `restart_n` in `drv_in.in`, and `dt_count` in `pop2_in`.
*   **531.deepsjeng_r / 631.deepsjeng_s:**
    *   Run `531_generate_dataset.py`. Inputs: `poisons`, minimum/maximum number of pieces to generate.
    *   Run `generate-n.py` to select the generated `*.txt` file.
*   **538.imagick_r / 638.imagick_s:**
    *   Run `538_generate_dataset.py`. Input: Image resolution (`width * height`).
    *   Run `generate-n.py` to select `*.tga` files.
*   **541.leela_r / 641.leela_s:**
    *   Run `541_generate_dataset.py`. Inputs: Number of games, board size, min/max moves per game.
    *   Run `generate-n.py` to select `*.sgf` files.
*   **544.nab_r / 644.nab_s:**
    *   Includes custom molecule models sourced from [RCSB](https://www.rcsb.org/).
    *   Run `generate-n.py`. Select models and random seeds via runtime arguments.
*   **548.exchange2_r / 648.exchange2_s:**
    *   Run `generate-n.py`. Modify dataset size via runtime arguments.
*   **549.fotonik3d_r / 649.fotonik3d_s:**
    *   Run `generate-n.py`. Modify `N_x`, `N_y`, `N_z`, `N_t`, and `OBC` parameters in the `yee.dat` file.
*   **554.roms_r / 654.roms_s:**
    *   Run `generate-n.py`. Modify `Lm`, `Mm`, `N`, and `NTIMES` in `*.in.x` files.
    *   > **⚠️ CRITICAL WARNING FOR 654.roms_s:** Ensure `NtileI * NtileJ` is multiple of the number of threads. For stability, `Lm` should be a multiple of `NtileI`, and `Mm` should be a multiple of `NtileJ`. Incorrect settings will cause runtime errors.
*   **557.xz_r / 657.xz_s:**
    *   Run `generate-n.py`. Modify dataset compression levels and cache size via runtime arguments.

---

## Dataset

Due to storage limitations, this repository only provides the generation tool scripts. The complete dataset is available for download at: []()

## Citation & Reference

If you use CPUOracle in your research, please refer to our experiment and cite our paper:

*   **arXiv Link:** [https://arxiv.org/abs/2605.26643](https://arxiv.org/abs/2605.26643)

```bibtex
@article{wang2026attributing,
  title={Attributing the System's Overall Effect to its Components},
  author={Wang, Chenxi and Wang, Lei and Gao, Wanling and Fan, Fanda and Kang, Guoxin and Li, Hongxiao and Su, Yuchen and Zhan, Jianfeng},
  journal={arXiv preprint arXiv:2605.26643},
  year={2026}
}