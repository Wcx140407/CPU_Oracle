def generate_script(n):
    # 文件名
    filename = f"run_502.gcc_r_ref-{n}.sh"
    
    # 脚本内容头部
    script_content = """#!/bin/bash
"""
    # 脚本内容主体
    script_content += f"export OMP_NUM_THREADS=1\n"
    script_content += f"export OMP_THREAD_LIMIT=1\n"

    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r gcc-smaller.c -O5 -fipa-pta -o gcc-smaller.opts-O5_-fipa-pta.s > gcc-smaller.opts-O4_-fipa-pta.out 2>> gcc-smaller.opts-O3_-fipa-pta.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r gcc-smaller.c -O5 -fipa-pta -o gcc-smaller.opts-O5_-fipa-pta.s > gcc-smaller.opts-O3_-fipa-pta.out 2>> gcc-smaller.opts-O3_-fipa-pta.err\n"

    script_content += f"wait\n"
    
    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r t1.c -O4 -o t1.opts-O4.s > t1.opts-O3.out 2>> t1.opts-O3.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r t1.c -O4 -o t1.opts-O4.s > t1.opts-O3.out 2>> t1.opts-O3.err \n"

    script_content += f"wait\n"

    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r t1.c -O3 -o t1.opts-O3.s > t1.opts-O3.out 2>> t1.opts-O3.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r t1.c -O3 -o t1.opts-O3.s > t1.opts-O3.out 2>> t1.opts-O3.err \n"

    script_content += f"wait\n"

    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r scilab.c -O5 -o scilab.opts-O5.s > scilab.opts-O0.out 2>> scilab.opts-O0.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r scilab.c -O5 -o scilab.opts-O5.s > scilab.opts-O0.out 2>> scilab.opts-O0.err \n"

    script_content += f"wait\n"

    #for i in range(1, n):
    #    script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r gcc-pp.c -O3 -finline-limit=36000 -fpic -o gcc-pp.opts-O3_-finline-limit_36000_-fpic.s > gcc-pp.opts-O2_-finline-limit_36000_-fpic.out 2>> gcc-pp.opts-O2_-finline-limit_36000_-fpic.err &\n"
    #script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r gcc-pp.c -O3 -finline-limit=36000 -fpic -o gcc-pp.opts-O3_-finline-limit_36000_-fpic.s > gcc-pp.opts-O2_-finline-limit_36000_-fpic.out 2>> gcc-pp.opts-O2_-finline-limit_36000_-fpic.err\n"

    #script_content += f"wait\n"

    #for i in range(1, n):
    #    script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r train01.c -O0 -o train01.opts-O0.s > train01.opts-O3.out 2>> train01.opts-O3.err &\n"
    #script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r train01.c -O0 -o train01.opts-O0.s > train01.opts-O3.out 2>> train01.opts-O3.err \n"

    #script_content += f"wait\n"

    #for i in range(1, n):
    #    script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r 200.c -O3 -o 200.opts-O3.s > 200.opts-O2.out 2>> 200.opts-O2.err &\n"
    #script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r 200.c -O3 -o 200.opts-O3.s > 200.opts-O2.out 2>> 200.opts-O2.err \n"

    #script_content += f"wait\n"

    #for i in range(1, n):
    #    script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r ref32.c -O3 -fselective-scheduling -fselective-scheduling2 -o ref32.opts-O3_-fselective-scheduling_-fselective-scheduling2.s > ref32.opts-O4_-fselective-scheduling_-fselective-scheduling2.out 2>> ref32.opts-O4_-fselective-scheduling_-fselective-scheduling2.err &\n"
    #script_content += f"../run_base_refrate_mytest-m64.0000/cpugcc_r ref32.c -O3 -fselective-scheduling -fselective-scheduling2 -o ref32.opts-O3_-fselective-scheduling_-fselective-scheduling2.s > ref32.opts-O4_-fselective-scheduling_-fselective-scheduling2.out 2>> ref32.opts-O4_-fselective-scheduling_-fselective-scheduling2.err\n"

    #script_content += f"wait\n"

    # 将内容写入文件
    with open(filename, "w") as f:
        f.write(script_content)

    print(f"Script {filename} has been generated.")

# 输入需要的运行次数
n = int(input("Enter the number of repetitions (n): "))
generate_script(n)
