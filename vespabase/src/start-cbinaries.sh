#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# BEGIN environment bootstrap section
# Do not edit between here and END as this section should stay identical in all scripts

findpath () {
    myname=${0}
    mypath=${myname%/*}
    myname=${myname##*/}
    if [ "$mypath" ] && [ -d "$mypath" ]; then
        return
    fi
    mypath=$(pwd)
    if [ -f "${mypath}/${myname}" ]; then
        return
    fi
    echo "FATAL: Could not figure out the path where $myname lives from $0"
    exit 1
}

COMMON_ENV=libexec/vespa/common-env.sh

source_common_env () {
    if [ "$VESPA_HOME" ] && [ -d "$VESPA_HOME" ]; then
        export VESPA_HOME
        common_env=$VESPA_HOME/$COMMON_ENV
        if [ -f "$common_env" ]; then
            . $common_env
            return
        fi
    fi
    return 1
}

findroot () {
    source_common_env && return
    if [ "$VESPA_HOME" ]; then
        echo "FATAL: bad VESPA_HOME value '$VESPA_HOME'"
        exit 1
    fi
    if [ "$ROOT" ] && [ -d "$ROOT" ]; then
        VESPA_HOME="$ROOT"
        source_common_env && return
    fi
    findpath
    while [ "$mypath" ]; do
        VESPA_HOME=${mypath}
        source_common_env && return
        mypath=${mypath%/*}
    done
    echo "FATAL: missing VESPA_HOME environment variable"
    echo "Could not locate $COMMON_ENV anywhere"
    exit 1
}

findhost () {
    if [ "${VESPA_HOSTNAME}" = "" ]; then
        VESPA_HOSTNAME=$(vespa-detect-hostname || hostname -f || hostname || echo "localhost") || exit 1
    fi
    validate="${VESPA_HOME}/bin/vespa-validate-hostname"
    if [ -f "$validate" ]; then
        "$validate" "${VESPA_HOSTNAME}" || exit 1
    fi
    export VESPA_HOSTNAME
}

findroot
findhost

# END environment bootstrap section

bname=`basename $0`
no_valgrind=true
use_callgrind=false

export STD_THREAD_PREVENT_TRY_CATCH=true
export GLIBCXX_FORCE_NEW=1
unset LD_PRELOAD

check_bname_in_value () {
    if [ "$1" = "all" ]; then
       return 0
    fi
    for f in $1; do
       if [ "$f" = "$bname" ]; then
           return 0
       fi
    done
    return 1
}

configure_valgrind () {
    no_valgrind=true
    if which valgrind >/dev/null; then
        if check_bname_in_value $VESPA_USE_VALGRIND; then
            no_valgrind=false
            valgrind_log=$VESPA_HOME/tmp/valgrind.$bname.log.$$
            case $VESPA_VALGRIND_OPT in
                *callgrind*) use_callgrind=true;;
            esac
        fi
    fi
}

configure_huge_pages () {
    if check_bname_in_value $VESPA_USE_HUGEPAGES_LIST; then
        log_debug_message "Want huge pages for '$bname' since VESPA_USE_HUGEPAGES_LIST=${VESPA_USE_HUGEPAGES_LIST}"
        export VESPA_USE_HUGEPAGES="yes"
    fi
}

configure_use_madvise () {
    for f in $VESPA_USE_MADVISE_LIST
    do
        # log_debug_message "Testing '$f'"
        app=`echo $f | cut -d '=' -f1`
        limit=`echo $f | cut -d '=' -f2`
        if [ "$app" = "$bname" ]; then
            log_debug_message "I ($bname) shall use madvise with limit $limit, as set in VESPA_USE_MADVISE_LIST"
            export VESPA_MALLOC_MADVISE_LIMIT="$limit"
            break
        fi

        if [ "$app" = "all" ]; then
            log_debug_message "I shall use madvise with limit $limit, as VESPA_USE_MADVISE_LIST is 'all'"
            export VESPA_MALLOC_MADVISE_LIMIT="$limit"
        fi
    done
}

configure_vespa_malloc () {
    if check_bname_in_value $VESPA_USE_NO_VESPAMALLOC; then
        # log_debug_message "Not using vespamalloc for '$bname' since VESPA_USE_NO_VESPAMALLOC=${VESPA_USE_NO_VESPAMALLOC}"
        return
    fi
    suf=vespa/malloc/libvespamalloc.so
    if check_bname_in_value $VESPA_USE_VESPAMALLOC_D; then
        suf=vespa/malloc/libvespamallocd.so
    fi
    if check_bname_in_value $VESPA_USE_VESPAMALLOC_DST; then
        suf=vespa/malloc/libvespamallocdst16.so
    fi

    if $no_valgrind || $use_callgrind; then
        # should really check that format is same as binary below
        for pre in lib64 lib; do
            tryfile="${VESPA_HOME}/${pre}/${suf}"
            if [ -f "$tryfile" ]; then
                LD_PRELOAD="$tryfile"
                log_debug_message "Using LD_PRELOAD='$tryfile'"
                if [ "$VESPA_USE_HUGEPAGES" ]; then
                    export VESPA_MALLOC_HUGEPAGES="$VESPA_USE_HUGEPAGES"
                    log_debug_message "enabling hugepages for '$0-bin'."
                fi
                break
            fi
        done
    fi
}

configure_numa_ctl () {
    numactl=""
    if numactl --interleave all true &> /dev/null; then
        # We are allowed to use numactl
        numactl="numactl --interleave all"
        if [ "$VESPA_AFFINITY_CPU_SOCKET" ]; then
            numcpu=`numactl --hardware 2>/dev/null | grep available | cut -d' ' -f2`
            if [ "$numcpu" ] && [ "$numcpu" -gt 1 ]; then
                log_debug_message "Starting $0 with affinity $VESPA_AFFINITY_CPU_SOCKET out of $numcpu"
                node=$(($VESPA_AFFINITY_CPU_SOCKET % $numcpu))
                numactl="numactl --cpunodebind=$node --membind=$node"
            fi
        fi
    fi
}

configure_valgrind
configure_huge_pages
configure_use_madvise
configure_vespa_malloc

if $no_valgrind ; then
    configure_numa_ctl
    ulimit -c unlimited
    log_debug_message "Starting $0 with : " \
         $numactl env LD_PRELOAD=$LD_PRELOAD $0-bin "$@"
    exec $numactl env LD_PRELOAD=$LD_PRELOAD $0-bin "$@"
else
    if $use_callgrind ; then
        export LD_PRELOAD
    else
        export LD_PRELOAD=""
    fi
    # ignore signal until shared libraries have loaded, etc:
    trap "" SIGTERM
    log_debug_message "Starting $0 with : " \
         valgrind $VESPA_VALGRIND_OPT --log-file="$valgrind_log" $0-bin "$@"
    exec valgrind $VESPA_VALGRIND_OPT --log-file="$valgrind_log" $0-bin "$@"
fi
