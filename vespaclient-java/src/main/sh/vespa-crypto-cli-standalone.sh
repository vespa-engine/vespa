#!/usr/bin/env sh
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Resolve symlink (if any) and normalize path
program=$(readlink -f "$0")
program_dir=$(dirname "$program")
jarfile=$(readlink -f "$program_dir"/../../../target/vespaclient-java-fat-with-provided.jar)

if ! test -e "$jarfile"
then
    echo "No such file: '$jarfile'" >&2
    exit 1
fi

exec java \
-Djava.awt.headless=true \
-Xms128m -Xmx2048m \
-cp "$jarfile" com.yahoo.vespa.security.tool.Main "$@"
