# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(zkfacade)

vespa_install_script(src/main/sh/zkcat vespa-zkcat bin)
vespa_install_script(src/main/sh/zkcli vespa-zkcli bin)
vespa_install_script(src/main/sh/zkctl vespa-zkctl bin)
vespa_install_script(src/main/sh/zkls vespa-zkls bin)
vespa_install_script(src/main/sh/zk-purge-old-snapshots vespa-zk-purge-old-snapshots bin)
