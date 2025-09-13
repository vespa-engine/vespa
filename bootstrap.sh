#!/usr/bin/env bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -o errexit
set -o pipefail
set -o nounset

if [ -n "${DEBUG:-}" ]; then
    set -o xtrace
fi

usage() {
    echo "Usage: $0 [full | java | default]" >&2
}

if [ $# -eq 0 ]; then
    # Build minimal set of java modules required to run cmake
    MODE=default
elif [ "$1" = "full" ]; then
    # Build all java modules required by C++ testing
    MODE=full
elif [ "$1" = "java" ]; then
    # Build only plugins
    MODE=java
elif [ "$1" = "default" ]; then
    MODE=default
elif [ "$1" = "wrapper" ]; then
    MODE=wrapper
elif [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    usage
    exit 0
else
    echo "Unknown argument: $1" >&2
    usage
    exit 1
fi

get_env_var_with_optional_default() {
    local var_name=$1; shift
    local default_val=${1:-}; shift

    # Disable nounset as we are checking if the variable is set
    set +o nounset
    eval "existing_value=\${$var_name}"
    if [[ -n $existing_value ]]; then
        echo "$existing_value"
    elif [[ -n $default_val ]]; then
        echo "$default_val"
    fi
    set -o nounset
}

MAVEN_CMD=$(get_env_var_with_optional_default VESPA_MAVEN_COMMAND "$(pwd)/mvnw")
MAVEN_EXTRA_OPTS=$(get_env_var_with_optional_default VESPA_MAVEN_EXTRA_OPTS)
MAVEN_TARGET=$(get_env_var_with_optional_default VESPA_MAVEN_TARGET "install")
readonly MAVEN_CMD MAVEN_EXTRA_OPTS MAVEN_TARGET
echo "Using maven command: ${MAVEN_CMD}"
echo "Using maven extra opts: ${MAVEN_EXTRA_OPTS}"
echo "Using maven target: ${MAVEN_TARGET}"

# Set up maven wrapper.
echo "Setting up maven wrapper in $(pwd)"
# shellcheck disable=SC2086 # allow word splitting for maven extra opts
mvn -B wrapper:wrapper -Dmaven=3.9.9 -N ${MAVEN_EXTRA_OPTS}

# Proxy allowing you to put $(pwd)/maven-wrapper/bin first in PATH
# to redirect any plain "mvn" commands so they use the wrapper
wbdir=maven-wrapper/bin
rm -rf ${wbdir}
mkdir -p ${wbdir}
printf '#!/bin/sh\nexec %s/mvnw "$@"\n' "$(pwd)" > ${wbdir}/mvn
chmod +x ${wbdir}/mvn
unset wbdir

${MAVEN_CMD} -v

if [ "$MODE" = "wrapper" ]; then
    exit
fi

mvn_install() {
    # shellcheck disable=SC2086 # allow word splitting for maven extra opts
    ${MAVEN_CMD} --batch-mode --no-snapshot-updates -Dmaven.wagon.http.retryHandler.count=5 clean "${MAVEN_TARGET}" ${MAVEN_EXTRA_OPTS} "$@"
}

# Generate vtag map
top=$(dirname "$0")
"$top/dist/getversionmap.sh" "$top" > "$top/dist/vtag.map"

# NOTES ON BUILDING JAVA MODULES
#
# mvn is unable to resolve references to a plugin, if the same mvn
# program builds the plugin in the same reactor build.
#
# Therefore, we need to manually build all plugins first.
#
# The 'java' mode only builds the plugins.
# The 'default' mode also builds some modules needed by C++ code.
# The 'full' mode also builds modules needed by C++ tests.

# must install parent poms first:
echo "Downloading all dependencies. This may take a few minutes with an empty Maven cache."
(
  cd dependency-versions
  mvn_install
)
(
  cd container-dependency-versions
  mvn_install
)
(
  cd parent
  mvn_install
)
mvn_install -N

# and build plugins first:
echo "Building Vespa Maven plugins."
mvn_install -f maven-plugins/pom.xml

# now everything else should just work with normal maven dependency resolution:

case "$MODE" in
    java)
        ;;
    full)
	echo "Building full set of dependencies."
        mvn_install -am -T1C -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -pl jrt,linguistics,messagebus
        ;;
    default)
	echo "Building default set of dependencies."
        ;;
esac
