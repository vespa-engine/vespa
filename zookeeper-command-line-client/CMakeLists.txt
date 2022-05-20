# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_jar(zookeeper-command-line-client-jar-with-dependencies.jar)

vespa_install_script(src/main/sh/vespa-zkcat vespa-zkcat bin)
vespa_install_script(src/main/sh/vespa-zkcli vespa-zkcli bin)
vespa_install_script(src/main/sh/vespa-zkctl vespa-zkctl bin)
vespa_install_script(src/main/sh/vespa-zkflw vespa-zkflw bin)
vespa_install_script(src/main/sh/vespa-zkls vespa-zkls bin)
vespa_install_script(src/main/sh/vespa-zktxnlog vespa-zktxnlog bin)
