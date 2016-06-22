#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
dirname=`dirname $1`
filename=`basename $1`
unset MAKELEVEL
unset MAKEFLAGS
unset VALGRIND
unset TEST_SUBSET

echo "<div class=\"example\" id=\"$dirname\">"
echo "<h2>$filename</h2>"
echo "<pre class=\"prettyprint linenums\">"
(cd $dirname && cat $filename) | ./vespalib_xml_escape_app
echo "</pre>"
echo "<pre class=\"output\">"
DIRNAME=`(cd $dirname && /bin/pwd)`
(cd $dirname && ./vespalib_${filename%.cpp}_app 2>&1) | perl -pe "s{$DIRNAME/}{}g" | ./vespalib_xml_escape_app
echo "</pre>"
echo "</div>"
