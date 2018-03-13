#! /bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    elif [ `expr match "$2" ".*'"` != 0 ]; then
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
        eval $(perl -ne '
            chomp;
            my ($action, $varname, $value) = split(" ", $_, 3);
            $varname =~ s{[.]}{__}g;
            if ($varname !~ m{^\w+}) {
                # print STDERR "invalid var name $varname"
                next;
            }
            if ($action eq "fallback" || $action eq "override") {
                next if ($action eq "fallback" && $ENV{$varname} ne "");
                # quote value
                if ($value !~ m{^["][^"]*["]$} ) {
                    $value =~ s{(\W)}{\\$1}g;
                }
                print "$varname=$value; export $varname; "
            }
            if ($action eq "unset") {
                print "export -n $varname; unset $varname; "
            }
        ' < $deffile)
    fi
}

# Check if a variable is set. Plain environment precedes prefixed vars.
get_var() {
   arg=$1
   ret_val=$2

   env_var_name=`echo $arg | tr '[:lower:]' '[:upper:]'`
   prefixed_var_name1=services__${arg}
   prefixed_var_name2=vespa_base__${arg}

   if   varhasvalue $env_var_name       ; then eval "ret_val=\${$env_var_name}"
   elif varhasvalue $prefixed_var_name1 ; then eval "ret_val=\${$prefixed_var_name1}"
   elif varhasvalue $prefixed_var_name2 ; then eval "ret_val=\${$prefixed_var_name2}"
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

populate_environment

PATH=$VESPA_HOME/bin64:$VESPA_HOME/bin:/usr/local/bin:/usr/X11R6/bin:/sbin:/bin:/usr/sbin:/usr/bin
export LD_LIBRARY_PATH=$VESPA_HOME/lib64
export MALLOC_ARENA_MAX=1

# how to find the "java" program?
# should be available in $VESPA_HOME/bin or JAVA_HOME
if [ "$JAVA_HOME" ] && [ -f "${JAVA_HOME}/bin/java" ]; then
    PATH="${PATH}:${JAVA_HOME}/bin"
fi

consider_fallback VESPA_VALGRIND_OPT "--num-callers=32 \
--run-libc-freeres=yes \
--track-origins=yes \
--freelist-vol=1000000000 \
--leak-check=full \
--show-reachable=yes \
--suppressions=${VESPA_HOME}/etc/vespa/suppressions.txt"

consider_fallback VESPA_USE_HUGEPAGES_LIST  $(get_var "hugepages_list")
consider_fallback VESPA_USE_HUGEPAGES_LIST  "all"

consider_fallback VESPA_USE_MADVISE_LIST    $(get_var "madvise_list")

consider_fallback VESPA_USE_VESPAMALLOC     $(get_var "vespamalloc_list")
consider_fallback VESPA_USE_VESPAMALLOC_D   $(get_var "vespamallocd_list")
consider_fallback VESPA_USE_VESPAMALLOC_DST $(get_var "vespamallocdst_list")
consider_fallback VESPA_USE_NO_VESPAMALLOC  $(get_var "no_vespamalloc_list")

# TODO:
# if [ "$VESPA_USE_HUGEPAGES_LIST" = "all" ]; then
#     # Can not enable this blindly as java will emit error if not available. THP must do then.
#     export VESPA_USE_HUGEPAGES="yes"
# fi


fixlimits () {
    # Cannot bump limits when not root (for testing)
    if [ "${VESPA_UNPRIVILEGED}" = yes ]; then
	return 0
    fi
    # number of open files:
    if varhasvalue file_descriptor_limit; then
       ulimit -n ${file_descriptor_limit} || exit 1
    elif [ `ulimit -n` -lt 262144 ]; then
        ulimit -n 262144 || exit 1
    fi

    # core file size
    if [ `ulimit -c` != "unlimited" ]; then
        ulimit -c unlimited
    fi

    # number of processes/threads
    max_processes=`ulimit -u`
    if [ "$max_processes" != "unlimited" ] && [ "$max_processes" -lt 409600 ]; then
        ulimit -u 409600
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
    run=$vespa_base__validationscript
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

no_transparent_hugepages () {
    # Should probably also be done on host.
    dn=/sys/kernel/mm/redhat_transparent_hugepage
    if [ -w $dn/enabled ]; then
	echo always > $dn/enabled
    fi
    if [ -w $dn/defrag ]; then
	echo never > $dn/defrag
    fi
    if [ -w $dn/khugepaged/defrag ]; then
	echo yes > $dn/khugepaged/defrag
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
    canon_ipv4=$(hostname | perl -pe 'chomp; ($_,$rest) = gethostbyname($_);')
    if [ -z "${canon_ipv4}" ]; then
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
