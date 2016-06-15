#!/bin/sh -e
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

findex=../../../bin/findex

echo "CLEAN"
rm -f index.cf
rm -f summary.cf
rm -rf merged
rm -rf datapart.*

echo "DOCSUM-INDEX"
./docsum-index

echo "AUTOINDEX"
$findex autoindex
