#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# define prog entry (called from progdefs file)
# $1 - program name
# $2 - instance name
# $3 - usage string
# $4 - full program name (if not set, $BINREF/$1 will be used)
prog() {
    idx=$entry_cnt
    eval "prog_$idx=\"$1\""
    eval "instance_$idx=\"$2\""
    eval "usage_$idx=\"$3\""
    eval "name_$idx=\"$4\""
    entry_cnt=$(($entry_cnt + 1))
}

# initial usage check, then load progdefs
if [ "$#" -lt 1 ]; then
    echo "usage: $0 <progdefs> ..."
    exit 1
fi
progdefs=$1
shift
usage=""
name=""
entry_cnt=0

case $progdefs in */*) ;; *) progdefs="./$progdefs" ;; esac

. $progdefs # read progdefs file

# print dynamic usage based on progdefs
print_usage() {
    idx=0
    while [ $idx -lt $entry_cnt ]; do
	eval "p=\"\$prog_$idx\""
	eval "i=\"\$instance_$idx\""
	echo "usage: ... (start|stop|check) $p $i"
	idx=$(($idx + 1))
    done
    echo "usage: ... (start|stop|check) all"
    echo "start/stop/check programs."
    exit 1
}

# check if given prog/instance pair is available
# $1 - prog
# $2 - instance
check_entry() {
    idx=0
    while [ $idx -lt $entry_cnt ]; do
	eval "p=\"\$prog_$idx\""
	eval "i=\"\$instance_$idx\""
	if [ "$p" = "$1" ] && [ "$i" = "$2" ]; then
	    eval "usage=\"\$usage_$idx\""
	    eval "name=\"\$name_$idx\""
	    return 0
	fi
	idx=$(($idx + 1))
    done
    return 1
}

# check for correct usage based on progdefs
check_usage() {
    if [ "$1" != "start" ] && [ "$1" != "stop" ] && [ "$1" != "check" ]; then
	print_usage
    fi
    if [ "$#" -eq 2 ] && [ "$2" = "all" ]; then
	return 0
    fi
    if [ "$#" -eq 3 ] && check_entry "$2" "$3"; then
	return 0
    fi
    print_usage
}

# start a single program
# $1 - program name
# $2 - instance name
# $3 - usage string
# $4 - full program name (if not set, $BINREF/$1 will be used)
start() {
    pid=`cat pid."$1"."$2" 2>/dev/null`
    if [ "X$pid" != "X" ] && kill -0 $pid 2> /dev/null; then
	return 0
    fi
    if [ "X$4" != "X" ]; then
	$4 $3 > out."$1"."$2" 2>&1 &
    else
	$BINREF/$1 $3 > out."$1"."$2" 2>&1 &
    fi
    echo "$!" > pid."$1"."$2"
}

# stop a single program
# $1 - program name
# $2 - instance name
stop() {
    pid=`cat pid."$1"."$2" 2>/dev/null`
    if [ "X$pid" = "X" ]; then
	return 0
    fi
    kill $pid > /dev/null 2>&1
    rm -f pid."$1"."$2"
}

# check a single program
# $1 - program name
# $2 - instance name
check() {
    pid=`cat pid."$1"."$2" 2>/dev/null`
    if [ "X$pid" != "X" ] && kill -0 $pid 2> /dev/null; then
	echo "$1/$2 is running"
    else
	echo "$1/$2 is not running"
    fi
}

# start all known programs
start_all() {
    idx=0
    while [ $idx -lt $entry_cnt ]; do
	eval "p=\$prog_$idx"
	eval "i=\$instance_$idx"
	eval "u=\$usage_$idx"
        eval "n=\$name_$idx"
	start "$p" "$i" "$u" "$n"
	idx=$(($idx + 1))
    done
}

# stop all known programs
stop_all() {
    idx=0
    while [ $idx -lt $entry_cnt ]; do
	eval "p=\$prog_$idx"
	eval "i=\$instance_$idx"
	stop "$p" "$i"
	idx=$(($idx + 1))
    done
}

# check all known programs
check_all() {
    idx=0
    while [ $idx -lt $entry_cnt ]; do
	eval "p=\$prog_$idx"
	eval "i=\$instance_$idx"
	check "$p" "$i"
	idx=$(($idx + 1))
    done
}

############################
############################

check_usage "$@"

# handle start/stop/check all
if [ "$2" = "all" ]; then
    if [ "$1" = "start" ]; then
	start_all
    elif [ "$1" = "stop" ]; then
	stop_all
    else
	check_all
    fi
    exit 0
fi

# handle start/stop/check of single program
if [ "$1" = "start" ]; then
    start "$2" "$3" "$usage" "$name"
elif [ "$1" = "stop" ]; then
    stop "$2" "$3"
else
    check "$2" "$3"
fi
