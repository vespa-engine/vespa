#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

SBCMD=../../apps/sbcmd/vespa-slobrok-cmd
SLOBROK=../../apps/slobrok/vespa-slobrok


listall () {
    sleep 0.2
    echo port 18481:
    ${SBCMD} 18481 slobrok.lookupRpcServer '*/*'
    ${SBCMD} 18481 slobrok.admin.listAllRpcServers

    echo port 18482:
    ${SBCMD} 18482 slobrok.lookupRpcServer '*/*'

    echo port 18483:
    ${SBCMD} 18483 slobrok.lookupRpcServer '*/*'

    echo port 18484:
    ${SBCMD} 18484 slobrok.lookupRpcServer '*/*'
    sleep 0.2
}


${VALGRIND} ${SLOBROK} -p 18481 > log.s1.txt 2>&1 &
${VALGRIND} ${SLOBROK} -p 18482 > log.s2.txt 2>&1 &
${VALGRIND} ${SLOBROK} -p 18483 > log.s3.txt 2>&1 &
${VALGRIND} ${SLOBROK} -p 18484 > log.s4.txt 2>&1 &

sleep 1
[ "${VALGRIND}" ] && sleep 9

./slobrok_rpc_info_app \
        tcp/localhost:18481 verbose > rpc-method-list

echo port 18481:
${SBCMD} 18481 slobrok.callback.listNamesServed
echo port 18482:
${SBCMD} 18482 slobrok.callback.listNamesServed
echo port 18483:
${SBCMD} 18483 slobrok.callback.listNamesServed
echo port 18484:
${SBCMD} 18484 slobrok.callback.listNamesServed

listall

./slobrok_tstdst_app -s 18481 -p 18485 &
./slobrok_tstdst_app -s 18482 -p 18486 &
./slobrok_tstdst_app -s 18483 -p 18487 -n othertest/17 &
./slobrok_tstdst_app -s 18484 -p 18488 -n testrpcsrv/13 &

listall

add=tcp/localhost:18482
${SBCMD} 18481 slobrok.admin.addPeer $add $add
add=tcp/localhost:18483
${SBCMD} 18481 slobrok.admin.addPeer $add $add
add=tcp/localhost:18484
${SBCMD} 18481 slobrok.admin.addPeer $add $add
add=tcp/localhost:18481
${SBCMD} 18484 slobrok.admin.addPeer $add $add

listall

./slobrok_tstdst_app -s 18484 -p 18489 -n testrpcsrv/19 &

listall

rem=tcp/localhost:18482
${SBCMD} 18481 slobrok.admin.removePeer $rem $rem

./slobrok_tstdst_app -s 18482 -p 18490 -n testrpcsrv/19 &

listall

./slobrok_tstdst_app -s 18483 -p 18491 -n testrpcsrv/19 &

listall

./slobrok_tstdst_app -s 18481 -p 18492 -n testrpcsrv/19 &

listall
${SBCMD} 18481 slobrok.admin.listAllRpcServers
sleep 3

${SBCMD} 18485 system.stop
${SBCMD} 18486 system.stop
${SBCMD} 18487 system.stop

listall

${SBCMD} 18488 system.stop

listall

${SBCMD} 18482 slobrok.system.stop
${SBCMD} 18483 slobrok.system.stop
${SBCMD} 18484 slobrok.system.stop

listall

${SBCMD} 18481 slobrok.system.stop
${SBCMD} 18489 system.stop
${SBCMD} 18491 system.stop
${SBCMD} 18492 system.stop
${SBCMD} 18490 system.stop

wait
