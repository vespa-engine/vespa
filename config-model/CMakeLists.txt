# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_jar(config-model-jar-with-dependencies.jar)

install(DIRECTORY src/main/resources/schema DESTINATION share/vespa PATTERN ".gitignore" EXCLUDE PATTERN "version" EXCLUDE)
install(DIRECTORY src/main/resources/schema DESTINATION share/vespa/schema/version/8.x PATTERN ".gitignore" EXCLUDE PATTERN "version" EXCLUDE)
