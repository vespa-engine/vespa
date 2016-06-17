# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_install_script(src/main/sh/cloudconfig_server-remove-state bin)
vespa_install_script(src/main/sh/start-filedistribution start-file libexec)
vespa_install_script(src/main/sh/ping-configserver libexec)
vespa_install_script(src/main/sh/start-configserver libexec)
vespa_install_script(src/main/sh/start-logd libexec)
vespa_install_script(src/main/sh/stop-configserver libexec)
