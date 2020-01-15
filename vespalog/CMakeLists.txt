# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespadefaults

    LIBS
    src/vespa/log

    APPS
    src/logctl
    src/logger

    TESTS
    src/test
    src/test/log_message
    src/test/simple
    src/test/threads
)

vespa_install_script(src/vespa-logfmt/vespa-logfmt.pl vespa-logfmt bin)
install(FILES src/vespa-logfmt/vespa-logfmt.1 DESTINATION man/man1)
install(DIRECTORY DESTINATION var/db/vespa/logcontrol)
