#!/usr/bin/env sh
# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

exec java \
-Djava.awt.headless=true \
-Xms128m -Xmx2048m \
--add-opens=java.base/sun.security.ssl=ALL-UNNAMED  \
-Djava.util.logging.config.file=logging.properties \
-cp vespa-feed-client-cli-jar-with-dependencies.jar ai.vespa.feed.client.CliClient "$@"
