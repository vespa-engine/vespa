# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_jar(security-tools-jar-with-dependencies.jar)

vespa_install_script(src/main/sh/vespa-curl-wrapper vespa-curl-wrapper libexec/vespa)
