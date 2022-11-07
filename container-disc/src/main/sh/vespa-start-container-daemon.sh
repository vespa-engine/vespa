#!/bin/bash
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

if test "$(uname -s)" = Darwin
then
    DISCRIMINATOR=`echo ${VESPA_CONFIG_ID} | md5 -r | cut -d' ' -f1`
else
    DISCRIMINATOR=`echo ${VESPA_CONFIG_ID} | md5sum | cut -d' ' -f1`
fi
CONTAINER_HOME="${VESPA_HOME}/var/jdisc_container/${DISCRIMINATOR}/"

if [[ "$VESPA_SERVICE_NAME" = "container" || "$VESPA_SERVICE_NAME" = "container-clustercontroller" ]]; then
    ZOOKEEPER_LOG_FILE_PREFIX="${VESPA_HOME}/logs/vespa/zookeeper.${VESPA_SERVICE_NAME}"
    rm -f $ZOOKEEPER_LOG_FILE_PREFIX*lck
    zookeeper_log_file_property="-Dzookeeper_log_file_prefix=${ZOOKEEPER_LOG_FILE_PREFIX}"
fi

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


getconfig() {
    set -e
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
    cmds=`echo "$qrstartcfg" | sed -n 's/^\([^. ]*\)[.]/\1_/;s/ /=/p'`
    eval "$cmds"
    set +e
}

# Print the value of the cgroups v2 interface filename $1 for current process,
# returning 0 on success. $1 is e.g. memory.max.
vespa_cg2get() {
    local filename="$1"

    # Verify cgroups v2
    if ! [ -e /sys/fs/cgroup/cgroup.controllers ]; then
        return 1
    fi

    local cgroup_content
    if ! cgroup_content=$(< /proc/self/cgroup); then
        echo "No such file: /proc/self/cgroup" >& 2
        return 1
    fi

    local slice
    while read -r; do
        if [ -n "$slice" ]; then
            echo "More than one line in /proc/self/cgroup" >&2
            return 1
        fi
        # Ignore prefix of line up to and including the right-most ':'.
        # Example line: "0::/user.slice/user-1002.slice/session-29.scope"
        slice="${REPLY##*:}"
    done <<< "$cgroup_content"

    local root_dir=/sys/fs/cgroup
    local leaf_dir="$root_dir$slice"
    local current_dir="$leaf_dir"

    local min_value=
    while (( ${#current_dir} >= ${#root_dir} )); do
        local path="$current_dir"/"$filename"
        if [ -r "$path" ]; then
            local value=$(< "$path")
            if [ -z "$min_value" ]; then
                min_value="$value"
            elif [ "$min_value" == max ]; then
                min_value="$value"
            elif [ "$value" != max ] && (( value < min_value )); then
                min_value="$value"
            fi
        fi

        current_dir="${current_dir%/*}"
    done

    if [ -z "$min_value" ]; then
        echo "No such filename was found at $leaf_dir: $filename" >&2
        return 1
    fi

    echo "$min_value"
    return 0
}

configure_memory() {
    consider_fallback jvm_minHeapsize 1536
    consider_fallback jvm_heapsize 1536
    consider_fallback jvm_stacksize 512
    consider_fallback jvm_baseMaxDirectMemorySize 75
    consider_fallback jvm_compressedClassSpaceSize 32
    consider_fallback jvm_directMemorySizeCache 0

    # Update jvm_heapsize only if percentage is explicitly set (default is 0).
    if ((jvm_heapSizeAsPercentageOfPhysicalMemory > 0)); then
        available=`free -m | grep Mem | tr -s ' ' | cut -f2 -d' '`
        if hash cgget 2>/dev/null; then
            # TODO: Create vespa_cgget for this and remove dependency on libcgroup-tools
            available_cgroup_bytes=$(cgget -nv -r memory.limit_in_bytes / 2>&1)
            if [ $? -ne 0 ]; then
                if [[ "$available_cgroup_bytes" =~ "Cgroup is not mounted" ]]; then
                    available_cgroup_bytes=$(vespa_cg2get memory.max)
                else
                    echo "$available_cgroup_bytes" >&2
                fi

                # If command failed or returned value is 'max' assign a big value (default in CGroup v1)
                if ! [[ "$available_cgroup_bytes" =~ ^[0-9]+$ ]]; then
                   available_cgroup_bytes=$(((1 << 63) -1))
                fi
            fi
            available_cgroup=$((available_cgroup_bytes >> 20))
            available=$((available > available_cgroup ? available_cgroup : available))
        fi
        #Subtract 1G as fixed overhead for an application container.
        reserved_mem=1024
        available=$((available > reserved_mem ? available - reserved_mem : available))

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
    memory_options="${memory_options} $(get_jvm_hugepage_settings $jvm_heapsize)"

    if ((jvm_compressedClassSpaceSize != 0)); then
        memory_options="${memory_options} -XX:CompressedClassSpaceSize=${jvm_compressedClassSpaceSize}m"
    fi

}

configure_cpu() {
    if ((jvm_availableProcessors > 0)); then
        cpu_options="-XX:ActiveProcessorCount=${jvm_availableProcessors}"
    else
        cpu_options="-XX:ActiveProcessorCount=`nproc --all`"
    fi
}

configure_numactl() {
    numactlcmd=$(get_numa_ctl_cmd)
}

configure_gcopts() {
    consider_fallback jvm_gcopts "-XX:MaxTenuringThreshold=15 -XX:NewRatio=1"
    if [ "$jvm_verbosegc" = "true" ]; then
        jvm_gcopts="${jvm_gcopts} -Xlog:gc"
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
        -XX:ErrorFile="${VESPA_HOME}/var/crash/hs_err_pid%p.log" \
        -XX:+ExitOnOutOfMemoryError \
        --add-opens=java.base/java.io=ALL-UNNAMED \
        --add-opens=java.base/java.lang=ALL-UNNAMED \
        --add-opens=java.base/java.net=ALL-UNNAMED \
        --add-opens=java.base/java.nio=ALL-UNNAMED \
        --add-opens=java.base/jdk.internal.loader=ALL-UNNAMED \
        --add-opens=java.base/sun.security.ssl=ALL-UNNAMED  \
        -Djava.io.tmpdir="${VESPA_HOME}/var/tmp" \
        -Djava.library.path="${VESPA_HOME}/lib64" \
        -Djava.security.properties=${VESPA_HOME}/conf/vespa/java.security.override \
        -Djava.awt.headless=true \
        -Djavax.net.ssl.keyStoreType=JKS \
        -Djdk.tls.rejectClientInitiatedRenegotiation=true \
        -Dsun.rmi.dgc.client.gcInterval=3600000 \
        -Dsun.net.client.defaultConnectTimeout=5000 -Dsun.net.client.defaultReadTimeout=60000 \
        -Djdisc.config.file="$cfpfile" \
        -Djdisc.export.packages=${jdisc_export_packages} \
        -Djdisc.cache.path="$bundlecachedir" \
        -Djdisc.bundle.path="${VESPA_HOME}/lib/jars" \
        -Djdisc.logger.enabled=false \
        -Djdisc.logger.level=WARNING \
        -Djdisc.logger.tag="${VESPA_CONFIG_ID}" \
        -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.Jdk14Logger \
        -Dvespa.log.control.dir="${VESPA_LOG_CONTROL_DIR}" \
        ${zookeeper_log_file_property} \
        -Dfile.encoding=UTF-8 \
        -cp "$CP" \
        "$@" \
        com.yahoo.jdisc.core.StandaloneMain file:${VESPA_HOME}/lib/jars/container-disc-jar-with-dependencies.jar

