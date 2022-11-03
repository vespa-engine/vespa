#! /bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# common environment setup for vespa scripts

# put these two lines in all scripts:
# cd $VESPA_HOME || { echo "Cannot cd to $VESPA_HOME" 1>&2; exit 1; } # usually wanted
# . $VESPA_HOME/libexec/vespa/common-env.sh || exit 1

varhasvalue () {
    # does the variable named in $1 have a (non-empty) value?
    eval "oldvariablevalue=\${$1}"
    [ "$oldvariablevalue" ]
}

varisempty () {
    # is the variable named in $1 unset, or does it have an empty value?
    eval "oldvariablevalue=\${$1}"
    [ -z "$oldvariablevalue" ]
}

consider_fallback () {
    if varhasvalue $1; then
        : $1 already has value $oldvariablevalue
    elif [ -z "${2}" ]; then
        : proposed value "${2}" is empty
    elif [ `expr "$2" : ".*'"` != 0 ]; then
        : proposed value "${2}" contains a single-quote
    else
        eval "${1}='${2}'"
        export ${1}
        # echo "FALLBACK: ${1}='${2}'"
    fi
}

read_conf_file () {
    deffile="$VESPA_HOME/conf/vespa/default-env.txt"
    if [ -f "${deffile}" ]; then
        eval $(${VESPA_HOME}/libexec/vespa/script-utils export-env)
    fi
}

# Check if a variable is set. Plain environment precedes prefixed vars.
get_var() {
   arg=$1
   ret_val=$2

   env_var_name=`echo $arg | tr '[:lower:]' '[:upper:]'`

   if   varhasvalue $env_var_name       ; then eval "ret_val=\${$env_var_name}"
   fi
   echo "$ret_val"
}

populate_environment () {
    # these are the variables we want while running vespa:
    # VESPA_HOME - where is Vespa installed
    # VESPA_CONFIGSERVERS - the host (or list of host) where a configserver runs
    # VESPA_CONFIG_SOURCES - possible override as the first place to get config

    # network settings
    # VESPA_PORT_BASE - the start of the port range where vespa services should listen
    # VESPA_CONFIGSERVER_RPC_PORT -  the RPC port for configservers
    # VESPA_CONFIGSERVER_HTTP_PORT - the webservice (REST api) port for configservers
    # VESPA_CONFIG_PROTOCOL_VERSION - the RPC protocol version to use
    # VESPA_WEB_SERVICE_PORT - where the main REST apis will normally run

    # debugging
    # VESPA_VALGRIND_OPT - for memory leak tracking, the options to use for valgrind
    # VESPA_USE_VESPAMALLOC_D - use debugging version of malloc
    # VESPA_USE_VESPAMALLOC_DST - use debugging and tracking version of malloc

    # optimizations
    # VESPA_USE_HUGEPAGES_LIST - list of programs that should use huge pages or memory
    # VESPA_USE_MADVISE_LIST - list of programs that should use madvise()
    # VESPA_USE_VESPAMALLOC - list of programs that should preload an optimized malloc
    # VESPA_USE_NO_VESPAMALLOC - list of programs that should use normal system malloc

    read_conf_file
    consider_fallback ROOT ${VESPA_HOME%/}
    if id vespa >/dev/null 2>&1 ; then
        consider_fallback VESPA_USER "vespa"
    elif id nobody >/dev/null 2>&1 ; then
        consider_fallback VESPA_USER "nobody"
    fi
}

prepend_path () {
    case ":$PATH:" in
        *:"$1":*) ;;
	*) PATH="$1:$PATH" ;;
    esac
}

add_valgrind_suppressions_file() {
    if [ -f "$1" ] ; then
	VESPA_VALGRIND_SUPPREESSIONS_OPT="$VESPA_VALGRIND_SUPPREESSIONS_OPT --suppressions=$1"
    fi
}

optionally_reduce_base_frequency() {
    if [ -z "$VESPA_TIMER_HZ" ]; then
        os_release=`uname -r`
        if [[ "$os_release" == *linuxkit* ]]; then
            export VESPA_TIMER_HZ=100
            : "Running docker on macos. Reducing base frequency from 1000hz to 100hz due to high cost of sampling time. This will reduce timeout accuracy. VESPA_TIMER_HZ=$VESPA_TIMER_HZ"
        fi
    else
        : "VESPA_TIMER_HZ already set to $VESPA_TIMER_HZ. Skipping auto detection."
    fi
}

