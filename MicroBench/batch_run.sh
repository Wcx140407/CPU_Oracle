#!/bin/bash

# 创建结果目录
mkdir -p aes/results
mkdir -p compress/results
mkdir -p fio/results
mkdir -p LU/results
mkdir -p matmul/results
mkdir -p Run_stream/results
mkdir -p sha256/results 
mkdir -p sha256_custom/results 
mkdir -p SOR/results
mkdir -p sort_fixed/results

# 定义函数：执行单个脚本3次并记录时间（高精度版）
execute_script_precise() {
    local script_dir="$1"
    local script_name="$2"
    #local script_path="$script_dir/$script_name"
    local script_path="$script_name"
    local results_dir="../results"
    #local results_dir="$script_dir/../results"
    
    # 从脚本名生成结果文件名（将 run_ 替换为 res_）
    local result_name="${script_name/run_/res_}"
    local result_name="${result_name%.sh}.csv"
    local result_path="$results_dir/$result_name"
    
    # 检查结果文件是否已存在
    if [ -f "$result_path" ]; then
        echo "结果文件已存在，跳过执行: $result_path"
        echo "----------------------------------------"
        return 0
    fi

    echo "执行脚本: $script_path"
    echo "结果文件: $result_path"
    
    # 清空或创建结果文件
    echo "execution,time_seconds" > "$result_path"
   
    # 执行3次，记录每次的时间
    for i in {1..3}; do
        echo "=== 第 $i 次执行 ==="
        
        # 使用date命令获取高精度时间
        start_time=$(date +%s.%N)
        bash "$script_path" > /dev/null 2>&1
        end_time=$(date +%s.%N)
        
        # 计算执行时间（秒，带小数）
        exec_time=$(echo "$end_time - $start_time" | bc)
        
        # 将时间写入CSV文件
        echo "$i,$exec_time" >> "$result_path"
        echo "第 $i 次执行完成，耗时: ${exec_time}秒"
    done
    
    echo "脚本 $script_name 执行完成，结果保存到 $result_name"
    echo 3 > /proc/sys/vm/drop_caches
    echo "----------------------------------------"
}

# 主执行逻辑
for dir in aes compress fio LU matmul Run_stream sha256 sha256_custom SOR sort_fixed; do
    echo "=== 处理目录: $dir ==="
    
    scripts_dir="$dir/generated_scripts"
    
    if [ ! -d "$scripts_dir" ]; then
        echo "警告: 目录 $scripts_dir 不存在，跳过"
        continue
    fi
    
    # 获取所有run_*.sh脚本，并按名称排序
    scripts=$(find "$scripts_dir" -name "run_*.sh" | sort)
    
    if [ -z "$scripts" ]; then
        echo "警告: 在 $scripts_dir 中没有找到 run_*.sh 脚本"
        continue
    fi
    
    cd $scripts_dir
    # 串行执行每个脚本
    for script in $scripts; do
	script_name=$(basename "$script")
        execute_script_precise "$scripts_dir" "$script_name"
    done
    cd ../../
done

echo "=== 所有脚本执行完成 ==="
