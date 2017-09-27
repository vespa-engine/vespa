# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_install_script(src/main/sh/vespa-configserver-remove-state bin)
vespa_install_script(src/main/sh/start-filedistribution libexec/vespa)
vespa_install_script(src/main/sh/ping-configserver libexec/vespa)
vespa_install_script(src/main/sh/start-configserver libexec/vespa)
vespa_install_script(src/main/sh/start-logd libexec/vespa)
vespa_install_script(src/main/sh/stop-configserver libexec/vespa)
install(DIRECTORY src/main/resources/logd DESTINATION conf)
install(DIRECTORY src/main/resources/configserver-app DESTINATION conf)
