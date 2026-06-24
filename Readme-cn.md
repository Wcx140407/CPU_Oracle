#CPUOracle v1.0
#基本描述
基准测试套件的扩展数据集工具，绝大部分负载支持用户自定义数据集
包括SPEC CPU2017基准所有43个负载的扩展数据集生成工具以及10个支持用户自定义数据集的微基准
SPEC CPU2017基准包括扩展数据集（含官方数据集），以及对应负载测试生成脚本用于定制同时测试的副本数（进程数），部分SPEC负载提供数据集生成工具以支持用户自定义数据集
10个微基准负载包括C实现和Java实现，提供10个数据集，可基于此灵活定制自己的数据集

#MicroBench
支持灵活定制数据集以及运行线程数的10个微基准负载（包括C实现和Java实现）
batch_run.sh #批量运行所有微基准bash脚本
fast_parse_data.py #批量统计所有微基准运行结果Python脚本
剩余目录为微基准负载，目录内
*.cpp #C负载源码
*.java #Java负载源码
run.sh #C负载可执行文件参考运行脚本（包含编译.cpp文件参考编译指令）
generate_scripts.sh #用户定制化批量生成C负载运行脚本的脚本
generate_java_scripts.sh #用户定制化批量生成Java负载运行脚本的脚本
generated_scripts #生成的C负载和Java负载运行脚本默认存储目录
基于本基准提供的数据集修改generate_scripts.sh和generate_java_scripts.sh脚本可以实现用户定制化数据集以及线程数测试

#SPECCPU2017_dataset
43个SPEC CPU2017负载扩展数据集工具（仅包含扩展数据集生成工具，SPEC CPU2017基准请从SPEC官网：https://www.spec.org/cpu2017 获取）
500 #500.perlbench_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/500.perlbench_r.html
generate-n.py #定制化数据集生成脚本，通过运行时参数修改数据集大小，建议修改number of messages to generate参数
600 #600.perlbench_s负载生成工具，具体生成参考500

502 #502.gcc_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/502.gcc_r.html
generate-n.py #定制化数据集生成脚本，任意一个可正常编译的*.c文件都可以是输入数据集，同时可以修改编译参数
602 #602.gcc_s负载生成工具，具体生成参考502

503 #503.bwaves_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/503.bwaves_r.html
generate-n.py #定制化数据集生成脚本，修改*.in文件的nx,ny,nz可以更改grid size
603 #603.bwaves_s负载生成工具，具体生成参考503

505 #505.mcf_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/505.mcf_r.html
505_generate_dataset.py #mcf负载定制化数据集生成脚本，输入参数为timetabled_trip number以及deadhead_trip number，请注意deadhead_trip number需小于timetabled_trip number*（timetabled_trip number - 1）/ 2
generate-n.py #定制化数据集生成脚本，custom_inp*.in是定制化用户数据集
605 #603.mcf_s负载生成工具，具体生成参考505

507 #507.cactuBSSN_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/507.cactuBSSN_r.html
generate-n.py #定制化数据集生成脚本，修改*.par文件的PUGH::local_nsize参数修改场的三维size，Cactus::cctk_itlast参数修改迭代次数
607 #607.cactuBSSN_s负载生成工具，具体生成参考507

508 #508.namd_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/508.namd_r.html
generate-n.py #定制化数据集生成脚本，修改iterations参数的迭代次数

510 #510.parest_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/510.parest_r.html
generate-n.py #定制化数据集生成脚本，修改*.prm文件的Number of experiments参数以及Maximal number of iterations参数

511 #511.povray_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/511.povray_r.html
generate-n.py #定制化数据集生成脚本，修改*.ini文件的Width参数以及Height参数

519 #519.lbm_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/519.lbm_r.html
generate-n.py #定制化数据集生成脚本，通过运行时参数修改数据集大小，建议修改time steps参数
619 #619.lbm_s负载生成工具，具体生成参考519

520 #520.omnetpp_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/520.omnetpp_r.html
generate-n.py #定制化数据集生成脚本，修改*.ini文件的sim-time-limit参数
620 #620.omnetpp_s负载生成工具，具体生成参考520

