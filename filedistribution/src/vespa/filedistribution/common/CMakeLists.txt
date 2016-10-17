# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(filedistribution_exceptionrethrower OBJECT
    SOURCES
    exception.cpp
    DEPENDS
)
vespa_add_library(filedistribution_common STATIC
    SOURCES
    componentsdeleter.cpp
    exception.cpp
    vespa_logfwd.cpp
    DEPENDS
)
target_compile_options(filedistribution_common PRIVATE -DTORRENT_DISABLE_ENCRYPTION -DTORRENT_DISABLE_DHT -DWITH_SHIPPED_GEOIP_H -DBOOST_ASIO_HASH_MAP_BUCKETS=1021 -DBOOST_EXCEPTION_DISABLE -DBOOST_ASIO_ENABLE_CANCELIO -DBOOST_ASIO_DYN_LINK -DTORRENT_LINKING_SHARED)
vespa_add_target_system_dependency(filedistribution_common boost boost_thread-mt-d)
