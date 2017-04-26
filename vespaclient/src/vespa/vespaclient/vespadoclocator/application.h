// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "locator.h"
#include <vespa/fastos/app.h>

class Application : public FastOS_Application {
private:
    /**
     * Locates and outputs the whereabouts of the given document id. If there is a problem parsing the given
     * document identifier, this method returns false.
     *
     * @param locator The locator to use.
     * @param docId   The document to locate.
     * @return True if the document was located.
     */
    bool printDocumentLocation(Locator &locator, const std::string &docId);

public:
    int Main() override;
};

