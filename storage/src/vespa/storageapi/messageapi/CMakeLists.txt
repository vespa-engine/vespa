# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storageapi_messageapi OBJECT
    SOURCES
    bucketcommand.cpp
    bucketreply.cpp
    bucketinfocommand.cpp
    bucketinforeply.cpp
    maintenancecommand.cpp
    returncode.cpp
    storagemessage.cpp
    storagecommand.cpp
    storagereply.cpp
    DEPENDS
)
