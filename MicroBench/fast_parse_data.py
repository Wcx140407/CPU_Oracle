import os
import time
import concurrent.futures
import multiprocessing

# ---------------------------------------------------------
# 工作进程函数 (Worker)：必须在全局作用域中定义，以便跨进程传递
# ---------------------------------------------------------
def process_file_chunk(file_batch):
    """
    处理分配给当前核心的一批文件（Chunk）
    返回解析后的 6 组数据列表
    """
    C_time_lines, C_avg_lines = [],[]
    Java_time_lines, Java_avg_lines = [], []
    All_time_lines, All_avg_lines = [],[]
    
    count = 0
    for filepath, d, filename in file_batch:
        count += 1
        # 提取通配符部分，例如 "res_gcc_100_O3.csv" -> "gcc_100_O3"
        wildcard = filename[4:-4]
        index_name = f"{d}_{wildcard}"
        
        try:
            with open(filepath, 'r', encoding='utf-8') as f:
                # read().split('\n') 比 splitlines() 更快一点
                lines = f.read().split('\n')
            
            # 极速解析：跳过表头行(lines[1:])，如果行不为空，按逗号切分提取数字
            # 这行纯 C 底层的列表推导式是 Python 里最快的循环方式
            times =[float(line.split(',')[1]) for line in lines[1:] if line]
            
            if not times:
                continue
                
            # 1. 计算均值
            avg_time = sum(times) / len(times)
            
            # 2. 判断负载类别
            is_c = "clang" in filename or "gcc" in filename
            is_java = "java" in filename
            is_gcc_o3 = "gcc" in filename and filename.endswith("_O3.csv")
            is_clang_o3 = "clang" in filename and filename.endswith("_O3.csv")

            # 预先构建好字符串
            avg_line = f"{index_name},{avg_time}\n"
            time_lines = [f"{index_name},{t}\n" for t in times]
            
            # 3. 数据分发
            if is_c:
                C_avg_lines.append(avg_line)
                C_time_lines.extend(time_lines)
            elif is_java:
                Java_avg_lines.append(avg_line)
                Java_time_lines.extend(time_lines)
                
            if is_gcc_o3 or is_java:
            #if is_clang_o3 or is_java:
                All_avg_lines.append(avg_line)
                All_time_lines.extend(time_lines)
                
        except Exception:
            # 遇到脏文件自动跳过
            continue
            
    return (count, C_time_lines, C_avg_lines, Java_time_lines, Java_avg_lines, All_time_lines, All_avg_lines)

# ---------------------------------------------------------
# 主进程逻辑
# ---------------------------------------------------------
def main():
    start_time = time.time()
    
    directories =[
        "aes", "compress", "fio", "LU", "matmul", 
        "Run_stream", "sha256", "sha256_custom", "SOR", "sort_fixed"
    ]
    #directories =[
    #    "aes", "compress", "fio", "LU", "matmul",
    #    "Run_stream", "sha256"
    #]

    print("🚀 正在使用高速模式扫描文件目录...")
    
    # 1. 使用极速的 os.scandir 收集所有需要处理的文件路径
    all_tasks =[]
    for d in directories:
        target_dir = os.path.join(d, "results")
        if not os.path.exists(target_dir):
            continue
        
        # os.scandir 比 os.listdir 快得多
        with os.scandir(target_dir) as entries:
            for entry in entries:
                if entry.is_file() and entry.name.startswith("res_") and entry.name.endswith(".csv"):
                    all_tasks.append((entry.path, d, entry.name))
                    
    total_files = len(all_tasks)
    if total_files == 0:
        print("未找到任何 CSV 文件！")
        return

    # 2. 动态切分任务列表 (Chunking) 以平衡进程间通信开销
    # 将几万个文件切分成若干份（比如每份1000个），分发给不同 CPU 核心
    cpu_cores = multiprocessing.cpu_count()
    # 每个核心分派约 4 个任务包，防止部分核心提前闲置
    chunk_size = max(1, total_files // (cpu_cores * 4)) 
    chunks = [all_tasks[i:i + chunk_size] for i in range(0, total_files, chunk_size)]
    
    print(f"📊 发现 {total_files} 个文件。已启用 {cpu_cores} 核并发解析计算，请稍候...")

    # 用于汇总所有进程结果的列表
    final_C_time, final_C_avg = [],[]
    final_Java_time, final_Java_avg = [],[]
    final_All_time, final_All_avg = [],[]
    processed_count = 0

    # 3. 开启多进程池并发处理
    with concurrent.futures.ProcessPoolExecutor(max_workers=cpu_cores) as executor:
        # map() 会自动把 chunks 发送给 worker 并行处理
        for result in executor.map(process_file_chunk, chunks):
            count, C_t, C_a, J_t, J_a, A_t, A_a = result
            
            processed_count += count
            
            # 使用 extend 高速拼接结果
            final_C_time.extend(C_t)
            final_C_avg.extend(C_a)
            final_Java_time.extend(J_t)
            final_Java_avg.extend(J_a)
            final_All_time.extend(A_t)
            final_All_avg.extend(A_a)

    print(f"✅ 文件解析完毕。正在批量落盘保存聚合文件...")

    # 4. 批量极速写盘
    def fast_write(filename, lines):
        with open(filename, 'w', encoding='utf-8') as f:
            f.writelines(lines)

    fast_write("ht_off_C_time_data.csv", final_C_time)
    fast_write("ht_off_C_avg_data.csv", final_C_avg)
    
    fast_write("ht_off_Java_time_data.csv", final_Java_time)
    fast_write("ht_off_Java_avg_data.csv", final_Java_avg)
    
    fast_write("ht_off_all_time_data.csv", final_All_time)
    fast_write("ht_off_all_avg_data.csv", final_All_avg)

    end_time = time.time()
    print(f"🎉 全部处理完成！共处理 {processed_count} 个文件，总耗时: {end_time - start_time:.2f} 秒。")

# --- 注意：Windows 系统下使用多进程，必须放在 __main__ 保护块内 ---
if __name__ == '__main__':
    main()
