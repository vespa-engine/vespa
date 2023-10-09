#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

if [ -z "$SOURCE_DIRECTORY" ]; then
    SOURCE_DIRECTORY="."
fi

fail=0

$SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh start server 1

$BINREF/runjava PollRPCServer tcp/localhost:$PORT_0 || fail=1
$BINREF/runjava MockupInvoke tcp/localhost:$PORT_0 aaa bbb >  out.txt || fail=1
$BINREF/runjava MockupInvoke tcp/localhost:$PORT_0 bbb ccc >> out.txt || fail=1
$BINREF/runjava MockupInvoke tcp/localhost:$PORT_0 ccc ddd >> out.txt || fail=1
$BINREF/runjava MockupInvoke tcp/localhost:$PORT_0 ddd eee >> out.txt || fail=1
$BINREF/runjava MockupInvoke tcp/localhost:$PORT_0 eee fff >> out.txt || fail=1

$SOURCE_DIRECTORY/../../binref/progctl.sh $SOURCE_DIRECTORY/progdefs.sh stop server 1

diff -u out.txt $SOURCE_DIRECTORY/ref.txt || fail=1

exit $fail
