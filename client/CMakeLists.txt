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
install_symlink(libexec/vespa/script-utils bin/vespa-security-env)
install_symlink(libexec/vespa/script-utils bin/vespa-get-cluster-state)
install_symlink(libexec/vespa/script-utils bin/vespa-get-node-state)
install_symlink(libexec/vespa/script-utils bin/vespa-set-node-state)
install_symlink(libexec/vespa/script-utils bin/vespa-start-configserver)

install_symlink(libexec/vespa/script-utils bin/vespa-get-config)
install_symlink(libexec/vespa/script-utils bin/vespa-verify-ranksetup)
install_symlink(libexec/vespa/script-utils bin/vespa-config-status)
install_symlink(libexec/vespa/script-utils bin/vespa-configproxy-cmd)
install_symlink(libexec/vespa/script-utils bin/vespa-doclocator)
install_symlink(libexec/vespa/script-utils bin/vespa-model-inspect)
install_symlink(libexec/vespa/script-utils bin/vespa-proton-cmd)
install_symlink(libexec/vespa/script-utils bin/vespa-rpc-invoke)
install_symlink(libexec/vespa/script-utils bin/vespa-sentinel-cmd)
install_symlink(libexec/vespa/script-utils bin/vespa-route)
install_symlink(libexec/vespa/script-utils bin/vespa-transactionlog-inspect)

install_symlink(libexec/vespa/script-utils sbin/vespa-distributord)
install_symlink(libexec/vespa/script-utils sbin/vespa-proton)
install_symlink(libexec/vespa/script-utils sbin/vespa-storaged)
