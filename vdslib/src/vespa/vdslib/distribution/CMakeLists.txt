# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vdslib_distribution OBJECT
    SOURCES
    bucket_space_distribution_configs.cpp
    distribution.cpp
    distribution_config_bundle.cpp
    distribution_config_util.cpp
    global_bucket_space_distribution_converter.cpp
    group.cpp
    redundancygroupdistribution.cpp
    DEPENDS
)
