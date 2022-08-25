// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "copy_dfw.h"
#include "i_docsum_store_document.h"
#include "resultclass.h"
#include "resultconfig.h"
#include <vespa/vespalib/data/slime/slime.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.copy_dfw");

namespace search::docsummary {

CopyDFW::CopyDFW()
    : _inputFieldEnumValue(static_cast<uint32_t>(-1)),
      _input_field_name()
{
}

CopyDFW::~CopyDFW() = default;

bool
CopyDFW::Init(const ResultConfig & config, const char *inputField)
{
    _inputFieldEnumValue = config.GetFieldNameEnum().Lookup(inputField);
    _input_field_name = inputField;

    if (_inputFieldEnumValue >= config.GetFieldNameEnum().GetNumEntries()) {
        LOG(warning, "no docsum format contains field '%s'; copied fields will be empty", inputField);
    }

    return true;
}

void
CopyDFW::insertField(uint32_t, const IDocsumStoreDocument* doc, GetDocsumsState *, ResType,
                     vespalib::slime::Inserter &target)
{
    if (doc != nullptr) {
        doc->insert_summary_field(_input_field_name, target);
    }
}

}
