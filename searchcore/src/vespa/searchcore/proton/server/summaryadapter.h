// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "isummaryadapter.h"

namespace proton {

class SummaryManager;
class ISummaryManager;

class SummaryAdapter : public ISummaryAdapter {
private:
    std::shared_ptr<SummaryManager>  _mgr;
    SerialNum                        _lastSerial;

    bool ignore(search::SerialNum serialNum) const;
    ISummaryManager & imgr() const;

public:
    explicit SummaryAdapter(std::shared_ptr<SummaryManager> mgr);
    ~SummaryAdapter() override;

    void put(SerialNum serialNum, const DocumentIdT lid, const Document &doc) override;
    void put(SerialNum serialNum, const DocumentIdT lid, const vespalib::nbostream &doc) override;
    void remove(SerialNum serialNum, const DocumentIdT lid) override;
    void heartBeat(SerialNum serialNum) override;
    const search::IDocumentStore &getDocumentStore() const override;
    std::unique_ptr<document::Document> get(const DocumentIdT lid, const DocumentTypeRepo &repo) override;
    void compactLidSpace(uint32_t wantedDocIdLimit) override;
};

} // namespace proton
