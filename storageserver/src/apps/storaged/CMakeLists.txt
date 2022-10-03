# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(storageserver_storaged_app
    SOURCES
    storage.cpp
    forcelink.cpp
    OUTPUT_NAME vespa-storaged-bin
    INSTALL sbin
    DEPENDS
    storageserver_storageapp
)

install_absolute_symlink(vespa-storaged-bin sbin/vespa-distributord-bin)
