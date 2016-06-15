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
