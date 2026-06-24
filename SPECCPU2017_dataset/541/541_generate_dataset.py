import os
import random
import string

def generate_random_sgf(output_dir, num_games, board_size, min_moves, max_moves):
    """
    生成随机的 .sgf 文件
    :param output_dir: 输出目录
    :param num_games: 生成的对局数量
    :param board_size: 棋盘大小（如 9, 13, 19）
    :param min_moves: 每局的最少步数
    :param max_moves: 每局的最多步数
    """
    os.makedirs(output_dir, exist_ok=True)
    
    sgf_file = os.path.join(output_dir, f"game.sgf")
    with open(sgf_file, "w") as f:
            # 写入 SGF 文件头部
        for game_id in range(1, num_games + 1):
            # 随机棋手段位
            black_rank = f"{random.randint(1, 9)}d"  # 黑方段位
            white_rank = f"{random.randint(1, 9)}d"  # 白方段位

            # 随机让子数（0 到 9）
            handicap = 0
            # handicap = random.randint(0, 9)

            # 随机贴目值（从常见贴目范围中选择）
            komi = random.choice([0.0, 6.5, 7.5])

            # 写入头部信息
            f.write("(;\n")
            f.write(f"SZ[{board_size}]\n")  # 棋盘大小
            f.write(f"HA[{handicap}]\n")  # 让子数
            f.write("ST[0]\n")  # 状态
            f.write(f"PB[Player Black {game_id}]\n")  # 黑方
            f.write(f"PW[Player White {game_id}]\n")  # 白方
            f.write(f"KM[{komi}]\n")  # 贴目
            f.write(f"RE[{'B+R' if random.random() < 0.5 else 'W+R'}]\n")  # 胜负结果
            f.write(f"BR[{black_rank}]\n")  # 黑方段位
            f.write(f"WR[{white_rank}]\n")  # 白方段位
            f.write(f"C[Player White {game_id} VS Player Black {game_id}]\n")  # 注释

            # 如果有让子，生成让子点
            handicap_points = set()
            if handicap > 0:
                for i in range(handicap):
                    while True:
                        # 生成固定的让子位置（通常为棋盘边角附近）
                        col = random.choice(["d", "q", "k"])
                        row = random.choice(["d", "q", "k"])
                        point = f"{col}{row}"
                        if point not in handicap_points:  # 确保让子点不重复
                            handicap_points.add(point)
                            f.write(f";AB[{point}]\n")  # `AB` 表示让子点
                            break

            # 生成落子记录
            num_moves = random.randint(min_moves, max_moves)
            # moves = set(handicap_points)  # 初始化为让子点，确保让子点不能再落子
            moves = set()
            players = ["B", "W"]

            for move_num in range(num_moves):
                player = players[move_num % 2]
                while True:
                    col = random.choice(string.ascii_lowercase[:board_size])
                    row = random.choice(string.ascii_lowercase[:board_size])
                    move = f"{col}{row}"
                    if move not in moves:  # 检查是否冲突
                        moves.add(move)
                        f.write(f";{player}[{move}]\n")
                        break
            
            # 结束 SGF 文件
            f.write(")\n")
        
    print(f"Generated: {sgf_file}")

def main():
    # 用户输入
    output_dir = "541_datasets"
    num_games = int(input("Enter the number of games to generate: ").strip())
    board_size = int(input("Enter board size (9, 13, 19): ").strip())
    min_moves = int(input("Enter minimum number of moves per game: ").strip())
    max_moves = int(input("Enter maximum number of moves per game: ").strip())

    # 参数校验
    if board_size not in [9, 13, 19]:
        print("Invalid board size! Only 9, 13, or 19 are supported.")
        return
    if min_moves > max_moves:
        print("Minimum moves cannot exceed maximum moves!")
        return

    # 生成 SGF 文件
    generate_random_sgf(output_dir, num_games, board_size, min_moves, max_moves)
    print("\nAll SGF files have been generated!")

if __name__ == "__main__":
    main()


