# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

set(GODIR ${CMAKE_CURRENT_SOURCE_DIR}/../client/go)

file(GLOB_RECURSE GOSRCFILES ${GODIR}/*.go)

add_custom_command(OUTPUT ${GODIR}/bin/vespa-logfmt
                   COMMAND make
                   DEPENDS ${GODIR}/Makefile ${GOSRCFILES}
                   WORKING_DIRECTORY ${GODIR})

add_custom_target(vespalog_logfmt ALL DEPENDS ${GODIR}/bin/vespa-logfmt)

install(PROGRAMS ${GODIR}/bin/vespa-logfmt DESTINATION bin)
install(FILES src/vespa-logfmt/vespa-logfmt.1 DESTINATION man/man1)
install(DIRECTORY DESTINATION var/db/vespa/logcontrol)
