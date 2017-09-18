# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install(CODE "execute_process(COMMAND ${CMAKE_CURRENT_LIST_DIR}/post_install.sh ${CMAKE_INSTALL_PREFIX})")