521 #521.wrf_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/521.wrf_r.html
generate-n.py #定制化数据集生成脚本，修改*.input文件的run_days, run_hours, run_minites, run_seconds参数，请注意参数范围暂时只能限定在一天内
621 #621.wrf_s负载生成工具，具体生成参考521

523 #523.xalancbmk_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/523.xalancbmk_r.html
523_generate_dataset.py #xalancbmk负载定制化数据集生成脚本，输入参数为根节点下的子节点数量
generate-n.py #定制化数据集生成脚本，选择dataset_*.xml文件和dataset_*.xsl文件
623 #623.xalancbmk_s负载生成工具，具体生成参考523

525 #525.x264_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/525.x264_r.html
generate-n.py #定制化数据集生成脚本，通过运行时参数修改数据集大小，建议修改seek参数和frames参数
625 #625.x264_s负载生成工具，具体生成参考525

526 #526.blender_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/526.blender_r.html
generate-n.py #定制化数据集生成脚本，通过运行时参数修改数据集大小，建议修改simulation参数和emulation参数

527 #527.cam4_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/527.cam4_r.html
generate-n.py #定制化数据集生成脚本，修改atm_in文件的nhtfrq参数，drv_in的stop_n参数
627 #627.cam4_s负载生成工具，具体生成参考527

628 #628.pop2_s负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/628.pop2_s.html
修改drv_in.in文件的stop_n参数以及restart_n参数，pop2_in文件的dt_count参数

531 #531.deepsjeng_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/531.deepsjeng_r.html
531_generate_dataset.py #deepsjeng负载定制化数据集生成脚本，输入参数为poisons以及最少生成棋子数与最大生成棋子数
generate-n.py #定制化数据集生成脚本，选择*.txt文件
631 #631.deepsjeng_s负载生成工具，具体生成参考531

538 #538.imagick_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/538.imagick_r.html
538_generate_dataset.py #imagick负载定制化数据集生成脚本，输入参数为图片分辨率：width*height
generate-n.py #定制化数据集生成脚本，选择*.tga文件
638 #638.imagick_s负载生成工具，具体生成参考538

541 #541.leela_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/541.leela_r.html
541_generate_dataset.py #leela负载定制化数据集生成脚本，输入参数为棋局数、棋盘尺寸、每局棋最少落子数以及最大落子数
generate-n.py #定制化数据集生成脚本，选择*.sgf文件
641 #641.leela_s负载生成工具，具体生成参考541

544 #544.nab_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/544.nab_r.html
目录下所有文件夹为类似SPEC官方数据集从https://www.rcsb.org网站定制的分子模型
generate-n.py #定制化数据集生成脚本，通过运行时参数选择数据集，可以选择不同分子模型以及随机种子
644 #644.nab_s负载生成工具，具体生成参考544

548 #548.exchange2_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/548.exchange2_r.html
generate-n.py #定制化数据集生成脚本，通过运行时参数修改数据集大小
648 #648.exchange2_s负载生成工具，具体生成参考548

549 #549.fotonik3d_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/549.fotonik3d_r.html
generate-n.py #定制化数据集生成脚本，修改yee.dat文件的N_x，N_y，N_z，N_t，OBC参数 
649 #649.fotonik3d_s负载生成工具，具体生成参考549

554 #554.roms_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/554.roms_r.html
generate-n.py #定制化数据集生成脚本，修改*.in.x文件的Lm, Mm, N, NTIMES参数 
654 #654.roms_s负载生成工具，具体生成参考554。请注意NtileI*NtileJ参数值为线程数倍数且尽可能让Lm参数是NtileI参数的倍数，Mm参数是NtileJ参数的倍数；参数未合理设置会导致运行出错！

557 #557.xz_r负载数据集生成工具
负载/数据集描述参考SPEC官网：https://www.spec.org/cpu2017/Docs/benchmarks/557.xz_r.html
generate-n.py #定制化数据集生成脚本，通过运行时参数修改数据集，修改压缩级别和缓存大小
657 #657.xz_s负载生成工具，具体生成参考557

#基本使用方法为用户使用本数据集生成工具自定义不同类型的数据集，用于软硬件配置空间探索
可以参考我们arxiv论文的实验:https://arxiv.org/abs/2605.26643
