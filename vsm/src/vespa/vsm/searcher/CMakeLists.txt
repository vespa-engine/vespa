# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vsm_vsmsearcher OBJECT
    SOURCES
    boolfieldsearcher.cpp
    fieldsearcher.cpp
    floatfieldsearcher.cpp
    fold.cpp
    futf8strchrfieldsearcher.cpp
    intfieldsearcher.cpp
    strchrfieldsearcher.cpp
    utf8flexiblestringfieldsearcher.cpp
    utf8strchrfieldsearcher.cpp
    utf8stringfieldsearcherbase.cpp
    utf8substringsearcher.cpp
    utf8substringsnippetmodifier.cpp
    utf8suffixstringfieldsearcher.cpp
    utf8exactstringfieldsearcher.cpp
    DEPENDS
    vsm_vconfig
    AFTER
    vsm_vconfig
)
