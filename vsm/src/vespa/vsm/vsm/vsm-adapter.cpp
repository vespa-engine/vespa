// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vsm-adapter.h"
#include "docsumconfig.h"

#include <vespa/log/log.h>
LOG_SETUP(".vsm.vsm-adapter");

using search::docsummary::ResConfigEntry;
using search::docsummary::KeywordExtractor;
using config::ConfigSnapshot;

namespace vsm {

GetDocsumsStateCallback::GetDocsumsStateCallback() :
    _summaryFeatures(),
    _rankFeatures()
{ }

void GetDocsumsStateCallback::FillSummaryFeatures(GetDocsumsState * state, IDocsumEnvironment * env)
{
    (void) env;
    if (_summaryFeatures.get() != NULL) { // set the summary features to write to the docsum
        state->_summaryFeatures = _summaryFeatures;
        state->_summaryFeaturesCached = true;
    }
}

void GetDocsumsStateCallback::FillRankFeatures(GetDocsumsState * state, IDocsumEnvironment * env)
{
    (void) env;
    if (_rankFeatures.get() != NULL) { // set the rank features to write to the docsum
        state->_rankFeatures = _rankFeatures;
    }
}

void GetDocsumsStateCallback::ParseLocation(GetDocsumsState *state)
{
    (void) state;
}

void GetDocsumsStateCallback::FillDocumentLocations(GetDocsumsState *state, IDocsumEnvironment * env)
{
    (void) state;
    (void) env;
}


GetDocsumsStateCallback::~GetDocsumsStateCallback() = default;

DocsumTools::FieldSpec::FieldSpec() :
    _outputName(),
    _inputNames(),
    _command(VsmsummaryConfig::Fieldmap::NONE)
{ }

DocsumTools::FieldSpec::~FieldSpec() = default;

DocsumTools::DocsumTools(std::unique_ptr<DynamicDocsumWriter> writer) :
    _writer(std::move(writer)),
    _juniper(),
    _resultClass(),
    _fieldSpecs()
{ }


DocsumTools::~DocsumTools() = default;

bool
DocsumTools::obtainFieldNames(const FastS_VsmsummaryHandle &cfg)
{
    uint32_t defaultSummaryId = getResultConfig()->LookupResultClassId(cfg->outputclass);
    _resultClass = getResultConfig()->LookupResultClass(defaultSummaryId);
    if (_resultClass != NULL) {
        for (uint32_t i = 0; i < _resultClass->GetNumEntries(); ++i) {
            const ResConfigEntry * entry = _resultClass->GetEntry(i);
            _fieldSpecs.push_back(FieldSpec());
            _fieldSpecs.back().setOutputName(entry->_bindname);
            bool found = false;
            if (cfg) {
                // check if we have this summary field in the vsmsummary config
                for (uint32_t j = 0; j < cfg->fieldmap.size() && !found; ++j) {
                    if (entry->_bindname == cfg->fieldmap[j].summary.c_str()) {
                        for (uint32_t k = 0; k < cfg->fieldmap[j].document.size(); ++k) {
                            _fieldSpecs.back().getInputNames().push_back(cfg->fieldmap[j].document[k].field);
                        }
                        _fieldSpecs.back().setCommand(cfg->fieldmap[j].command);
                        found = true;
                    }
                }
            }
            if (!found) {
                // use yourself as input
                _fieldSpecs.back().getInputNames().push_back(entry->_bindname);
            }
        }
    } else {
        LOG(warning, "could not locate result class: '%s'", cfg->outputclass.c_str());
    }
    return true;
}

void
VSMAdapter::configure(const VSMConfigSnapshot & snapshot)
{
    vespalib::LockGuard guard(_lock);
    LOG(debug, "(re-)configure VSM (docsum tools)");

    std::shared_ptr<SummaryConfig>      summary(snapshot.getConfig<SummaryConfig>().release());
    std::shared_ptr<SummarymapConfig>   summaryMap(snapshot.getConfig<SummarymapConfig>().release());
    std::shared_ptr<VsmsummaryConfig>   vsmSummary(snapshot.getConfig<VsmsummaryConfig>().release());
    std::shared_ptr<JuniperrcConfig>    juniperrc(snapshot.getConfig<JuniperrcConfig>().release());

    _fieldsCfg.set(snapshot.getConfig<VsmfieldsConfig>().release());
    _fieldsCfg.latch();

    LOG(debug, "configureFields(): Size of cfg fieldspec: %zd", _fieldsCfg.get()->fieldspec.size()); // UlfC: debugging
    LOG(debug, "configureFields(): Size of cfg documenttype: %zd", _fieldsCfg.get()->documenttype.size()); // UlfC: debugging
    LOG(debug, "configureSummary(): Size of cfg classes: %zd", summary->classes.size()); // UlfC: debugging
    LOG(debug, "configureSummaryMap(): Size of cfg override: %zd", summaryMap->override.size()); // UlfC: debugging
    LOG(debug, "configureVsmSummary(): Size of cfg fieldmap: %zd", vsmSummary->fieldmap.size()); // UlfC: debugging
    LOG(debug, "configureVsmSummary(): outputclass='%s'", vsmSummary->outputclass.c_str()); // UlfC: debugging

    // init result config
    std::unique_ptr<ResultConfig> resCfg(new ResultConfig());
    if ( ! resCfg->ReadConfig(*summary.get(), _configId.c_str())) {
        throw std::runtime_error("(re-)configuration of VSM (docsum tools) failed due to bad summary config");
    }

    // init keyword extractor
    std::unique_ptr<KeywordExtractor> kwExtractor(new KeywordExtractor(NULL));
    kwExtractor->AddLegalIndexSpec(_highlightindexes.c_str());
    vespalib::string spec = kwExtractor->GetLegalIndexSpec();
    LOG(debug, "index highlight spec: '%s'", spec.c_str());

    // create dynamic docsum writer
    std::unique_ptr<DynamicDocsumWriter>
        writer(new DynamicDocsumWriter(resCfg.release(), kwExtractor.release()));

    // configure juniper (used when configuring DynamicDocsumConfig)
    std::unique_ptr<juniper::Juniper> juniper;
    {
        _juniperProps.reset(new JuniperProperties(*juniperrc));
        juniper.reset(new juniper::Juniper(_juniperProps.get(), &_wordFolder));
    }

    // create new docsum tools
    std::unique_ptr<DocsumTools> docsumTools(new DocsumTools(std::move(writer)));
    docsumTools->setJuniper(std::move(juniper));

    // configure dynamic docsum writer
    DynamicDocsumConfig dynDocsumConfig(docsumTools.get(), docsumTools->getDocsumWriter());
    dynDocsumConfig.configure(*summaryMap.get());

    // configure new docsum tools
    if (docsumTools->obtainFieldNames(vsmSummary)) {
        // latch new docsum tools into production
        _docsumTools.set(docsumTools.release());
        _docsumTools.latch();
    } else {
        throw std::runtime_error("(re-)configuration of VSM (docsum tools) failed");
    }
}

VSMAdapter::VSMAdapter(const vespalib::string & highlightindexes, const vespalib::string & configId, Fast_WordFolder & wordFolder)
    : _highlightindexes(highlightindexes),
      _configId(configId),
      _wordFolder(wordFolder),
      _fieldsCfg(),
      _docsumTools(),
      _juniperProps(),
      _lock()
{
}


VSMAdapter::~VSMAdapter() = default;

}
