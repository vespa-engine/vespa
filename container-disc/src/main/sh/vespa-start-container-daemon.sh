#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#set -x

if [ -z "${VESPA_HOME}" ]; then
    echo "Missing VESPA_HOME variable"
    exit 1
fi
if [ -z "${VESPA_SERVICE_NAME}" ]; then
    echo "Missing VESPA_SERVICE_NAME variable"
    exit 1
fi
if [ -z "${VESPA_CONFIG_ID}" ]; then
    echo "Missing VESPA_CONFIG_ID variable"
    exit 1
fi
cd ${VESPA_HOME} || { echo "Cannot cd to ${VESPA_HOME}" 1>&2; exit 1; }

. libexec/vespa/common-env.sh

DISCRIMINATOR=`echo ${VESPA_CONFIG_ID} | md5sum | cut -d' ' -f1`
CONTAINER_HOME="${VESPA_HOME}/var/jdisc_container/${DISCRIMINATOR}/"

ZOOKEEPER_LOG_FILE="${VESPA_HOME}/logs/vespa/zookeeper.${VESPA_SERVICE_NAME}.log"
rm -f $ZOOKEEPER_LOG_FILE*lck

# common setup
export VESPA_LOG_TARGET=file:${VESPA_HOME}/logs/vespa/vespa.log
export VESPA_LOG_CONTROL_DIR=${VESPA_HOME}/var/db/vespa/logcontrol
export LD_LIBRARY_PATH=${VESPA_HOME}/lib64

cfpfile=${CONTAINER_HOME}/jdisc.properties
bundlecachedir=${CONTAINER_HOME}/bundlecache

# class path
CP="${VESPA_HOME}/lib/jars/jdisc_core-jar-with-dependencies.jar"

mkdir -p $bundlecachedir || exit 1
printenv > $cfpfile || exit 1

# ??? TODO ??? XXX ???
# LANG=en_US.utf8
# LC_ALL=C


getconfig() {
    qrstartcfg=""
    case "${VESPA_CONFIG_ID}" in
        dir:*)
            config_dir=${VESPA_CONFIG_ID#dir:}
            qrstartcfg="`cat ${config_dir}/qr-start.cfg`"
            ;;
        *)
            qrstartcfg="`$VESPA_HOME/bin/vespa-get-config -l -w 10 -n search.config.qr-start -i ${VESPA_CONFIG_ID}`"
            ;;
    esac
    cmds=`echo "$qrstartcfg" | perl -ne 's/^(\w+)\.(\w+) (.*)/$1_$2=$3/ && print'`
    eval "$cmds"
}

configure_memory() {
    consider_fallback jvm_minHeapsize 1536
    consider_fallback jvm_heapsize 1536
    consider_fallback jvm_stacksize 512
    consider_fallback jvm_baseMaxDirectMemorySize 75
    consider_fallback jvm_directMemorySizeCache 0

    # Update jvm_heapsize only if percentage is explicitly set (default is 0).
    if ((jvm_heapSizeAsPercentageOfPhysicalMemory > 0)); then
        available=`free -m | grep Mem | tr -s ' ' | cut -f2 -d' '`
        if hash cgget 2>/dev/null; then
            available_cgroup_bytes=$(cgget -nv -r memory.limit_in_bytes /)
            available_cgroup=$((available_cgroup_bytes >> 20))
            available=$((available > available_cgroup ? available_cgroup : available))
        fi

        jvm_heapsize=$((available * jvm_heapSizeAsPercentageOfPhysicalMemory / 100))
        jvm_minHeapsize=${jvm_heapsize}
    fi

    # Safety measure against bad min vs max heapsize.
   if ((jvm_minHeapsize > jvm_heapsize)); then
        jvm_minHeapsize=${jvm_heapsize}
        echo "Misconfigured heap size, jvm_minHeapsize(${jvm_minHeapsize} is larger than jvm_heapsize(${jvm_heapsize}). It has been capped."
   fi

    maxDirectMemorySize=$(( jvm_baseMaxDirectMemorySize + jvm_heapsize / 8 + jvm_directMemorySizeCache ))

    memory_options="-Xms${jvm_minHeapsize}m -Xmx${jvm_heapsize}m"
    memory_options="${memory_options} -XX:ThreadStackSize=${jvm_stacksize}"
    memory_options="${memory_options} -XX:MaxDirectMemorySize=${maxDirectMemorySize}m"    

    if [ "${VESPA_USE_HUGEPAGES}" ]; then
        memory_options="${memory_options} -XX:+UseLargePages"
    fi
}

configure_cpu() {
    if ((jvm_availableProcessors > 0)); then
        cpu_options="-XX:ActiveProcessorCount=${jvm_availableProcessors}"
    else
        cpu_options="-XX:ActiveProcessorCount=`nproc`"
    fi
}

