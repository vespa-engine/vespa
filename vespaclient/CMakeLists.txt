# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespadefaults
    fastos
    configdefinitions
    config_cloudconfig
    vespalog
    document
    documentapi
    vespalib
    staging_vespalib

    LIBS
    src/vespa/vespaclient/clusterlist

    APPS
    src/vespa/vespaclient/vdsstates
    src/vespa/vespaclient/vesparoute
)

vespa_install_script(src/sh/vespa-set-node-state vespa-set-node-state bin)
vespa_install_script(src/sh/vespa-get-node-state vespa-get-node-state bin)
vespa_install_script(src/sh/vespa-get-cluster-state vespa-get-cluster-state bin)

vespa_install_script(src/perl/bin/SetNodeState.pl libexec/vespa)
vespa_install_script(src/perl/bin/GetNodeState.pl libexec/vespa)
vespa_install_script(src/perl/bin/GetClusterState.pl libexec/vespa)

install(DIRECTORY src/perl/lib/Yahoo/Vespa/
        DESTINATION lib/perl5/site_perl/Yahoo/Vespa
        FILES_MATCHING
        PATTERN "*.pm")

install(DIRECTORY src/perl/lib/Yahoo/Vespa/Bin/
        DESTINATION lib/perl5/site_perl/Yahoo/Vespa/Bin
        FILES_MATCHING
        PATTERN "*.pm")
