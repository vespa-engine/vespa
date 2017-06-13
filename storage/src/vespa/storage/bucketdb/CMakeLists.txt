# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_bucketdb OBJECT
    SOURCES
    bucketcopy.cpp
    bucketdatabase.cpp
    bucketinfo.cpp
    bucketmanager.cpp
    distribution_hash_normalizer.cpp
    judyarray.cpp
    mapbucketdatabase.cpp
    lockablemap.cpp
    storagebucketdbinitializer.cpp
    storbucketdb.cpp
    bucketmanagermetrics.cpp
    DEPENDS
    AFTER
    storage_storageconfig
)
vespa_generate_config(storage_bucketdb stor-bucketdb.def)
install(FILES stor-bucketdb.def DESTINATION var/db/vespa/config_server/serverdb/classes)
vespa_generate_config(storage_bucketdb stor-bucket-init.def)
install(FILES stor-bucket-init.def DESTINATION var/db/vespa/config_server/serverdb/classes)