configure_numactl() {
    log_message debug "starting ${VESPA_SERVICE_NAME} for ${VESPA_CONFIG_ID}"
    if numactl --interleave all true &> /dev/null; then
        # We are allowed to use numactl
        numnodes=$(numactl --hardware |
                   grep available |
                   awk '$3 == "nodes" { print $2 }')
        if [ "$VESPA_AFFINITY_CPU_SOCKET" ] &&
           [ "$numnodes" -gt 1 ]
        then
            node=$(($VESPA_AFFINITY_CPU_SOCKET % $numnodes))
            log_message debug "with affinity to $VESPA_AFFINITY_CPU_SOCKET out of $numnodes cpu sockets"
            numactlcmd="numactl --cpunodebind=$node --membind=$node"
        else
            log_message debug "with memory interleaving on all nodes"
            numactlcmd="numactl --interleave all"
        fi
    else
            log_message debug "without numactl (no permission or not available)"
            numactlcmd=""
    fi
    log_message debug "numactlcmd: $numactlcmd"
}

configure_gcopts() {
    consider_fallback jvm_gcopts "-XX:MaxTenuringThreshold=15 -XX:NewRatio=1"
    if [ "$jvm_verbosegc" = "true" ]; then
        jvm_gcopts="${jvm_gcopts} -verbose:gc"
    fi
}

configure_env_vars() {
    if [ "$qrs_env" ]; then
        for setting in ${qrs_env} ; do
            case $setting in
                *"="*)
                    eval "$setting";
                    export ${setting%%=*}
                    ;;
                *)
                    log_message warning "ignoring invalid qrs_env setting '$setting' from '$qrs_env'"
                    ;;
            esac
        done
    fi
}

configure_classpath () {
    if [ "${jdisc_classpath_extra}" ]; then
        CP="${CP}:${jdisc_classpath_extra}"
    fi
}

configure_preload () {
    export JAVAVM_LD_PRELOAD=
    unset LD_PRELOAD
    envcmd="/usr/bin/env"
    # trim whitespace:
    PRELOAD=${PRELOAD# }
    PRELOAD=${PRELOAD% }
    if [ "$PRELOAD" ]; then
        envcmd="/usr/bin/env JAVAVM_LD_PRELOAD=$PRELOAD LD_PRELOAD=$PRELOAD"
        log_message config "setting up extra preload: $envcmd"
    fi
}

# import VARIABLENAME with default VALUE
import_cfg_var () {
   varname=$1
   ret_val=$2
   prefixed_varname="vespa_container__${varname}"

   if varhasvalue $varname ; then
       : already set
   elif varhasvalue $prefixed_varname ; then
       eval "$varname=\${$prefixed_varname}"
   else
       eval "$varname=\${ret_val}"
   fi
}

getconfig
configure_memory
configure_gcopts
configure_env_vars
configure_classpath
configure_numactl
configure_cpu
configure_preload

exec $numactlcmd $envcmd java \
        -Dconfig.id="${VESPA_CONFIG_ID}" \
        -XX:+PreserveFramePointer \
        ${VESPA_CONTAINER_JVMARGS} \
        ${cpu_options} \
        ${memory_options} \
        ${jvm_gcopts} \
        -XX:MaxJavaStackTraceDepth=1000000 \
        -XX:+HeapDumpOnOutOfMemoryError \
        -XX:HeapDumpPath="${VESPA_HOME}/var/crash" \
        -XX:+ExitOnOutOfMemoryError \
        --illegal-access=warn \
        --add-opens=java.base/java.io=ALL-UNNAMED \
        --add-opens=java.base/java.lang=ALL-UNNAMED \
        --add-opens=java.base/java.net=ALL-UNNAMED \
        --add-opens=java.base/jdk.internal.loader=ALL-UNNAMED \
        -Djava.library.path="${VESPA_HOME}/lib64" \
        -Djava.awt.headless=true \
        -Djavax.net.ssl.keyStoreType=JKS \
        -Dsun.rmi.dgc.client.gcInterval=3600000 \
        -Dsun.net.client.defaultConnectTimeout=5000 -Dsun.net.client.defaultReadTimeout=60000 \
        -Djdisc.config.file="$cfpfile" \
        -Djdisc.export.packages=${jdisc_export_packages} \
        -Djdisc.cache.path="$bundlecachedir" \
        -Djdisc.debug.resources=false \
        -Djdisc.bundle.path="${VESPA_HOME}/lib/jars" \
        -Djdisc.logger.enabled=false \
        -Djdisc.logger.level=ALL \
        -Djdisc.logger.tag="${VESPA_CONFIG_ID}" \
        -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.Jdk14Logger \
        -Dvespa.log.control.dir="${VESPA_LOG_CONTROL_DIR}" \
        -Dzookeeperlogfile="${ZOOKEEPER_LOG_FILE}" \
        -Dfile.encoding=UTF-8 \
        -cp "$CP" \
        "$@" \
        com.yahoo.jdisc.core.StandaloneMain file:${VESPA_HOME}/lib/jars/container-disc-jar-with-dependencies.jar
