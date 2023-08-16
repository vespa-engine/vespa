# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vdslib_distribution OBJECT
    SOURCES
    distribution.cpp
    distribution_config_util.cpp
    group.cpp
    redundancygroupdistribution.cpp
    DEPENDS
)
