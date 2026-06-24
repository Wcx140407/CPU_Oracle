import xml.etree.ElementTree as ET
import random
import os


def generate_random_xml(output_file, num_root_children, max_depth, min_pieces=5, max_pieces=15):
    """
    动态生成随机 XML 文件
    :param output_file: 输出 XML 文件路径
    :param num_root_children: 根节点的子节点数量
    :param max_depth: 最大嵌套深度
    :param min_pieces: 每个节点的最小随机属性/子节点数量
    :param max_pieces: 每个节点的最大随机属性/子节点数量
    """
    def add_random_children(parent, current_depth):
        """递归添加随机子节点"""
        if current_depth >= max_depth:
            return
        num_children = random.randint(min_pieces, max_pieces)
        for _ in range(num_children):
            # 随机生成节点名和属性
            tag_name = f"node{random.randint(1, 100)}"
            attribs = {f"attr{random.randint(1, 10)}": f"value{random.randint(1, 100)}"}
            child = ET.SubElement(parent, tag_name, attribs)
            child.text = f"Value {random.randint(1, 1000)}"  # 随机节点值
            add_random_children(child, current_depth + 1)

    # 创建根节点
    root = ET.Element("root")
    add_random_children(root, 0)

    # 添加根子节点
    for _ in range(num_root_children):
        child = ET.SubElement(root, f"child{random.randint(1, 100)}")
        add_random_children(child, 1)

    # 保存生成的 XML
    tree = ET.ElementTree(root)
    tree.write(output_file, encoding="utf-8", xml_declaration=True)
    print(f"Generated random XML: {output_file}")


def generate_dynamic_xsl(output_file, xml_file):
    """
    动态生成 XSL 文件，解析 XML 文件中的节点结构生成转换规则
    :param output_file: 输出 XSL 文件路径
    :param xml_file: 用于分析结构的 XML 文件路径
    """
    tree = ET.parse(xml_file)
    root = tree.getroot()

    def collect_tags(node, tags):
        """递归收集 XML 中的所有唯一标签名"""
        tags.add(node.tag)
        for child in node:
            collect_tags(child, tags)

    # 收集所有标签
    tags = set()
    collect_tags(root, tags)

    # 动态生成 XSL 内容
    xsl_content = """<?xml version="1.0" encoding="UTF-8"?>\n"""
    xsl_content += """<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">\n"""
    xsl_content += """    <xsl:template match="/">\n"""
    xsl_content += """        <html>\n"""
    xsl_content += """            <body>\n"""
    xsl_content += """                <h2>Dynamic XML Data</h2>\n"""
    xsl_content += """                <table border="1">\n"""
    xsl_content += """                    <tr><th>Tag</th><th>Attributes</th><th>Value</th></tr>\n"""
    for tag in tags:
        xsl_content += f"""                    <xsl:for-each select="//{tag}">\n"""
        xsl_content += f"""                        <tr>\n"""
        xsl_content += f"""                            <td>{tag}</td>\n"""
        xsl_content += f"""                            <td><xsl:value-of select="concat(@*, ' ')"/></td>\n"""
        xsl_content += f"""                            <td><xsl:value-of select='.'/></td>\n"""
        xsl_content += f"""                        </tr>\n"""
        xsl_content += f"""                    </xsl:for-each>\n"""
    xsl_content += """                </table>\n"""
    xsl_content += """            </body>\n"""
    xsl_content += """        </html>\n"""
    xsl_content += """    </xsl:template>\n"""
    xsl_content += """</xsl:stylesheet>\n"""

    # 写入 XSL 文件
    with open(output_file, "w") as f:
        f.write(xsl_content)
    print(f"Generated dynamic XSL: {output_file}")


def generate_multiple_datasets(num_datasets, base_dir, num_root_children, max_depth, min_pieces, max_pieces):
    """
    批量生成随机 XML 和对应的动态 XSL 数据集
    :param num_datasets: 生成数据集数量
    :param base_dir: 数据集保存目录
    :param num_root_children: 根节点子节点数量
    :param max_depth: XML 最大深度
    :param min_pieces: 节点最小随机子节点/属性数量
    :param max_pieces: 节点最大随机子节点/属性数量
    """
    os.makedirs(base_dir, exist_ok=True)

    for i in range(1, num_datasets + 1):
        xml_file = os.path.join(base_dir, f"dataset_{num_root_children}.xml")
        xsl_file = os.path.join(base_dir, f"dataset_{num_root_children}.xsl")

        # 生成随机 XML 和对应的动态 XSL 文件
        generate_random_xml(xml_file, num_root_children, max_depth, min_pieces, max_pieces)
        generate_dynamic_xsl(xsl_file, xml_file)


if __name__ == "__main__":
    # 用户配置参数
    num_datasets = 1  # 数据集数量
    base_dir = "523_datasets"  # 保存目录
    #num_root_children = 10000  # 根节点子节点数量
    num_root_children = int(input("Enter node num: ").strip())
    max_depth = 4 # 最大深度
    min_pieces = 3  # 最小随机子节点/属性数量
    max_pieces = 6  # 最大随机子节点/属性数量

    # 批量生成数据集
    generate_multiple_datasets(num_datasets, base_dir, num_root_children, max_depth, min_pieces, max_pieces)
