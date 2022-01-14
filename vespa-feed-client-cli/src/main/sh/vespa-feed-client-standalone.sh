#!/usr/bin/env sh
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

exec java \
-Djava.awt.headless=true \
-Xms128m -Xmx2048m \
-Djava.util.logging.config.file=`dirname $0`/logging.properties \
-cp `dirname $0`/vespa-feed-client-cli-jar-with-dependencies.jar ai.vespa.feed.client.impl.CliClient "$@"
