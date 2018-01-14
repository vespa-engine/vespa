# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(config-model)

vespa_install_script(src/main/perl/vespa-deploy bin)
vespa_install_script(src/main/perl/vespa-expand-config.pl bin)
vespa_install_script(src/main/perl/vespa-replicate-log-stream bin)
vespa_install_script(src/main/sh/vespa-validate-application bin)

install(DIRECTORY src/main/resources/schema DESTINATION share/vespa PATTERN ".gitignore" EXCLUDE)
install(DIRECTORY src/main/resources/schema DESTINATION share/vespa/schema/version/6.x PATTERN ".gitignore" EXCLUDE)
