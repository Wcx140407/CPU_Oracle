def generate_script(n):
    # 文件名
    filename = f"run_557.xz_r_ref-{n}.sh"
    
    # 脚本内容头部
    script_content = """#!/bin/bash
"""
    # 脚本内容主体
    script_content += f"export OMP_NUM_THREADS=1\n"
    script_content += f"export OMP_THREAD_LIMIT=1\n"
    
    #for i in range(1, n):
    #    script_content += f"../run_base_refrate_mytest-m64.0000/xz_r cld.tar.xz 311 19cf30ae51eddcbefda78dd06014b4b96281456e078ca7c13e1c0c9e6aaea8dff3efb4ad6b0456697718cede6bd5454852652806a657bb56e07d61128434b474 59796407 61004416 7 > cld.tar-160-6.out 2>> cld.tar-160-6.err &\n"
    #script_content += f"../run_base_refrate_mytest-m64.0000/xz_r cld.tar.xz 311 19cf30ae51eddcbefda78dd06014b4b96281456e078ca7c13e1c0c9e6aaea8dff3efb4ad6b0456697718cede6bd5454852652806a657bb56e07d61128434b474 59796407 61004416 7 > cld.tar-160-6.out 2>> cld.tar-160-6.err\n"

    #script_content += f"wait\n"

    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/xz_r cpu2006docs.tar.xz 279 055ce243071129412e9dd0b3b69a21654033a9b723d874b2015c774fac1553d9713be561ca86f74e4f16f22e664fc17a79f30caa5ad2c04fbc447549c2810fae 23047774 23513385 0e > cpu2006docs.tar-250-6e.out 2>> cpu2006docs.tar-250-6e.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/xz_r cpu2006docs.tar.xz 279 055ce243071129412e9dd0b3b69a21654033a9b723d874b2015c774fac1553d9713be561ca86f74e4f16f22e664fc17a79f30caa5ad2c04fbc447549c2810fae 23047774 23513385 0e > cpu2006docs.tar-250-6e.out 2>> cpu2006docs.tar-250-6e.err\n"

    script_content += f"wait\n"

    #for i in range(1, n):
    #    script_content += f"../run_base_refrate_mytest-m64.0000/xz_r input.combined.xz 611 a841f68f38572a49d86226b7ff5baeb31bd19dc637a922a972b2e6d1257a890f6a544ecab967c313e370478c74f760eb229d4eef8a8d2836d233d3e9dd1430bf 40401484 41217675 2e > input.combined-250-7.out 2>> input.combined-250-7.err &\n"
    #script_content += f"../run_base_refrate_mytest-m64.0000/xz_r input.combined.xz 611 a841f68f38572a49d86226b7ff5baeb31bd19dc637a922a972b2e6d1257a890f6a544ecab967c313e370478c74f760eb229d4eef8a8d2836d233d3e9dd1430bf 40401484 41217675 2e > input.combined-250-7.out 2>> input.combined-250-7.err\n"

    #script_content += f"wait\n"

    #for i in range(1, n):
    #    script_content += f"../run_base_refspeed_mytest-m64.0000/xz_s IMG_2560.cr2.xz 765 ec03e53b02deae89b6650f1de4bed76a012366fb3d4bdc791e8633d1a5964e03004523752ab008eff0d9e693689c53056533a05fc4b277f0086544c6c3cbbbf6 40822692 40824404 7 > IMG_2560.cr2.out  2>> IMG_2560.cr2.err &\n"
    #script_content += f"../run_base_refspeed_mytest-m64.0000/xz_s IMG_2560.cr2.xz 765 ec03e53b02deae89b6650f1de4bed76a012366fb3d4bdc791e8633d1a5964e03004523752ab008eff0d9e693689c53056533a05fc4b277f0086544c6c3cbbbf6 40822692 40824404 7 > IMG_2560.cr2.out  2>> IMG_2560.cr2.err\n"

    #script_content += f"wait\n"

    # 将内容写入文件
    with open(filename, "w") as f:
        f.write(script_content)

    print(f"Script {filename} has been generated.")

# 输入需要的运行次数
n = int(input("Enter the number of repetitions (n): "))
generate_script(n)
