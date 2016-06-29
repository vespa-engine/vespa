#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

ok=true

../../apps/sbcmd/sbcmd 18511 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18512 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18513 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18514 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18515 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18516 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18517 slobrok.system.stop || ok=false

if $ok; then
    echo "Signaled all brokers to stop OK"
fi

for cnt in 1 2 3 4 5 6 7 8 9; do
    sleep $cnt
    alive=false
    for x in `cat pids.txt`; do
	kill $x 2>/dev/null && ps -p $x && alive=true
    done
    if $alive; then
        echo "Some processes still alive after $cnt seconds"
    else
        rm -f pids.txt
        $ok
        exit
    fi
done

for x in `cat pids.txt`; do
    kill -9 $x 2>/dev/null && echo "Force killed pid $x"
done
exit 1
