#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
filename=$1
echo "<h2>$filename</h2>"
echo "<pre class=\"prettyprint linenums\">"
(cat $filename | ./xml_escape)
echo "</pre>"
