#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

export VESPA_LOG_LEVEL='all -spam'

sed s=localhost=`hostname`= < template-7.cfg > 7.cfg

../../apps/slobrok/slobrok -c file:7.cfg -p 18511 > slobrok0.out 2>&1 &
echo $! >> pids.txt
../../apps/slobrok/slobrok -c file:7.cfg -p 18512 > slobrok1.out 2>&1 &
echo $! >> pids.txt
../../apps/slobrok/slobrok -c file:7.cfg -p 18513 > slobrok2.out 2>&1 &
echo $! >> pids.txt
../../apps/slobrok/slobrok -c file:7.cfg -p 18514 > slobrok3.out 2>&1 &
echo $! >> pids.txt
../../apps/slobrok/slobrok -c file:7.cfg -p 18515 > slobrok4.out 2>&1 &
echo $! >> pids.txt
../../apps/slobrok/slobrok -c file:7.cfg -p 18516 > slobrok5.out 2>&1 &
echo $! >> pids.txt 
../../apps/slobrok/slobrok -c file:7.cfg -p 18517 > slobrok6.out 2>&1 &
echo $! >> pids.txt

echo "Started: " `cat pids.txt`

export VESPA_LOG_LEVEL='all -debug -spam'

for x in 1 2 3 4 5 6 7 8 9; do
    sleep $x
    echo "waiting for service location brokers to start, slept $x seconds"
    alive=true
    for port in 18511 18512 18513 18514 18515 18516 18517; do 
        ../../apps/sbcmd/sbcmd $port slobrok.callback.listNamesServed || alive=false
    done
    if $alive; then
        echo "all started ok after $x seconds"
        exit 0
    fi
done
echo "giving up, this probably won't work"
exit 1
