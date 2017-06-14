#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#
# This script will be run by the 'runtests.sh' script before
# each individual test run. It will typically use the 'geturl'
# program to clear the fsearch and fdispatch caches.
#

# do not produce any output, log error messages to 'pretest.err'
exec > /dev/null 2>>pretest.err

#
# Clear fsearch and fdispatch caches. hostX and portX should be
# replaced with real host names and port numbers referring to the http
# daemons of the fsearch and fdispatch programs you are benchmarking.
#
#bin/geturl host1 port1 "/admin?command=clear_caches"
#bin/geturl host2 port2 "/admin?command=clear_caches"
#bin/geturl host3 port3 "/admin?command=clear_caches"
#bin/geturl host4 port4 "/admin?command=clear_caches"
#bin/geturl host5 port5 "/admin?command=clear_caches"
#...
