#!/bin/sh
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
        # ensure it ends with "/" :
        VESPA_HOME=${VESPA_HOME%/}/
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

findroot

# END environment bootstrap section

bname=`basename $0`
no_valgrind=true
use_callgrind=false

case $VESPA_VALGRIND_OPT in
    *callgrind*) use_callgrind=true;;
esac

for f in $VESPA_USE_VALGRIND
do
    # log_debug_message "Testing '$f'"
    if [ "$f" = "$bname" ]; then
        # log_debug_message "I ($bname) shall use valgrind, as set in VESPA_USE_VALGRIND"
        no_valgrind=false
        break
    fi
done

if [ "$VESPA_USE_VALGRIND" = "all" ]; then
    # log_debug_message "I shall use valgrind, as VESPA_USE_VALGRIND is 'all'"
    no_valgrind=false
fi

export STD_THREAD_PREVENT_TRY_CATCH=true

# special malloc setup; we should make some better mechanism for this
#
export GLIBCXX_FORCE_NEW=1
#
unset LD_PRELOAD
p64=/home/y/lib64
p32=/home/y/lib32
pre=/home/y/lib
suf=vespa/malloc/libvespamalloc.so
#
# should really check that format is same as binary below
#
for f in $VESPA_USE_HUGEPAGES_LIST
do
    # log_debug_message "Testing '$f'"
    if [ "$f" = "$bname" ]; then
        # log_debug_message "I ($bname) shall use huge pages, as set in VESPA_USE_HUGEPAGES_LIST"
        export VESPA_USE_HUGEPAGES="yes"
        break
    fi
done

if [ "$VESPA_USE_HUGEPAGES_LIST" = "all" ]; then
    # log_debug_message "I shall use huge pages, as VESPA_USE_HUGEPAGES_LIST is 'all'"
    export VESPA_USE_HUGEPAGES="yes"
fi

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

for f in $VESPA_USE_NO_VESPAMALLOC
do
    # log_debug_message "Testing '$f'"
    if [ "$f" = "$bname" ]; then
        # log_debug_message "I ($bname) shall not use vespamalloc, as set in VESPA_USE_NO_VESPAMALLOC"
        suf=vespa/malloc/libvespamalloc_notexisting.so
        break
    fi
done

if [ "$VESPA_USE_NO_VESPAMALLOC" = "all" ]; then
    # log_debug_message "I shall not use, as VESPA_USE_NO_VESPAMALLOC is 'all'"
    suf=vespa/malloc/libvespamalloc_notexisting.so
fi

for f in $VESPA_USE_VESPAMALLOC
do
    # log_debug_message "Testing '$f'"
    if [ "$f" = "$bname" ]; then
        # log_debug_message "I ($bname) shall use libvespamalloc, as set in VESPA_USE_VESPAMALLOC"
        suf=vespa/malloc/libvespamalloc.so
        break
    fi
done

if [ "$VESPA_USE_VESPAMALLOC" = "all" ]; then
    # log_debug_message "I shall use libvespamalloc, as VESPA_USE_VESPAMALLOC is 'all'"
    suf=vespa/malloc/libvespamalloc.so
fi

for f in $VESPA_USE_VESPAMALLOC_D
do
    # log_debug_message "Testing '$f'"
    if [ "$f" = "$bname" ]; then
        # log_debug_message "I ($bname) shall use libvespamallocd, as set in VESPA_USE_VESPAMALLOC_D"
        suf=vespa/malloc/libvespamallocd.so
        break
    fi
done

if [ "$VESPA_USE_VESPAMALLOC_D" = "all" ]; then
    # log_debug_message "I shall use libvespamallocd, as VESPA_USE_VESPAMALLOC_D is 'all'"
    suf=vespa/malloc/libvespamallocd.so
fi

for f in $VESPA_USE_VESPAMALLOC_DST
do
    # log_debug_message "Testing '$f'"
    if [ "$f" = "$bname" ]; then
        # log_debug_message "I ($bname) shall use libvespamallocdst16, as set in VESPA_USE_VESPAMALLOC_DST"
        suf=vespa/malloc/libvespamallocdst16.so
        break
    fi
done

if [ "$VESPA_USE_VESPAMALLOC_DST" = "all" ]; then
    # log_debug_message "I shall use libvespamallocdst16, as VESPA_USE_VESPAMALLOC_DST is 'all'"
    suf=vespa/malloc/libvespamallocdst16.so
fi

if $no_valgrind || $use_callgrind; then
    for tryfile in $p64/$suf $p32/$suf $pre/$suf ; do
        if [ -f $tryfile ]; then
                LD_PRELOAD=$tryfile
		log_debug_message "Using LD_PRELOAD=$tryfile"
                if [ "$VESPA_USE_HUGEPAGES" ]; then
                    export VESPA_MALLOC_HUGEPAGES="$VESPA_USE_HUGEPAGES"
		    log_debug_message "enabling hugepages for '$0-bin'."
                fi
                break
        fi
    done
fi

# log_debug_message "VESPA_USE_VALGRIND='$VESPA_USE_VALGRIND'; bname='$bname'; no_valgrind='$no_valgrind'"

if $no_valgrind || ! which valgrind >/dev/null ; then
    # log_debug_message $0-bin $@
    ulimit -c unlimited
    if numactl --interleave all true &> /dev/null; then
        # We are allowed to use numactl
        if [ "$VESPA_AFFINITY_CPU_SOCKET" ]; then
            numcpu=`numactl --hardware | grep available | cut -d' ' -f2`
            node=$(($VESPA_AFFINITY_CPU_SOCKET % $numcpu))
            log_debug_message "Starting $0 with affinity on cpu $VESPA_AFFINITY_CPU_SOCKET out of $numcpu : " \
                 numactl --cpunodebind=$node --membind=$node env LD_PRELOAD=$LD_PRELOAD $0-bin "$@"
            exec numactl --cpunodebind=$node --membind=$node env LD_PRELOAD=$LD_PRELOAD $0-bin "$@"
        else
            log_debug_message "Starting $0 with memory interleaving on all nodes : " \
                 numactl --interleave all env LD_PRELOAD=$LD_PRELOAD $0-bin "$@"
            exec numactl --interleave all env LD_PRELOAD=$LD_PRELOAD $0-bin "$@"
        fi
    else
        log_debug_message "Starting $0 without numactl (no permission or not available) : " \
             env LD_PRELOAD=$LD_PRELOAD $0-bin "$@"
        exec env LD_PRELOAD=$LD_PRELOAD $0-bin "$@"
    fi
else
    if $use_callgrind ; then
        export LD_PRELOAD
    else
        export LD_PRELOAD=""
    fi
    # ignore signal until shared libraries have loaded, etc:
    trap "" SIGTERM
    log_debug_message "Starting : " \
         valgrind $VESPA_VALGRIND_OPT --log-file=/home/y/tmp/valgrind.$bname.log.$$ $0-bin "$@"
    exec valgrind $VESPA_VALGRIND_OPT --log-file=/home/y/tmp/valgrind.$bname.log.$$ $0-bin "$@"
fi
