# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set(GODIR ${CMAKE_CURRENT_SOURCE_DIR}/go)

file(GLOB_RECURSE GOSRCFILES ${GODIR}/*.go)

add_custom_command(OUTPUT ${GODIR}/bin/vespa ${GODIR}/bin/vespa-wrapper
                   COMMAND make
                   DEPENDS ${GODIR}/Makefile ${GOSRCFILES}
                   WORKING_DIRECTORY ${GODIR})

add_custom_target(client_go_binaries ALL DEPENDS ${GODIR}/bin/vespa ${GODIR}/bin/vespa-wrapper)

install(PROGRAMS ${GODIR}/bin/vespa DESTINATION bin)
install(PROGRAMS ${GODIR}/bin/vespa-wrapper DESTINATION libexec/vespa)

install_symlink(libexec/vespa/vespa-wrapper bin/vespa-logfmt)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-deploy)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-security-env)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-get-cluster-state)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-get-node-state)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-set-node-state)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-start-configserver)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-start-services bin)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-stop-services bin)

install_symlink(libexec/vespa/vespa-wrapper bin/vespa-get-config)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-verify-ranksetup)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-config-status)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-configproxy-cmd)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-doclocator)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-model-inspect)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-proton-cmd)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-rpc-invoke)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-sentinel-cmd)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-route)
install_symlink(libexec/vespa/vespa-wrapper bin/vespa-transactionlog-inspect)

install_symlink(libexec/vespa/vespa-wrapper sbin/vespa-distributord)
install_symlink(libexec/vespa/vespa-wrapper sbin/vespa-proton)
install_symlink(libexec/vespa/vespa-wrapper sbin/vespa-storaged)
