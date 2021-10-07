// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/base.h>
#include <vespa/config/retriever/configsnapshot.h>
#include <vespa/vsm/config/vsm-cfif.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>
#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/idocsumenvironment.h>
#include <vespa/juniper/rpinterface.h>

using search::docsummary::ResultConfig;
using search::docsummary::ResultClass;
using search::docsummary::IDocsumWriter;
using search::docsummary::DynamicDocsumWriter;
using search::docsummary::GetDocsumsState;
using search::docsummary::IDocsumEnvironment;
using search::docsummary::JuniperProperties;

using vespa::config::search::SummaryConfig;
using vespa::config::search::SummarymapConfig;
using vespa::config::search::summary::JuniperrcConfig;

namespace vsm {

class IMatchingElementsFiller;

class GetDocsumsStateCallback : public search::docsummary::GetDocsumsStateCallback
{
private:
    search::FeatureSet::SP _summaryFeatures;
    search::FeatureSet::SP _rankFeatures;
    std::unique_ptr<IMatchingElementsFiller> _matching_elements_filler;

public:
    GetDocsumsStateCallback();
    void FillSummaryFeatures(GetDocsumsState * state, IDocsumEnvironment * env) override;
    void FillRankFeatures(GetDocsumsState * state, IDocsumEnvironment * env) override;
    virtual void FillDocumentLocations(GetDocsumsState * state, IDocsumEnvironment * env);
    virtual std::unique_ptr<search::MatchingElements> fill_matching_elements(const search::MatchingElementsFields& fields) override;
    void setSummaryFeatures(const search::FeatureSet::SP & sf) { _summaryFeatures = sf; }
    void setRankFeatures(const search::FeatureSet::SP & rf) { _rankFeatures = rf; }
    void set_matching_elements_filler(std::unique_ptr<IMatchingElementsFiller> matching_elements_filler);
    ~GetDocsumsStateCallback();
};

class DocsumTools : public IDocsumEnvironment
{
public:
    class FieldSpec {
    private:
        vespalib::string                      _outputName;
        std::vector<vespalib::string>         _inputNames;
        VsmsummaryConfig::Fieldmap::Command   _command;

    public:
        FieldSpec();
        ~FieldSpec();
        const vespalib::string & getOutputName() const { return _outputName; }
        void setOutputName(const vespalib::string & name) { _outputName = name; }
        const std::vector<vespalib::string> & getInputNames() const { return _inputNames; }
        std::vector<vespalib::string> & getInputNames() { return _inputNames; }
        VsmsummaryConfig::Fieldmap::Command getCommand() const { return _command; }
        void setCommand(VsmsummaryConfig::Fieldmap::Command command) { _command = command; }
    };

private:
    std::unique_ptr<DynamicDocsumWriter>                  _writer;
    std::unique_ptr<juniper::Juniper>                     _juniper;
    const ResultClass                                   * _resultClass;
    std::vector<FieldSpec>                                _fieldSpecs;
    DocsumTools(const DocsumTools &);
    DocsumTools &operator=(const DocsumTools &);

public:
    DocsumTools(std::unique_ptr<DynamicDocsumWriter> writer);
    ~DocsumTools();
    void setJuniper(std::unique_ptr<juniper::Juniper> juniper) { _juniper = std::move(juniper); }
    ResultConfig *getResultConfig() const { return _writer->GetResultConfig(); }
    DynamicDocsumWriter *getDocsumWriter() const { return _writer.get(); }
    const ResultClass *getResultClass() const { return _resultClass; }
    const std::vector<FieldSpec> & getFieldSpecs() const { return _fieldSpecs; }
    bool obtainFieldNames(const FastS_VsmsummaryHandle &cfg);

    // inherit doc from IDocsumEnvironment
    search::IAttributeManager * getAttributeManager() override { return NULL; }
    vespalib::string lookupIndex(const vespalib::string&) const override { return ""; }
    juniper::Juniper * getJuniper() override { return _juniper.get(); }
};

typedef std::shared_ptr<DocsumTools> DocsumToolsPtr;

class VSMConfigSnapshot {
private:
    const vespalib::string _configId;
    const config::ConfigSnapshot _snapshot;
public:
    VSMConfigSnapshot(const vespalib::string & configId, const config::ConfigSnapshot & snapshot)
        : _configId(configId),
          _snapshot(snapshot)
    { }
    template <typename ConfigType>
    std::unique_ptr<ConfigType> getConfig() const
    {
        return _snapshot.getConfig<ConfigType>(_configId);
    }
};

class VSMAdapter
{
public:
    VSMAdapter(const vespalib::string & highlightindexes, const vespalib::string & configId, Fast_WordFolder & wordFolder);
    virtual ~VSMAdapter();

    VsmfieldsHandle getFieldsConfig() const { return _fieldsCfg.get(); }
    DocsumToolsPtr getDocsumTools()   const { return _docsumTools.get(); }
    void configure(const VSMConfigSnapshot & snapshot);
private:
    vespalib::string                          _highlightindexes;
    const vespalib::string                    _configId;
    Fast_WordFolder                         & _wordFolder;
    vespalib::PtrHolder<VsmfieldsConfig>      _fieldsCfg;
    vespalib::PtrHolder<DocsumTools>          _docsumTools;
    std::unique_ptr<JuniperProperties>        _juniperProps;

    std::mutex                                _lock;

    VSMAdapter(const VSMAdapter &);
    VSMAdapter &operator=(const VSMAdapter &);
};

} // namespace vsm

