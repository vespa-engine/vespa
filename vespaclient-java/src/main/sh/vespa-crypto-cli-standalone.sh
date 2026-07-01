#!/bin/bash
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Resolve symlink (if any) and normalize path
program=$(readlink -f "$0")
program_dir=$(dirname "$program")

jar_name=vespaclient-java-fat-with-provided.jar

# Locate the fat JAR, in priority order:
#   1. $VESPA_CRYPTO_CLI_JAR, if set (explicit override)
#   2. installed location, relative to this script (RPM: bin/ and lib/jars/ are siblings)
#   3. target/ in the Maven build tree (local dev)
jarfile=""
if [ -n "$VESPA_CRYPTO_CLI_JAR" ]; then
    jarfile=$VESPA_CRYPTO_CLI_JAR
else
    for candidate in \
        "$program_dir/../lib/jars/$jar_name" \
        "$program_dir/../../../target/$jar_name"
    do
        if [ -e "$candidate" ]; then
            jarfile=$(readlink -f "$candidate")
            break
        fi
    done
fi

if [ -z "$jarfile" ] || ! test -e "$jarfile"
then
    if [ -n "$VESPA_CRYPTO_CLI_JAR" ]; then
        echo "VESPA_CRYPTO_CLI_JAR is set to '$VESPA_CRYPTO_CLI_JAR' but no such file exists." >&2
    else
        echo "Could not locate '$jar_name'. Set VESPA_CRYPTO_CLI_JAR to its path." >&2
    fi
    exit 1
fi

exec java \
-Djava.awt.headless=true \
-Xms128m -Xmx2048m \
-cp "$jarfile" com.yahoo.vespa.security.tool.Main "$@"
