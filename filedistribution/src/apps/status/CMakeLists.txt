# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(filedistribution_status-filedistribution_app
    SOURCES
    status-filedistribution.cpp
    INSTALL bin
    OUTPUT_NAME vespa-status-filedistribution-bin
    DEPENDS
    filedistribution_filedistributionmodel
    filedistribution_common
)
target_compile_options(filedistribution_status-filedistribution_app PRIVATE -DTORRENT_DISABLE_ENCRYPTION -DTORRENT_DISABLE_DHT -DWITH_SHIPPED_GEOIP_H -DBOOST_ASIO_HASH_MAP_BUCKETS=1021 -DBOOST_EXCEPTION_DISABLE -DBOOST_ASIO_ENABLE_CANCELIO -DBOOST_ASIO_DYN_LINK -DTORRENT_LINKING_SHARED)
vespa_add_target_system_dependency(filedistribution_status-filedistribution_app boost boost_system-mt-d)
vespa_add_target_system_dependency(filedistribution_status-filedistribution_app boost boost_thread-mt-d)
vespa_add_target_system_dependency(filedistribution_status-filedistribution_app boost boost_program_options-mt-d)
vespa_add_target_system_dependency(filedistribution_status-filedistribution_app boost boost_filesystem-mt-d)
vespa_install_script(vespa-status-filedistribution.sh vespa-status-filedistribution bin)
