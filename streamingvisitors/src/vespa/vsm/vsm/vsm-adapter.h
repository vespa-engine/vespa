// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/base.h>
#include <vespa/vsm/config/vsm-cfif.h>
#include <vespa/config-summary.h>
#include <vespa/searchsummary/docsummary/docsumwriter.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/idocsumenvironment.h>
#include <vespa/juniper/rpinterface.h>
#include <vespa/vespalib/util/featureset.h>

using search::docsummary::ResultConfig;
using search::docsummary::ResultClass;
using search::docsummary::IDocsumWriter;
using search::docsummary::DynamicDocsumWriter;
using search::docsummary::GetDocsumsState;
using search::docsummary::IDocsumEnvironment;
using search::docsummary::JuniperProperties;


namespace config { class ConfigSnapshot; }
namespace vsm {

class IMatchingElementsFiller;

class GetDocsumsStateCallback : public search::docsummary::GetDocsumsStateCallback
{
private:
    vespalib::FeatureSet::SP _summaryFeatures;
    vespalib::FeatureSet::SP _rankFeatures;
    std::unique_ptr<IMatchingElementsFiller> _matching_elements_filler;

public:
    GetDocsumsStateCallback();
    void fillSummaryFeatures(GetDocsumsState& state) override;
    void fillRankFeatures(GetDocsumsState& state) override;
    std::unique_ptr<search::MatchingElements> fill_matching_elements(const search::MatchingElementsFields& fields) override;
    void setSummaryFeatures(const vespalib::FeatureSet::SP & sf) { _summaryFeatures = sf; }
    void setRankFeatures(const vespalib::FeatureSet::SP & rf) { _rankFeatures = rf; }
    void set_matching_elements_filler(std::unique_ptr<IMatchingElementsFiller> matching_elements_filler);
    ~GetDocsumsStateCallback() override;
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
        FieldSpec() noexcept;
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

public:
    DocsumTools();
    DocsumTools(const DocsumTools &) = delete;
    DocsumTools &operator=(const DocsumTools &) = delete;
    ~DocsumTools() override;
    void set_writer(std::unique_ptr<DynamicDocsumWriter> writer);
    void setJuniper(std::unique_ptr<juniper::Juniper> juniper) { _juniper = std::move(juniper); }
    const ResultConfig *getResultConfig() const { return _writer->GetResultConfig(); }
    DynamicDocsumWriter *getDocsumWriter() const { return _writer.get(); }
    const ResultClass *getResultClass() const { return _resultClass; }
    const std::vector<FieldSpec> & getFieldSpecs() const { return _fieldSpecs; }
    bool obtainFieldNames(const FastS_VsmsummaryHandle &cfg);

    // inherit doc from IDocsumEnvironment
    const search::IAttributeManager * getAttributeManager() const override { return nullptr; }
    const juniper::Juniper * getJuniper() const override { return _juniper.get(); }
};

using DocsumToolsPtr = std::shared_ptr<DocsumTools>;

class VSMConfigSnapshot {
private:
    const vespalib::string _configId;
    std::unique_ptr<const config::ConfigSnapshot> _snapshot;
public:
    VSMConfigSnapshot(const vespalib::string & configId, const config::ConfigSnapshot & snapshot);
    ~VSMConfigSnapshot();
    template <typename ConfigType>
    std::unique_ptr<ConfigType> getConfig() const;
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

