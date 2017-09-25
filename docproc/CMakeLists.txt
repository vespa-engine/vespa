# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install(FILES src/main/resources/configdefinitions/docproc.def RENAME config.docproc.docproc.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/schemamapping.def RENAME config.docproc.schemamapping.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
install(FILES src/main/resources/configdefinitions/splitter-joiner-document-processor.def RENAME config.docproc.splitter-joiner-document-processor.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)
