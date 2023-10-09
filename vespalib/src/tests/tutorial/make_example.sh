#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
dirname=`dirname $1`
filename=`basename $1`
unset VALGRIND
unset TEST_SUBSET

echo "<div class=\"example\" id=\"$dirname\">"
echo "<h2>$filename</h2>"
echo "<pre class=\"prettyprint linenums\">"
(cd $dirname && cat $filename) | ./vespalib_xml_escape_app
echo "</pre>"
echo "<pre class=\"output\">"
(cd $dirname && ./vespalib_${filename%.cpp}_app 2>&1) | ./vespalib_xml_escape_app
echo "</pre>"
echo "</div>"
