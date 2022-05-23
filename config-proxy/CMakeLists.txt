# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_jar(config-proxy-jar-with-dependencies.jar)

vespa_install_script(src/main/sh/vespa-config-ctl.sh vespa-config-ctl bin)
vespa_install_script(src/main/sh/vespa-config-loadtester.sh vespa-config-loadtester bin)
vespa_install_script(src/main/sh/vespa-config-verification.sh vespa-config-verification bin)
