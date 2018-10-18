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
    consider_fallback jvm_heapsize 1536
    consider_fallback jvm_stacksize 512
    consider_fallback jvm_baseMaxDirectMemorySize 75
    consider_fallback jvm_directMemorySizeCache 0

    # Update jvm_heapsize only if percentage is explicitly set (default is 0).
    if ((jvm_heapSizeAsPercentageOfPhysicalMemory > 0)); then
        if ((VESPA_TOTAL_MEMORY_MB > 0)); then
            available="$VESPA_TOTAL_MEMORY_MB"
        else
            available=`free -m | grep Mem | tr -s ' ' | cut -f2 -d' '`
        fi

        jvm_heapsize=$((available * jvm_heapSizeAsPercentageOfPhysicalMemory / 100))
        if (( jvm_heapsize < 1024 )); then
            jvm_heapsize=1024
        fi
    fi

    maxDirectMemorySize=$(( jvm_baseMaxDirectMemorySize + jvm_heapsize / 8 + jvm_directMemorySizeCache ))

    memory_options="-Xms${jvm_heapsize}m -Xmx${jvm_heapsize}m"
    memory_options="${memory_options} -XX:ThreadStackSize=${jvm_stacksize}"
    memory_options="${memory_options} -XX:MaxDirectMemorySize=${maxDirectMemorySize}m"    

    if [ "${VESPA_USE_HUGEPAGES}" ]; then
        memory_options="${memory_options} -XX:+UseLargePages"
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

exec_jsvc () {
    if [ "$jsvc_classpath_pre" ]; then
        CP="${jsvc_classpath_pre}:${CP}"
    fi
    for jf in $jsvc_extra_classpath_libjars ; do
        CP="${CP}:${VESPA_HOME}/lib/jars/$jf.jar"
    done
    for jf in $jsvc_extra_classpath_files ; do
        CP="${CP}:jf"
    done

    PRELOAD="$PRELOAD $jsvc_extra_preload"
    configure_preload
    exec $numactlcmd $envcmd $jsvc_binary_name \
        -Dconfig.id="${VESPA_CONFIG_ID}" \
        -XX:+PreserveFramePointer \
        ${jsvc_opts} \
        ${memory_options} \
        ${jvm_gcopts} \
        -XX:MaxJavaStackTraceDepth=1000000 \
        -XX:+HeapDumpOnOutOfMemoryError \
        -XX:HeapDumpPath="${VESPA_HOME}/var/crash" \
        -XX:+ExitOnOutOfMemoryError \
        --illegal-access=warn \
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
        -Djdisc.logger.enabled=true \
        -Djdisc.logger.level=ALL \
        -Djdisc.logger.tag="${VESPA_CONFIG_ID}" \
        -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.Jdk14Logger \
        -Dvespa.log.control.dir="${VESPA_LOG_CONTROL_DIR}" \
        -Dzookeeperlogfile="${ZOOKEEPER_LOG_FILE}" \
        -Dfile.encoding=UTF-8 \
        -cp "$CP" \
        "$@" \
        com.yahoo.jdisc.core.BootstrapDaemon file:${VESPA_HOME}/lib/jars/container-disc-jar-with-dependencies.jar
}

maybe_use_jsvc () {
    import_cfg_var use_jsvc "false"

    # if configured, run JSVC aka commons.daemon
    if [ "$use_jsvc" = "true" ]; then
        import_cfg_var jsvc_binary_name "jsvc"
        import_cfg_var jsvc_extra_preload

        import_cfg_var jsvc_use_pidfile "false"

        import_cfg_var jsvc_classpath_pre
        import_cfg_var jsvc_extra_classpath_libjars
        import_cfg_var jsvc_extra_classpath_files
        import_cfg_var jsvc_ipv6opts
        import_cfg_var jsvc_extra_opts
        import_cfg_var jsvc_normal_opts
        import_cfg_var jsvc_java_home_opt
        if [ "$jsvc_use_pidfile" = "true" ]; then
            import_cfg_var jsvc_pidfile_opt "-pidfile ${VESPA_HOME}/var/run/jsvc.${VESPA_SERVICE_NAME}.pid"
        else
            import_cfg_var jsvc_pidfile_opt ""
        fi
        import_cfg_var jsvc_user_opt
        import_cfg_var jsvc_agent_opt
        import_cfg_var jsvc_ynet_opt
        import_cfg_var jsvc_unknown_opts

        jsvc_opts="$jsvc_ipv6opts $jsvc_extra_opts $jsvc_normal_opts $jsvc_java_home_opt $jsvc_pidfile_opt $jsvc_user_opt $jsvc_agent_opt $jsvc_ynet_opt $jsvc_unknown_opts"
        exec_jsvc
    fi
}

getconfig
configure_memory
configure_gcopts
configure_env_vars
configure_classpath
configure_numactl
configure_preload
maybe_use_jsvc

exec $numactlcmd $envcmd java \
        -Dconfig.id="${VESPA_CONFIG_ID}" \
        -XX:+PreserveFramePointer \
        ${memory_options} \
        ${jvm_gcopts} \
        -XX:MaxJavaStackTraceDepth=1000000 \
        -XX:+HeapDumpOnOutOfMemoryError \
        -XX:HeapDumpPath="${VESPA_HOME}/var/crash" \
        -XX:+ExitOnOutOfMemoryError \
        --illegal-access=warn \
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
