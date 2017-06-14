# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import sys
import time

megabyte = [0] * (1024 * 1024 / 8)
data = megabyte * int(sys.argv[1])

while True:
    time.sleep(1)
    data.extend(megabyte)
