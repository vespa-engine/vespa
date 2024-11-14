#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# BEGIN environment bootstrap section
# Do not edit between here and END as this section should stay identical in all scripts

findpath () {
    myname=${0}
    mypath=${myname%/*}
    myname=${myname##*/}
    empty_if_start_slash=${mypath%%/*}
    if [ "${empty_if_start_slash}" ]; then
        mypath=$(pwd)/${mypath}
    fi
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

ROOT=${VESPA_HOME%/}
export ROOT

# END environment bootstrap section

Usage() {
    cat <<EOF
Usage: ${0##*/} start [OPTION]...
Usage: ${0##*/} stop [OPTION]...
Manage Vespa standalone jdisc container service.

Options:
  -u USER      Run as USER. Overrides any VESPA_USER environment variable.
  -s SERVICE   The service name.
  -f           Force the command by skipping some checks.
  -- ARGS...   Pass the rest of the arguments (ARGS) to the Java invocation
EOF

    exit 1
}

Fail() {
    printf "%s\n" "$*"
    exit 1
}

FixDataDirectory() {
    if ! [ -d "$1" ]; then
        if [ -e "$1" ]; then
            # TODO: Remove this if-branch once >=7.511 has rolled out everywhere
            echo "Removing file '$1'"
            rm "$1"
        fi
        echo "Creating data directory '$1'"
        mkdir -p "$1" || exit 1
    fi
    chown "${VESPA_USER}" "$1"
    chmod 755 "$1"
}

StartCommand() {
    local service="$1"
    local force="$2"
    shift 2
    local -a jvm_arguments=("$@")

    local service_regex='^[0-9a-zA-Z_-]+$'
    if ! [[ "$service" =~ $service_regex ]]; then
        Fail "Service must match regex '$service_regex'"
    fi

    local pidfile="${VESPA_HOME}/var/run/$service.pid"
    FixDataDirectory "$(dirname "$pidfile")"
    if [ "$force" = false ] && test -r "$pidfile"; then
        echo "$service is already running as PID $(< "$pidfile") according to $pidfile"
        return
    fi

    # common setup
    export VESPA_SERVICE_NAME="$service"

    if true; then
        ${VESPA_HOME}/libexec/vespa/vespa-wrapper run-standalone-container "${jvm_arguments[@]}" &
        echo $! > "$pidfile"
        return
    fi

    # stuff for the process:
    local appdir="${VESPA_HOME}/conf/$service-app"
    local cfpfile="${VESPA_HOME}/var/jdisc_container/$service.properties"
    local bundlecachedir="${VESPA_HOME}/var/vespa/bundlecache/$service"

    cd "${VESPA_HOME}" || Fail "Cannot cd to ${VESPA_HOME}"

    fixlimits

    # If JAVA_HOME is set in the environment, the sourcing of common-env.sh
    # elsewhere in this file ensures $JAVA_HOME/bin is first in PATH, so 'java'
    # may be invoked w/o path.  In any case, checkjava verifies bare 'java'.
    checkjava


    local vespa_log="${VESPA_HOME}/logs/vespa/vespa.log"
    export VESPA_LOG_TARGET="file:$vespa_log"
    FixDataDirectory "$(dirname "$vespa_log")"

    export VESPA_LOG_CONTROL_FILE="${VESPA_HOME}/var/db/vespa/logcontrol/$service.logcontrol"
    export VESPA_LOG_CONTROL_DIR="$(dirname "$VESPA_LOG_CONTROL_FILE")"
    FixDataDirectory "$VESPA_LOG_CONTROL_DIR"

    # Does not need fast allocation
    export MALLOC_ARENA_MAX=1

    # will be picked up by standalone-container:
    export standalone_jdisc_container__app_location="$appdir"

    # class path
    CP="${VESPA_HOME}/lib/jars/jdisc_core-jar-with-dependencies.jar"

    FixDataDirectory "$(dirname "$cfpfile")"
    printenv > "$cfpfile"
    FixDataDirectory "$bundlecachedir"
    FixDataDirectory "${VESPA_HOME}/var/crash"

    heap_min=$(get_min_heap_mb "${jvm_arguments}" 128)
    heap_max=$(get_max_heap_mb "${jvm_arguments}" 2048)
    java \
        -Xms${heap_min}m -Xmx${heap_max}m \
        -XX:+PreserveFramePointer \
        $(get_jvm_hugepage_settings $heap_max) \
        -XX:+HeapDumpOnOutOfMemoryError \
        -XX:HeapDumpPath="${VESPA_HOME}/var/crash" \
        -XX:+ExitOnOutOfMemoryError \
        --add-opens=java.base/java.io=ALL-UNNAMED \
        --add-opens=java.base/java.lang=ALL-UNNAMED \
        --add-opens=java.base/java.net=ALL-UNNAMED \
        --add-opens=java.base/java.nio=ALL-UNNAMED \
        --add-opens=java.base/jdk.internal.loader=ALL-UNNAMED \
        --add-opens=java.base/sun.security.ssl=ALL-UNNAMED  \
        -Dconfig_model.ip_check=false \
        -Djava.library.path="${VESPA_HOME}/lib64" \
        -Djava.security.properties=${VESPA_HOME}/conf/vespa/java.security.override \
        -Djava.awt.headless=true \
        -Dsun.rmi.dgc.client.gcInterval=3600000 \
        -Dsun.net.client.defaultConnectTimeout=5000 \
        -Dsun.net.client.defaultReadTimeout=60000 \
        -Djavax.net.ssl.keyStoreType=JKS \
        -Djdk.tls.rejectClientInitiatedRenegotiation=true \
        -Djdisc.config.file="$cfpfile" \
        -Djdisc.export.packages= \
        -Djdisc.cache.path="$bundlecachedir" \
        -Djdisc.bundle.path="${VESPA_HOME}/lib/jars" \
        -Djdisc.logger.enabled=false \
        -Djdisc.logger.level=WARNING \
        -Djdisc.logger.tag="$service" \
        -Dfile.encoding=UTF-8 \
        -cp "$CP" \
        "${jvm_arguments[@]}" \
        com.yahoo.jdisc.core.StandaloneMain standalone-container-jar-with-dependencies.jar &

    local pid="$!"
    echo "$pid" > "$pidfile"
}

Kill() {
    local force="$1"
    local expected_user="$2"
    local expected_comm="$3" # Executable name only
    local pid="$4"

    local -i now
    if ! now=$(date +%s); then
        Fail "Failed to get the current date in seconds since epoch"
    fi
    local -i timeout=$(( now + 300 ))

    local has_killed=false

    while true; do
        local ps_output=""
        if ! ps_output=$(ps -p "$pid" -o user= -o comm=); then
            # success
            return
        fi

        local user comm
        read -r user comm <<< "$ps_output"

        if test "$user" != "$expected_user"; then
            echo "Warning: Pid collision ($pid): Expected user $expected_user but found $user."
            echo "Will assume original process has died."
            return
        fi

        if test "$comm" != "$expected_comm"; then
            echo "Warning: Pid collision ($pid): Expected program $expected_comm but found $comm."
            echo "Will assume original process has died."
            return
        fi

        if ! "$has_killed"; then
            if $force; then
                if ! kill -KILL "$pid"; then
                    Fail "Failed to kill $pid"
                fi
            else
                if ! kill "$pid"; then
                    Fail "Failed to kill $pid"
                fi
            fi

            has_killed=true
        fi

        sleep 1

        now=$(date +%s)
        if (( now >= timeout )); then
            Fail "Process $pid still exists after $timeout seconds, giving up"
        fi
    done
}

StopCommand() {
    local user="$1"
    local service="$2"
    local force="$3"

    local pidfile="${VESPA_HOME}/var/run/$service.pid"
    if ! test -r "$pidfile"; then
        echo "$service is not running"
        return
    fi

    local pid=$(< "$pidfile")
    if ! [[ "$pid" =~ ^[0-9]+$ ]]; then
        Fail "Pid file '$pidfile' does not contain a valid pid: $pid"
    fi

    Kill "$force" "$user" java "$pid"
    rm -f "$pidfile"
}

Main() {
    if (( $# == 0 )); then
        Usage
    fi

    local command="$1"
    shift

    local service="standalone/container"
    local user="$VESPA_USER"
    local force=false
    local -a jvm_arguments=()

    while (( $# > 0 )); do
        case "$1" in
            --help|-h) Usage ;;
            --service|-s)
                service="$2"
                shift 2
                ;;
            --user|-u)
                user="$2"
                shift 2
                ;;
            --force|-f)
                force=true
                shift
                ;;
            --)
                shift
                jvm_arguments=("$@")
                break
                ;;
            *) break ;;
        esac
    done

    # Service name will be included in paths and possibly environment variable
    # names, so be restrictive.
    local service_regex='^[a-zA-Z0-9_-]+$'
    if test -z "$service"; then
        Fail "SERVICE not specified"
    elif ! [[ "$service" =~ $service_regex ]]; then
        Fail "Service must math the regex '$service_regex'"
    fi

    if ! getent passwd "$user" &> /dev/null; then
        Fail "Bad user ($user): not found in passwd"
    elif test "$(id -un)" != "$user"; then
        Fail "${0##*/} must be started by $user"
    fi

    case "$command" in
        help) Usage ;;
        start) StartCommand "$service" "$force" "${jvm_arguments[@]}" ;;
        stop) StopCommand "$user" "$service" "$force" "$@" ;;
        *) Fail "Unknown command '$command'" ;;
    esac
}

Main "$@"
