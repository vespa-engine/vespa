#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

ok=true

../../apps/sbcmd/sbcmd 18511 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18512 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18513 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18514 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18515 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18516 slobrok.system.stop || ok=false
../../apps/sbcmd/sbcmd 18517 slobrok.system.stop || ok=false

sleep 2

for x in `cat pids.txt`; do
	kill $x 2>/dev/null && ok=false
done
rm -f pids.txt

$ok
