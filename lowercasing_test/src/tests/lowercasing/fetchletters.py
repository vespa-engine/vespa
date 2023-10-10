#!/usr/bin/env python3
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# This program reads a Unicode database and emits all letters in lower
# and upper case.

# Refer to http://www.unicode.org/ucd/ to download new files.

import sys

def add_character(unicodespec, characterstore):
    characterstora

def main(raw, out):
    # Fetch upper and lower case characters in Unicode
    characters = [x for x in raw if x[2] == 'Lu' or x[2] == 'Ll']
    image = [chr(int(c[0], 16)) for c in characters]
    output = "\n".join(image)
    out.write(output.encode("UTF-8"))
    out.write(u"\n".encode("UTF-8"))

if __name__ == '__main__':
    try:
        raw = [x.split(";") for x in open("./UnicodeData.txt", "r").readlines()]
    except:
        sys.stderr.write("Problems reading ./UnicodeData.txt.\n")
        sys.exit(1)
    main(raw, sys.stdout)
