from chess import Board, Piece
import random

def generate_custom_piece_positions(size, output_file, min_pieces=5, max_pieces=15):
    """生成指定数量的随机棋盘布局，剩余棋子数量固定在自定义范围内"""
    with open(output_file, 'w') as f:
        generated = 0
        while generated < size:
            # 创建一个空棋盘
            board = Board()
            board.clear()

            # 随机生成棋子数量
            piece_count = random.randint(min_pieces, max_pieces)

            # 随机放置棋子
            pieces = [
                Piece(piece_type, color)
                for piece_type in [1, 2, 3, 4, 5, 6]  # 棋子类型: 1~6 (兵、骑士、象、车、后、王)
                for color in [True, False]           # 颜色: True (白), False (黑)
            ]
            random.shuffle(pieces)  # 随机化棋子顺序
            placed_pieces = 0

            while placed_pieces < piece_count and len(pieces) > 0:
                piece = pieces.pop()
                square = random.randint(0, 63)  # 棋盘上的随机位置
                if board.piece_at(square) is None:  # 检查该位置是否为空
                    board.set_piece_at(square, piece)
                    placed_pieces += 1

            # 添加当前棋盘布局到文件
            fen = board.fen()
            bm = random.choice(list(board.legal_moves)).uci() if not board.is_checkmate() else "0000"
            f.write(f"{fen} bm {bm}; id \"Custom.{generated+1}\";\n")
            f.write(f"{piece_count}\n")
            generated += 1

    print(f"Generated {size} positions in {output_file} with pieces between {min_pieces} and {max_pieces}.")

# 用户输入
try:
    size = int(input("Enter the number of chess positions to generate: "))
    if size <= 0:
        raise ValueError("Size must be a positive integer.")
    
    min_pieces = int(input("Enter the minimum number of pieces: "))
    max_pieces = int(input("Enter the maximum number of pieces: "))
    if min_pieces <= 0 or max_pieces <= 0 or min_pieces > max_pieces:
        raise ValueError("Invalid range for piece count.")

    # 设置输出文件名
    output_file = f"random_dataset_{size}.txt"

    # 生成棋盘
    generate_custom_piece_positions(size, output_file, min_pieces, max_pieces)

except ValueError as e:
    print(f"Invalid input: {e}")