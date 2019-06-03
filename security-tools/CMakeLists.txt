# Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(security-tools)

vespa_install_script(src/main/sh/vespa-security-env vespa-security-env bin)
vespa_install_script(src/main/sh/vespa-curl-wrapper vespa-curl-wrapper libexec/vespa)

