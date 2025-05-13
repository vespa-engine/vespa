// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "copy_dfw.h"
#include "i_docsum_store_document.h"
#include <vespa/vespalib/data/slime/slime.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.copy_dfw");

namespace search::docsummary {

CopyDFW::CopyDFW(const std::string& inputField)
    : _input_field_name(inputField)
{
}

CopyDFW::~CopyDFW() = default;

void
CopyDFW::insert_field(uint32_t, const IDocsumStoreDocument* doc, GetDocsumsState&,
                      const SummaryElementsSelector& elements_selector,
                      vespalib::slime::Inserter &target) const
{
    (void) elements_selector;
    if (doc != nullptr) {
        doc->insert_summary_field(_input_field_name, target);
    }
}

}
