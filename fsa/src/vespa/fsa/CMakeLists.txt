# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespafsa
    SOURCES
    automaton.cpp
    base64.cpp
    blob.cpp
    conceptnet.cpp
    detector.cpp
    fsa.cpp
    metadata.cpp
    ngram.cpp
    permuter.cpp
    segmenter.cpp
    selector.cpp
    unicode.cpp
    unicode_charprops.cpp
    unicode_lowercase.cpp
    unicode_tables.cpp
    vectorizer.cpp
    wordchartokenizer.cpp
    INSTALL lib64
    DEPENDS
)

install(FILES
    automaton.h
    base64.h
    blob.h
    checksum.h
    conceptnet.h
    detector.h
    file.h
    fsa.h
    metadata.h
    ngram.h
    permuter.h
    segmenter.h
    selector.h
    timestamp.h
    tokenizer.h
    unicode.h
    vectorizer.h
    wordchartokenizer.h
    DESTINATION include/vespa/fsa)