get_thp_size_mb() {
    local thp_size=2
    if [ -r /sys/kernel/mm/transparent_hugepage/hpage_pmd_size ]; then
        local bytes
        read -r bytes < /sys/kernel/mm/transparent_hugepage/hpage_pmd_size
        thp_size=$((bytes / 1024 / 1024))
    fi
    echo "$thp_size"
}

get_jvm_hugepage_settings() {
    local heap_mb="$1"
    local sz_mb=$(get_thp_size_mb)
    if (($sz_mb * 2 < $heap_mb)); then
        options=" -XX:+UseTransparentHugePages"
    fi
    echo "$options"
}

get_heap_size() {
    local param=$1
    local args=$2
    local value=$3
    for token in $args
    do
        [[ "$token" =~ ^"${param}"([0-9]+)(.)$ ]] || continue
        size="${BASH_REMATCH[1]}"
        unit="${BASH_REMATCH[2],,}" # lower-case
        case "$unit" in
            k) value=$(( $size / 1024 )) ;;
            m) value="$size" ;;
            g) value=$(( $size * 1024 )) ;;
            *) echo "Warning: Invalid unit in '$token'" >&2 ;;
        esac
    done
    echo "$value"
}

get_min_heap_mb() {
    get_heap_size "-Xms" "$1" $2
}

get_max_heap_mb() {
    get_heap_size "-Xmx" "$1" $2
}

populate_environment

export LD_LIBRARY_PATH=$VESPA_HOME/lib64
export MALLOC_ARENA_MAX=1

optionally_reduce_base_frequency

# Prefer newer gdb and pstack
prepend_path /opt/rh/gcc-toolset-11/root/usr/bin

# Maven is needed for tester applications
prepend_path "$VESPA_HOME/local/maven/bin"
prepend_path "$VESPA_HOME/bin64"
prepend_path "$VESPA_HOME/bin"

# how to find the "java" program?
# should be available in $VESPA_HOME/bin or JAVA_HOME
if [ "$JAVA_HOME" ] && [ -f "${JAVA_HOME}/bin/java" ]; then
    prepend_path "${JAVA_HOME}/bin"
fi

VESPA_VALGRIND_SUPPREESSIONS_OPT=""
add_valgrind_suppressions_file ${VESPA_HOME}/etc/vespa/valgrind-suppressions.txt
add_valgrind_suppressions_file ${VESPA_HOME}/etc/vespa/suppressions.txt

consider_fallback VESPA_VALGRIND_OPT "--num-callers=32 \
--run-libc-freeres=yes \
--track-origins=yes \
--freelist-vol=1000000000 \
--leak-check=full \
--show-reachable=yes \
${VESPA_VALGRIND_SUPPREESSIONS_OPT}"

consider_fallback VESPA_USE_HUGEPAGES_LIST  "$(get_var hugepages_list)"
consider_fallback VESPA_USE_HUGEPAGES_LIST  "all"

consider_fallback VESPA_USE_MADVISE_LIST    "$(get_var madvise_list)"

consider_fallback VESPA_USE_VESPAMALLOC     "$(get_var vespamalloc_list)"
consider_fallback VESPA_USE_VESPAMALLOC_D   "$(get_var vespamallocd_list)"
consider_fallback VESPA_USE_VESPAMALLOC_DST "$(get_var vespamallocdst_list)"
consider_fallback VESPA_USE_NO_VESPAMALLOC  "$(get_var no_vespamalloc_list)"
consider_fallback VESPA_USE_NO_VESPAMALLOC  "vespa-rpc-invoke vespa-get-config vespa-sentinel-cmd vespa-route vespa-proton-cmd vespa-configproxy-cmd vespa-config-status"

# TODO:
# if [ "$VESPA_USE_HUGEPAGES_LIST" = "all" ]; then
#     # Can not enable this blindly as java will emit error if not available. THP must do then.
#     export VESPA_USE_HUGEPAGES="yes"
# fi


