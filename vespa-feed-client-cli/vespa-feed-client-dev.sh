#!/usr/bin/env sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# Resolve symlink (if any) and normalize path
program=$(readlink -f "$0")
program_dir=$(dirname "$program")

exec java \
-Djava.awt.headless=true \
-Xms128m -Xmx2048m \
-Djava.util.logging.config.file=$program_dir/src/main/resources/logging.properties \
-cp $program_dir/target/vespa-feed-client-cli-jar-with-dependencies.jar ai.vespa.feed.client.impl.CliClient "$@"
