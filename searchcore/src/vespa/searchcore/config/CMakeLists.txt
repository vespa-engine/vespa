# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_fconfig STATIC
    SOURCES
    DEPENDS
)
vespa_generate_config(searchcore_fconfig vespa.config.search.core.fdispatchrc.def)
vespa_generate_config(searchcore_fconfig vespa.config.search.core.proton.def)
vespa_generate_config(searchcore_fconfig vespa.config.search.core.ranking-constants.def)
vespa_generate_config(searchcore_fconfig vespa.config.search.core.onnx-models.def)
vespa_generate_config(searchcore_fconfig vespa.config.search.core.hwinfo.def)

install_config_definitions(src/vespa/searchcore/config)
