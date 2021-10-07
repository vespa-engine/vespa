# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_fconfig STATIC
    SOURCES
    DEPENDS
)
vespa_generate_config(searchcore_fconfig fdispatchrc.def)
install_config_definition(fdispatchrc.def vespa.config.search.core.fdispatchrc.def)
vespa_generate_config(searchcore_fconfig proton.def)
install_config_definition(proton.def vespa.config.search.core.proton.def)
vespa_generate_config(searchcore_fconfig ranking-constants.def)
install_config_definition(ranking-constants.def vespa.config.search.core.ranking-constants.def)
vespa_generate_config(searchcore_fconfig ranking-expressions.def)
install_config_definition(ranking-expressions.def vespa.config.search.core.ranking-expressions.def)
vespa_generate_config(searchcore_fconfig onnx-models.def)
install_config_definition(onnx-models.def vespa.config.search.core.onnx-models.def)
vespa_generate_config(searchcore_fconfig hwinfo.def)
