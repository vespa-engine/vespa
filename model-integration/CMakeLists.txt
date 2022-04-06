# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(model-integration)

vespa_install_script(src/main/python/vespa-convert-tf2onnx.py vespa-convert-tf2onnx bin)

install(FILES src/main/config/model-integration.xml DESTINATION conf/configserver-app)