def generate_script(n):
    # 文件名
    filename = f"run_538.imagick_r_ref-{n}.sh"
    
    # 脚本内容头部
    script_content = """#!/bin/bash
"""
    # 脚本内容主体
    script_content += f"export OMP_NUM_THREADS=1\n"
    script_content += f"export OMP_THREAD_LIMIT=1\n"
    for i in range(1, n):
        script_content += f"../run_base_refrate_mytest-m64.0000/imagick_r -limit disk 0 input_image_32.tga -edge 41 -resample 181% -emboss 31 -colorspace YUV -mean-shift 19x19+15% -resize 30% output32.tga > refrate_convert.out 2>> refrate_convert.err &\n"
    script_content += f"../run_base_refrate_mytest-m64.0000/imagick_r -limit disk 0 input_image_32.tga -edge 41 -resample 181% -emboss 31 -colorspace YUV -mean-shift 19x19+15% -resize 30% output32.tga > refrate_convert.out 2>> refrate_convert.err\n"
   
    script_content += f"wait\n"

    #for i in range(1, n):
    #    script_content += f"../run_base_refrate_mytest-m64.0000/x264_r --pass 2 --stats x264_stats.log --bitrate 1000 --dumpyuv 200 --frames 1000 -o BuckBunny_New.264 BuckBunny.yuv 1280x720 > run_000-1000_x264_r_base.mytest-m64_x264_pass2.out 2>> run_000-1000_x264_r_base.mytest-m64_x264_pass2.err &\n"
    #script_content += f"../run_base_refrate_mytest-m64.0000/x264_r --pass 2 --stats x264_stats.log --bitrate 1000 --dumpyuv 200 --frames 1000 -o BuckBunny_New.264 BuckBunny.yuv 1280x720 > run_000-1000_x264_r_base.mytest-m64_x264_pass2.out 2>> run_000-1000_x264_r_base.mytest-m64_x264_pass2.err\n"

    #for i in range(1, n):
    #    script_content += f"../run_base_refrate_mytest-m64.0000/x264_r --seek 500 --dumpyuv 200 --frames 1250 -o BuckBunny_New.264 BuckBunny.yuv 1280x720 > run_0500-1250_x264_r_base.mytest-m64_x264.out 2>> run_0500-1250_x264_r_base.mytest-m64_x264.err &\n"
    #script_content += f"../run_base_refrate_mytest-m64.0000/x264_r --seek 500 --dumpyuv 200 --frames 1250 -o BuckBunny_New.264 BuckBunny.yuv 1280x720 > run_0500-1250_x264_r_base.mytest-m64_x264.out 2>> run_0500-1250_x264_r_base.mytest-m64_x264.err\n"

    # for i in range(1, n):
    #     script_content += f"../run_base_refrate_mytest-m64.0000/x264_r --pass 2 --stats x264_stats.log --bitrate 1000 --dumpyuv 200 --frames 1000 -o BuckBunny_New.264 BuckBunny.yuv 1280x720 > run_000-1000_x264_r_base.mytest-m64_x264_pass2.out 2>> run_000-1000_x264_r_base.mytest-m64_x264_pass2.err &\n"
    # script_content += f"../run_base_refrate_mytest-m64.0000/x264_r --pass 2 --stats x264_stats.log --bitrate 1000 --dumpyuv 200 --frames 1000 -o BuckBunny_New.264 BuckBunny.yuv 1280x720 > run_000-1000_x264_r_base.mytest-m64_x264_pass2.out 2>> run_000-1000_x264_r_base.mytest-m64_x264_pass2.err\n"

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
