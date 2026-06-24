def generate_script(n):
    # 文件名
    filename = f"run_510.parest_r_ref-{n}.sh"
    
    # 脚本内容头部
    script_content = """#!/bin/bash
"""
    # 脚本内容主体
    script_content += f"export OMP_NUM_THREADS=1\n"
    script_content += f"export OMP_THREAD_LIMIT=1\n"
    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/parest_r ref.prm > ref.out 2>> ref.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/parest_r ref.prm > ref.out 2>> ref.err\n"
    
    script_content += f"wait\n"

    # 将内容写入文件
    with open(filename, "w") as f:
        f.write(script_content)

    print(f"Script {filename} has been generated.")

# 输入需要的运行次数
n = int(input("Enter the number of repetitions (n): "))
generate_script(n)
