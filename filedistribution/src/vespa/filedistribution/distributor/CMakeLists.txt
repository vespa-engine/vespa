# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(filedistribution_distributor STATIC
    SOURCES
    filedistributortrackerimpl.cpp
    filedownloader.cpp
    filedownloadermanager.cpp
    hostname.cpp
    scheduler.cpp
    signalhandling.cpp
    state_server_impl.cpp
    DEPENDS
)
target_compile_options(filedistribution_distributor PRIVATE -DTORRENT_DISABLE_ENCRYPTION -DTORRENT_DISABLE_DHT -DWITH_SHIPPED_GEOIP_H -DBOOST_ASIO_HASH_MAP_BUCKETS=1021 -DBOOST_EXCEPTION_DISABLE -DBOOST_ASIO_ENABLE_CANCELIO -DBOOST_ASIO_DYN_LINK -DTORRENT_LINKING_SHARED)
vespa_generate_config(filedistribution_distributor filedistributor.def)
install(FILES filedistributor.def DESTINATION var/db/vespa/config_server/serverdb/classes)
