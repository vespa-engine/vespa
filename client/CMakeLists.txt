# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set(GODIR ${CMAKE_CURRENT_SOURCE_DIR}/go)

file(GLOB_RECURSE GOSRCFILES ${GODIR}/*.go)

add_custom_command(OUTPUT ${GODIR}/bin/vespa ${GODIR}/bin/script-utils
                   COMMAND make
                   DEPENDS ${GODIR}/Makefile ${GOSRCFILES}
                   WORKING_DIRECTORY ${GODIR})

add_custom_target(client_go_binaries ALL DEPENDS ${GODIR}/bin/vespa ${GODIR}/bin/script-utils)

install(PROGRAMS ${GODIR}/bin/script-utils DESTINATION libexec/vespa)
install_symlink(libexec/vespa/script-utils bin/vespa-logfmt)
install_symlink(libexec/vespa/script-utils bin/vespa-deploy)
install_symlink(libexec/vespa/script-utils bin/vespa-get-cluster-state)
install_symlink(libexec/vespa/script-utils bin/vespa-get-node-state)
install_symlink(libexec/vespa/script-utils bin/vespa-set-node-state)
