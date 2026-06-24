import os
import random

def generate_dataset(timetabled_trips, deadhead_trips, max_time=1440, max_cost=1000, output_dir="505_datasets"):
    """
    生成自定义数据集用于 SPEC CPU2017 的 505.mcf_r 测试负载。
    
    参数:
    - timetabled_trips: 时刻表行程数
    - deadhead_trips: 非时刻表行程数
    - max_time: 最大时间范围（单位：分钟），默认一天 1440 分钟。
    - max_cost: 最大成本（用于非时刻表行程），默认 1000。
    - output_file: 输出文件名。
    """
    os.makedirs(output_dir, exist_ok=True)
    output_file = os.path.join(output_dir, f"custom_inp.in")
    max_possible_deadhead_trips = timetabled_trips * (timetabled_trips - 1) // 2
    if deadhead_trips > max_possible_deadhead_trips:
        raise ValueError(f"deadhead_trips ({deadhead_trips}) 超过最大可能值 ({max_possible_deadhead_trips})。")
    
    with open(output_file, "w") as f:
        # 写入第一行
        f.write(f"{timetabled_trips} {deadhead_trips}\n")
        
        # 生成时刻表行程
        for _ in range(timetabled_trips):
            start_time = random.randint(1, max_time - 1)
            end_time = random.randint(start_time + 1, max_time)
            f.write(f"{start_time} {end_time}\n")
        
        # # 创建所有可能的 (start_trip, end_trip) 对，并随机抽取所需数量
        # possible_trips = [(i, j) for i in range(1, timetabled_trips) for j in range(i + 1, timetabled_trips + 1)]
        # selected_trips = random.sample(possible_trips, deadhead_trips)
        
        # for start_trip, end_trip in selected_trips:
        #     cost = random.randint(1, max_cost)
        #     f.write(f"{start_trip} {end_trip} {cost}\n")
        generated_trips = set()  # 用于存储已生成的 (start_trip, end_trip) 对
        while len(generated_trips) < deadhead_trips:
            start_trip = random.randint(1, timetabled_trips - 1)
            end_trip = random.randint(start_trip + 1, timetabled_trips)
            if (start_trip, end_trip) not in generated_trips:
                generated_trips.add((start_trip, end_trip))
                cost = random.randint(1, max_cost)
                f.write(f"{start_trip} {end_trip} {cost}\n")
    
    print(f"数据集已生成并保存到 {output_file}")

# 示例用法
if __name__ == "__main__":
    # 定义时刻表和非时刻表行程数量
    timetabled_trips = int(input("Enter the timetabled trips to generate: ").strip())
    deadhead_trips = int(input("Enter the deadhead trips to generate: ").strip())
    max_t = int(input("Enter the max time to generate: ").strip())
    max_c = int(input("Enter the max cost to generate: ").strip())
    
    # 调用生成器
    generate_dataset(timetabled_trips, deadhead_trips, max_time=max_t, max_cost=max_c, output_dir="505_datasets")

