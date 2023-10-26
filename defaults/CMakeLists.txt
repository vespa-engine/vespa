# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    LIBS
    src/vespa

    APPS
    src/apps/printdefault
)

# No separate java artifact is installed (part of config-bundle)
