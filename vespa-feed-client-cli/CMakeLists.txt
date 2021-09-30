# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(vespa-feed-client-cli)

vespa_install_script(src/main/sh/vespa-feed-client.sh vespa-feed-client bin)
install(FILES src/main/resources/logging.properties DESTINATION conf/vespa-feed-client)