fixlimits () {
    max_processes_limit=409600
    if ! varhasvalue file_descriptor_limit; then
        file_descriptor_limit=262144
    fi

    max_processes=$(ulimit -u)
    core_size=$(ulimit -c)
    file_descriptor=$(ulimit -n)
    # Warn if we Cannot bump limits when not root
    if [ "$(id -u)" -ne 0 ]; then
        # number of open files:
        if [ $file_descriptor -lt $file_descriptor_limit ]; then
            echo "Expected file descriptor limit to be at least $file_descriptor_limit, was $file_descriptor" >&2
        fi

        # core file size
        if [ "$core_size" != "unlimited" ]; then
            echo "Expected core file size to be unlimited, was $core_size" >&2
        fi

        # number of processes/threads
        if [ "$max_processes" != "unlimited" ] && [ "$max_processes" -lt "$max_processes_limit" ]; then
            echo "Expected max processes to be at least $max_processes_limit, was $max_processes" >&2
        fi
    else
        # number of open files:
        if [ $file_descriptor -lt $file_descriptor_limit ]; then
            ulimit -n "$file_descriptor_limit" || exit 1
        fi

        # core file size
        if [ "$core_size" != "unlimited" ]; then
            ulimit -c unlimited
        fi

        # number of processes/threads
        if [ "$max_processes" != "unlimited" ] && [ "$max_processes" -lt "$max_processes_limit" ]; then
            ulimit -u "$max_processes_limit"
        fi
    fi
}

checkjava () {
    if java -version 2>&1 | grep "64-Bit Server VM" >/dev/null ; then
	: OK
    else
	echo
	echo "java must invoke the 64-bit Java VM"
	echo "Got:"
	java -version
	echo "Path: $PATH"
	echo
	exit 1
    fi
}

runvalidation() {
    run=$VESPA_VALIDATIONSCRIPT
    if [ "$run" ]; then
	if [ -x "$run" ]; then
	    if $run ; then
		: OK
	    else
		echo "validation script '$run' failed"
		exit 1
	    fi
	else
	    echo "validation script '$run' must be executable"
	    exit 1
	fi
    else
	: no validation script
    fi
}

disable_vm_zone_reclaim_mode () {
    # Should probably also be done on host.
    dn=/proc/sys/vm/zone_reclaim_mode
    if [ -w $dn ]; then
        echo 0 > $dn
    fi
}

drop_caches () {
    dn=/proc/sys/vm/drop_caches
    if [ -w $dn ]; then
        echo 3 > $dn
    fi
}

enable_transparent_hugepages_with_background_compaction () {
    # Should probably also be done on host.
    if grep -q "release 6" /etc/redhat-release; then
        dn=/sys/kernel/mm/redhat_transparent_hugepage
        khugepaged_defrag=yes
    else
        dn=/sys/kernel/mm/transparent_hugepage
        khugepaged_defrag=1
    fi

    if [ -w $dn/enabled ]; then
	echo always > $dn/enabled
    fi
    if [ -w $dn/defrag ]; then
	echo never > $dn/defrag
    fi
    if [ -w $dn/khugepaged/defrag ]; then
	echo $khugepaged_defrag > $dn/khugepaged/defrag
    fi
}

use_configserver_if_needed () {
    nvcs=$($VESPA_HOME/bin/vespa-print-default configsources)
    x="$VESPA_CONFIG_SOURCES"
    if [ "$x" ]; then
	export VESPA_CONFIG_SOURCES="$x,$nvcs"
    else
	export VESPA_CONFIG_SOURCES="$nvcs"
    fi
}

getJavaOptionsIPV46() {
    if ${VESPA_HOME}/libexec/vespa/script-utils ipv6-only; then
        echo " -Djava.net.preferIPv6Addresses=true"
    fi
}

log_message () {
    msg_log_level="info"
    case $1 in
        error|warning|info|event|config|debug|spam) msg_log_level=$1; shift;;
    esac
    printf "%s\t%s\t%s\n" $$ $msg_log_level "$*"
}

log_debug_message () {
    if [ "$YINST_RUNNING" ]; then
        log_message "debug" "$*"
    fi
}

log_warning_message () {
    log_message "warning" "$*" 1>&2
}

get_numa_ctl_cmd () {
    if ! type numactl &> /dev/null; then
        if test "$(uname -s)" = Darwin
        then
            return 0
        fi
        echo "FATAL: Could not find required program numactl."
        exit 1
    fi

    if numactl --interleave all echo &> /dev/null; then
        # We are allowed to use numactl
        numnodes=$(numactl --hardware 2>/dev/null |
                   grep available |
                   awk '$3 == "nodes" { print $2 }')

        if [ "$VESPA_AFFINITY_CPU_SOCKET" ] &&
           [ "$numnodes" -gt 1 ]
        then
            node=$(($VESPA_AFFINITY_CPU_SOCKET % $numnodes))
            numactlcmd="numactl --cpunodebind=$node --membind=$node"
        else
            numactlcmd="numactl --interleave all"
        fi
    else
        numactlcmd=""
    fi

    echo $numactlcmd
}

