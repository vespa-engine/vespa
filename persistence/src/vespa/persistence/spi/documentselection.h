// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::DocumentSelection
 * \ingroup spi
 *
 * \brief
 */

#pragma once

#include <persistence/spi/types.h>

namespace storage::spi {

class DocumentSelection
{
    vespalib::string _documentSelection;
 public:
    explicit DocumentSelection(const vespalib::string& docSel)
        : _documentSelection(docSel) {}

    bool match(const document::Document&) const { return true; }

    const vespalib::string& getDocumentSelection() const {
        return _documentSelection;
    }
};

}
