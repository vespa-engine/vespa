# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespadefaults
    fastos

    LIBS
    src/vespa/log

    APPS
    src/logctl
    src/logger

    TESTS
    src/test
    src/test/simple
    src/test/threads
)

vespa_install_script(src/logfmt/logfmt.pl logfmt bin)
install(FILES src/logfmt/logfmt.1 DESTINATION man/man1)
