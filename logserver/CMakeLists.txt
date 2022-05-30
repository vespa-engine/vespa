# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_jar(logserver-jar-with-dependencies.jar)

vespa_install_script(bin/logserver-start.sh vespa-logserver-start bin)
