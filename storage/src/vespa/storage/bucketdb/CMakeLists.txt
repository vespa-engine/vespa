# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_bucketdb OBJECT
    SOURCES
    bucketcopy.cpp
    bucketdatabase.cpp
    bucketinfo.cpp
    bucketmanager.cpp
    bucketmanagermetrics.cpp
    judyarray.cpp
    lockablemap.cpp
    mapbucketdatabase.cpp
    storagebucketdbinitializer.cpp
    storbucketdb.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
vespa_generate_config(storage_bucketdb stor-bucketdb.def)
install_config_definition(stor-bucketdb.def vespa.config.content.core.stor-bucketdb.def)
vespa_generate_config(storage_bucketdb stor-bucket-init.def)
install_config_definition(stor-bucket-init.def vespa.config.content.core.stor-bucket-init.def)
