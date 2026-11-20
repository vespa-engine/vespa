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

# Locate the vespa source directory, often ~/git/vespa
top=$(dirname "$0")

# The build modes use relative paths from the source root, so check
# this before touching anything (e.g. mvnw) in the current directory.
# The 'wrapper' mode sets up mvnw in the current directory and is exempt.
if [ "$MODE" != "wrapper" ]; then
    here=$(/bin/pwd || pwd )
    there=$(cd "$top" && { /bin/pwd || pwd; })
    if [ "$here" != "$there" ]; then
        echo "Full bootstrap.sh must be run in the top-level vespa directory $there" 1>&2
        exit 1
    fi
fi

# Read a required property from mvnvm.properties, which is the single
# source of truth for the Maven version (also used by mvnvm).
get_mvnvm_property() {
    local prop_file="${top}/mvnvm.properties"
    local key=$1 value
    if [ -f "${prop_file}" ]; then
        value=$(sed -n "s/^${key}=//p" "${prop_file}" | tail -n 1)
    fi
    if [ -z "${value:-}" ]; then
        echo "Could not find ${key} in ${prop_file}" >&2
        exit 1
    fi
    echo "$value"
}

get_env_var_with_optional_default() {
    local var_name=$1; shift
    local default_val=${1:-};

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

wanted_mvn_version=$(get_mvnvm_property mvn_version)
echo "Using maven version: ${wanted_mvn_version}"
# Read the checksum up front, so a missing property fails before the
# existing wrapper is touched.
distribution_sha256=$(get_mvnvm_property mvn_distribution_sha256)

current_mvn_version=$(/usr/bin/mvn -version 2>/dev/null | awk '$1 == "Apache" && $2 == "Maven" { print $3; exit }' || true)
if [ "$current_mvn_version" = "$wanted_mvn_version" ]; then
    # we already have the correct version installed, skip the wrapper
    ln -sf /usr/bin/mvn mvnw
else
    rm -f mvnw
    # Set up maven wrapper.
    echo "Setting up maven wrapper ${wanted_mvn_version} in $(pwd)"
    maven_wrapper_args=(-B)
    if [[ -n "${MAVEN_GLOBAL_SETTINGS:-}" ]]; then
        maven_wrapper_args=(-gs "$MAVEN_GLOBAL_SETTINGS" -B)
    fi
    # shellcheck disable=SC2086 # allow word splitting for maven extra opts
    mvn "${maven_wrapper_args[@]}" wrapper:wrapper -Dmaven="${wanted_mvn_version}" -N ${MAVEN_EXTRA_OPTS}

    # Keep verification independent of the mirror used to fetch the wrapper.
    wrapper_properties=.mvn/wrapper/maven-wrapper.properties
    if grep -q '^distributionSha256Sum=' "$wrapper_properties"; then
        grep -qx "distributionSha256Sum=$distribution_sha256" "$wrapper_properties" || {
            echo "Unexpected Maven wrapper distribution checksum" >&2
            exit 1
        }
    else
        printf '\ndistributionSha256Sum=%s\n' "$distribution_sha256" >> "$wrapper_properties"
    fi

    # Proxy allowing you to put $(pwd)/maven-wrapper/bin first in PATH
    # to redirect any plain "mvn" commands so they use the wrapper
    wbdir=maven-wrapper/bin
    rm -rf ${wbdir}
    mkdir -p ${wbdir}
    printf '#!/bin/sh\nexec %s/mvnw "$@"\n' "$(pwd)" > ${wbdir}/mvn
    chmod +x ${wbdir}/mvn
    unset wbdir
fi

${MAVEN_CMD} -v

if [ "$MODE" = "wrapper" ]; then
    exit
fi

mvn_install() {
    # shellcheck disable=SC2086 # allow word splitting for maven extra opts
    ${MAVEN_CMD} --batch-mode --no-snapshot-updates -Dmaven.wagon.http.retryHandler.count=5 clean "${MAVEN_TARGET}" ${MAVEN_EXTRA_OPTS} "$@"
}

# Generate vtag map
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
