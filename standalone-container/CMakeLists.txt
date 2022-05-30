# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_jar(standalone-container-jar-with-dependencies.jar)
install(PROGRAMS src/main/sh/standalone-container.sh DESTINATION libexec/vespa)
