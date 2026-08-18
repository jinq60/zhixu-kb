#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
离线 PDF -> Markdown 转换工具（轻量版：PyMuPDF + pdfplumber）

用途：
  当 Docker 里没有空间/资源部署重量级 PDF 解析服务（Docling/Marker）时，
  先用这个脚本把 PDF 转成 markdown（保留段落、表格、图片），
  再把 output.md 上传到项目里做 AI 整理。

安装依赖：
  pip install PyMuPDF==1.24.5 pdfplumber==0.11.0 Pillow

用法：
  python scripts/convert-pdf-to-md.py /path/to/document.pdf

输出：
  ./output/document/output.md
  ./output/document/images/*.png
"""

import base64
import os
import sys
from pathlib import Path


def main():
    if len(sys.argv) < 2:
        print(f"Usage: python {sys.argv[0]} <pdf-path>")
        sys.exit(1)

    pdf_path = Path(sys.argv[1]).resolve()
    if not pdf_path.exists():
        print(f"文件不存在: {pdf_path}")
        sys.exit(1)

    # 延迟导入，这样即使用户还没装依赖，也能先看到错误提示
    try:
        import fitz  # PyMuPDF
        import pdfplumber
    except ImportError as exc:
        print(f"缺少依赖: {exc}")
        print("请运行: pip install PyMuPDF==1.24.5 pdfplumber==0.11.0 Pillow")
        sys.exit(1)

    out_dir = Path("output") / pdf_path.stem
    img_dir = out_dir / "images"
    out_dir.mkdir(parents=True, exist_ok=True)
    img_dir.mkdir(parents=True, exist_ok=True)

    doc = fitz.open(str(pdf_path))
    md_lines = [f"# {pdf_path.name}\n"]

    for page_num, page in enumerate(doc, start=1):
        md_lines.append(f"\n## 第 {page_num} 页\n")

        # 1. 文本
        text = page.get_text().strip()
        if text:
            md_lines.append(text)

        # 2. 表格
        try:
            with pdfplumber.open(str(pdf_path)) as plumber:
                if page_num <= len(plumber.pages):
                    tables = plumber.pages[page_num - 1].extract_tables()
                    for table in tables:
                        if not table:
                            continue
                        md_lines.append("")
                        for row in table:
                            cells = [str(cell or "").replace("|", "\\|") for cell in row]
                            md_lines.append("| " + " | ".join(cells) + " |")
        except Exception as exc:
            md_lines.append(f"\n> 表格提取失败: {exc}\n")

        # 3. 图片：保存为文件，markdown 中引用相对路径
        for img_idx, img_info in enumerate(page.get_images(full=True), start=1):
            try:
                xref = img_info[0]
                pix = fitz.Pixmap(doc, xref)
                if pix.n > 4:  # CMYK -> RGB
                    pix = fitz.Pixmap(fitz.csRGB, pix)

                img_name = f"page{page_num}_img{img_idx}.png"
                img_path = img_dir / img_name
                pix.save(str(img_path))
                md_lines.append(f"\n![第 {page_num} 页图片 {img_idx}](./images/{img_name})\n")
            except Exception as exc:
                md_lines.append(f"\n> 图片提取失败: {exc}\n")

    md_path = out_dir / "output.md"
    md_path.write_text("\n\n".join(md_lines), encoding="utf-8")

    print(f"✅ 转换完成: {md_path}")
    print(f"   图片目录: {img_dir}")
    print(f"   总页数: {doc.page_count}")


if __name__ == "__main__":
    main()
