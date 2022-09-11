// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniperdfw.h"
#include "docsumstate.h"
#include "i_docsum_store_document.h"
#include "juniper_query_adapter.h"
#include <vespa/vespalib/objects/hexdump.h>
#include <vespa/juniper/config.h>
#include <vespa/juniper/result.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.dynamicteaserdfw");

namespace search::docsummary {


JuniperDFW::JuniperDFW(const juniper::Juniper * juniper)
    : _input_field_name(),
      _juniperConfig(),
      _juniper(juniper)
{
}


JuniperDFW::~JuniperDFW() = default;

bool
JuniperDFW::Init(
        const char *fieldName,
        const vespalib::string& inputField)
{
    bool rc = true;
    _juniperConfig = _juniper->CreateConfig(fieldName);
    if ( ! _juniperConfig) {
        LOG(warning, "could not create juniper config for field '%s'", fieldName);
        rc = false;
    }

    _input_field_name = inputField;
    return rc;
}

bool
JuniperTeaserDFW::Init(
        const char *fieldName,
        const vespalib::string& inputField)
{
    return JuniperDFW::Init(fieldName, inputField);
}

vespalib::string
DynamicTeaserDFW::makeDynamicTeaser(uint32_t docid, vespalib::stringref input, GetDocsumsState *state) const
{
    if (!state->_dynteaser._query) {
        JuniperQueryAdapter iq(state->_kwExtractor,
                               state->_args.getStackDump(),
                               &state->_args.highlightTerms());
        state->_dynteaser._query = _juniper->CreateQueryHandle(iq, nullptr);
    }

    LOG(debug, "makeDynamicTeaser: docid (%d)",
        docid);

    std::unique_ptr<juniper::Result> result;

    if (state->_dynteaser._query != nullptr) {

        if (LOG_WOULD_LOG(spam)) {
            std::ostringstream hexDump;
            hexDump << vespalib::HexDump(input.data(), input.length());
            LOG(spam, "makeDynamicTeaser: docid=%d, input='%s', hexdump:\n%s",
                docid, std::string(input.data(), input.length()).c_str(), hexDump.str().c_str());
        }

        auto langid = static_cast<uint32_t>(-1);

        result = juniper::Analyse(*_juniperConfig, *state->_dynteaser._query,
                                  input.data(), input.length(), docid, langid);
    }

    juniper::Summary *teaser = result
                               ? juniper::GetTeaser(*result, _juniperConfig.get())
                               : nullptr;

    if (LOG_WOULD_LOG(debug)) {
        std::ostringstream hexDump;
        if (teaser != nullptr) {
            hexDump << vespalib::HexDump(teaser->Text(), teaser->Length());
        }
        LOG(debug, "makeDynamicTeaser: docid=%d, teaser='%s', hexdump:\n%s",
            docid, (teaser != nullptr ? std::string(teaser->Text(), teaser->Length()).c_str() : "nullptr"),
            hexDump.str().c_str());
    }

    if (teaser != nullptr) {
        return {teaser->Text(), teaser->Length()};
    } else {
        return {};
    }
}

void
DynamicTeaserDFW::insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState *state, ResType,
                              vespalib::slime::Inserter &target) const
{
    if (doc != nullptr) {
        auto input = doc->get_juniper_input(_input_field_name);
        if (!input.empty()) {
            vespalib::string teaser = makeDynamicTeaser(docid, input.get_value(), state);
            vespalib::Memory value(teaser.c_str(), teaser.size());
            target.insertString(value);
        }
    }
}

}
