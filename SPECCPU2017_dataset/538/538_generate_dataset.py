import os
from PIL import Image, ImageDraw, ImageFont
import random


def generate_random_tga_images(output_dir, num_images, width, height):
    """
    生成随机 .tga 图片文件
    :param output_dir: 图片保存路径
    :param num_images: 图片数量
    :param width: 图片宽度
    :param height: 图片高度
    """
    os.makedirs(output_dir, exist_ok=True)
    for i in range(num_images):
        # 创建随机颜色图片
        image = Image.new("RGB", (width, height), (random.randint(0, 255), random.randint(0, 255), random.randint(0, 255)))

        # 在图片上绘制随机文字
        draw = ImageDraw.Draw(image)
        text = f"Image {i+1}"
        try:
            font = ImageFont.truetype("arial.ttf", 20)
        except:
            font = ImageFont.load_default()
        text_width, text_height = draw.textsize(text, font=font)
        text_position = ((width - text_width) // 2, (height - text_height) // 2)
        draw.text(text_position, text, fill=(255, 255, 255), font=font)

        # 保存图片为 .tga 格式
        output_path = os.path.join(output_dir, f"input_image_{i+1}.tga")
        image.save(output_path, "TGA")
        print(f"Generated: {output_path}")


def generate_imagick_commands(input_dir, output_file, scale):
    """
    根据数据集规模生成 ImageMagick 转换指令
    :param input_dir: 输入图片目录
    :param output_file: 输出命令文件路径
    :param scale: 数据集规模，可选值为 'small', 'medium', 'large'
    """
    # 定义不同规模的操作参数
    operations = {
        "small": [
            "-resize 320x240",
            "-edge 10",
            "-negate",
            "-flop"
        ],
        "medium": [
            "-resize 800x600",
            "-shear 20x10",
            "-edge 30",
            "-negate",
            "-flop",
            "-colorspace YUV"
        ],
        "large": [
            "-shear 40x20",
            "-edge 50",
            "-negate",
            "-flop",
            "-emboss 25",
            "-mean-shift 15x15+20%",
            "-resize 35%"
        ]
    }

    # 确定操作集
    selected_ops = operations.get(scale, operations["small"])

    # 写入命令文件
    with open(output_file, "w") as f:
        for image_file in sorted(os.listdir(input_dir)):
            if image_file.endswith(".tga"):
                input_path = os.path.join(input_dir, image_file)
                output_path = os.path.join(input_dir, f"processed_{image_file}")
                command = f"convert {input_path} -limit disk 0 {' '.join(selected_ops)} {output_path}"
                f.write(command + "\n")
                print(f"Generated command: {command}")


def main():
    # 用户输入参数
    # output_dir = input("Enter output directory for .tga images: ").strip()
    output_dir = "538_datasets"
    # num_images = int(input("Enter the number of images to generate: ").strip())
    num_images = 1
    width = int(input("Enter image width: ").strip())
    height = int(input("Enter image height: ").strip())
    #scale = input("Enter dataset scale (small, medium, large): ").strip().lower()
    # command_file = input("Enter path for ImageMagick command file: ").strip()
    #command_file = "imagick_commands.txt"

    # 生成 .tga 图片
    print("\nGenerating .tga images...")
    generate_random_tga_images(output_dir, num_images, width, height)

    # 生成 ImageMagick 转换指令
    # print("\nGenerating ImageMagick commands...")
    # generate_imagick_commands(output_dir, command_file, scale)

    print("\nAll tasks completed!")


if __name__ == "__main__":
    main()
