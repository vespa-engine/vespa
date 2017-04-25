// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::DocumentSelection
 * \ingroup spi
 *
 * \brief
 */

#pragma once

#include "types.h"

namespace storage {
namespace spi {

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
}
