// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "juniperdfw.h"
#include "docsumstate.h"
#include "i_docsum_store_document.h"
#include "i_juniper_converter.h"
#include "i_query_term_filter_factory.h"
#include "juniper_query_adapter.h"
#include <vespa/document/fieldvalue/stringfieldvalue.h>
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
      _juniper(juniper),
      _query_term_filter()
{
}


JuniperDFW::~JuniperDFW() = default;

bool
JuniperDFW::Init(
        const char *fieldName,
        const vespalib::string& inputField,
        const IQueryTermFilterFactory& query_term_filter_factory)
{
    bool rc = true;
    _juniperConfig = _juniper->CreateConfig(fieldName);
    if ( ! _juniperConfig) {
        LOG(warning, "could not create juniper config for field '%s'", fieldName);
        rc = false;
    }

    _input_field_name = inputField;
    _query_term_filter = query_term_filter_factory.make(_input_field_name);
    return rc;
}

bool
JuniperTeaserDFW::Init(
        const char *fieldName,
        const vespalib::string& inputField,
        const IQueryTermFilterFactory& query_term_filter_factory)
{
    return JuniperDFW::Init(fieldName, inputField, query_term_filter_factory);
}

void
DynamicTeaserDFW::insert_juniper_field(uint32_t docid, vespalib::stringref input, GetDocsumsState& state, vespalib::slime::Inserter& inserter) const
{
    auto& query = state._dynteaser.get_query(_input_field_name);
    if (!query) {
        JuniperQueryAdapter iq(_query_term_filter.get(),
                               state._args.getStackDump(),
                               &state._args.highlightTerms());
        query = _juniper->CreateQueryHandle(iq, nullptr);
    }

    LOG(debug, "makeDynamicTeaser: docid (%d)",
        docid);

    std::unique_ptr<juniper::Result> result;

    if (query) {

        if (LOG_WOULD_LOG(spam)) {
            std::ostringstream hexDump;
            hexDump << vespalib::HexDump(input.data(), input.length());
            LOG(spam, "makeDynamicTeaser: docid=%d, input='%s', hexdump:\n%s",
                docid, std::string(input.data(), input.length()).c_str(), hexDump.str().c_str());
        }

        auto langid = static_cast<uint32_t>(-1);

        result = juniper::Analyse(*_juniperConfig, *query,
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
        inserter.insertString({teaser->Text(), teaser->Length()});
    } else {
        inserter.insertString({});
    }
}

namespace {

class JuniperConverter : public IJuniperConverter
{
    const DynamicTeaserDFW& _writer;
    uint32_t          _doc_id;
    GetDocsumsState&   _state;

public:
    JuniperConverter(const DynamicTeaserDFW& writer, uint32_t doc_id, GetDocsumsState& state);
    ~JuniperConverter() override;
    void convert(vespalib::stringref input, vespalib::slime::Inserter& inserter) override;
};

JuniperConverter::JuniperConverter(const DynamicTeaserDFW& writer, uint32_t doc_id, GetDocsumsState& state)
    : IJuniperConverter(),
      _writer(writer),
      _doc_id(doc_id),
      _state(state)
{
}

JuniperConverter::~JuniperConverter() = default;

void
JuniperConverter::convert(vespalib::stringref input, vespalib::slime::Inserter& inserter)
{
    _writer.insert_juniper_field(_doc_id, input, _state, inserter);
}

}

void
DynamicTeaserDFW::insertField(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                              vespalib::slime::Inserter &target) const
{
    if (doc != nullptr) {
        JuniperConverter converter(*this, docid, state);
        doc->insert_juniper_field(_input_field_name, target, converter);
    }
}

}
