#!/bin/bash -e
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
elif [ "$1" = "-h" ] || [ "$1" = "--help" ]; then
    usage
    exit 0
else
    echo "Unknown argument: $1" >&2
    usage
    exit 1
fi

get_env_var_with_optional_default() {
   local var_name=$1
   local default_val=$2
   eval "existing_value=\${$var_name}"
    if [[ -n $existing_value ]]; then
        echo "$existing_value"
    elif [[ -n $default_val ]]; then
        echo "$default_val"
    fi
}

readonly MAVEN_CMD=$(get_env_var_with_optional_default VESPA_MAVEN_COMMAND "$(pwd)/mvnw")

readonly MAVEN_EXTRA_OPTS=$(get_env_var_with_optional_default VESPA_MAVEN_EXTRA_OPTS)
echo "Using maven command: ${MAVEN_CMD}"
echo "Using maven extra opts: ${MAVEN_EXTRA_OPTS}"

mvn_install() {
    ${MAVEN_CMD} --batch-mode --no-snapshot-updates -Dmaven.wagon.http.retryHandler.count=5 clean install ${MAVEN_EXTRA_OPTS} "$@"
}

force_move() {
    local src_dir=$1
    local file=$2

    rm -rf "./${file:?}"
    cp -r "$src_dir/${file:?}" .
    rm -rf "$src_dir/${file:?}"
}

# Generate vtag map
top=$(dirname $0)
$top/dist/getversionmap.sh $top > $top/dist/vtag.map

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

# Set up maven wrapper.
echo "Setting up maven wrapper in $(pwd)"
mvn wrapper:wrapper -Dmaven=3.8.8 -f maven-plugins/pom.xml ${MAVEN_EXTRA_OPTS}
force_move maven-plugins .mvn
force_move maven-plugins mvnw
rm -f maven-plugins/mvnw.cmd
${MAVEN_CMD} -v

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
        mvn_install -am -Dmaven.test.skip=true -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -pl jrt,linguistics,messagebus
        ;;
    default)
	echo "Building default set of dependencies."
        ;;
esac
