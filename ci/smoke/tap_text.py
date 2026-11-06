#!/usr/bin/env python3
"""Find a UI node by visible text in a uiautomator dump and print its center.

Usage: tap_text.py <dump.xml> <text>
Prints "x y" on stdout; exits 1 when no node carries that exact text
(or content-desc).
"""
import re
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    path, needle = sys.argv[1], sys.argv[2]
    tree = ET.parse(path)
    for node in tree.iter("node"):
        if node.attrib.get("text", "") == needle or node.attrib.get("content-desc", "") == needle:
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
            if not m:
                continue
            x1, y1, x2, y2 = (int(v) for v in m.groups())
            print(f"{(x1 + x2) // 2} {(y1 + y2) // 2}")
            return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
