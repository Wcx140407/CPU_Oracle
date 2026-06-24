def generate_script(n):
    # 文件名
    filename = f"run_503.bwaves_r_ref-{n}.sh"
    
    # 脚本内容头部
    script_content = """#!/bin/bash
"""
    # 脚本内容主体
    script_content += f"export OMP_NUM_THREADS=1\n"
    script_content += f"export OMP_THREAD_LIMIT=1\n"
    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/bwaves_r bwaves_1 < bwaves_1.in > bwaves_1.out 2>> bwaves_1.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/bwaves_r bwaves_1 < bwaves_1.in > bwaves_1.out 2>> bwaves_1.err\n"
    
    script_content += f"wait\n"

    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/bwaves_r bwaves_2 < bwaves_2.in > bwaves_2.out 2>> bwaves_2.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/bwaves_r bwaves_2 < bwaves_2.in > bwaves_2.out 2>> bwaves_2.err\n"

    script_content += f"wait\n"

    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/bwaves_r bwaves_3 < bwaves_3.in > bwaves_3.out 2>> bwaves_3.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/bwaves_r bwaves_3 < bwaves_3.in > bwaves_3.out 2>> bwaves_3.err\n"

    script_content += f"wait\n"

    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/bwaves_r bwaves_4 < bwaves_4.in > bwaves_4.out 2>> bwaves_4.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/bwaves_r bwaves_4 < bwaves_4.in > bwaves_4.out 2>> bwaves_4.err\n"

    script_content += f"wait\n"

    # for i in range(1, n):
    #     script_content += f"../run_base_refrate_mytest-m64.0000/bwaves_r bwaves_4 < bwaves_4.in > bwaves_4.out 2>> bwaves_4.err &\n"
    # script_content += f"../run_base_refrate_mytest-m64.0000/bwaves_r bwaves_4 < bwaves_4.in > bwaves_4.out 2>> bwaves_4.err\n"

    # 将内容写入文件
    with open(filename, "w") as f:
        f.write(script_content)

    print(f"Script {filename} has been generated.")

# 输入需要的运行次数
n = int(input("Enter the number of repetitions (n): "))
generate_script(n)
