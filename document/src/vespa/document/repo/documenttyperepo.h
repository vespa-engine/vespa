// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>

namespace document {

namespace internal {
    class InternalDocumenttypesType;
    class DocumentTypeMap;
}

class AnnotationType;
class DataType;
struct DataTypeRepo;
class DocumentType;

class DocumentTypeRepo {
public:
    using DocumenttypesConfig = const internal::InternalDocumenttypesType;
    struct Handler {
        virtual ~Handler() = default;
        virtual void handle(const DocumentType & type) = 0;
    };


    template <class FunctionType>
    static std::unique_ptr<Handler>
    makeLambda(FunctionType &&function)
    {
        return std::make_unique<LambdaHandler<std::decay_t<FunctionType>>>
                (std::forward<FunctionType>(function));
    }

    // This one should only be used for testing. If you do not have any config.
    explicit DocumentTypeRepo(const DocumentType & docType);

    DocumentTypeRepo(const DocumentTypeRepo &) = delete;
    DocumentTypeRepo &operator=(const DocumentTypeRepo &) = delete;
    DocumentTypeRepo();
    explicit DocumentTypeRepo(const DocumenttypesConfig & config);
    ~DocumentTypeRepo();

    const DocumentType *getDocumentType(int32_t doc_type_id) const noexcept;
    const DocumentType *getDocumentType(vespalib::stringref name) const noexcept;
    const DataType *getDataType(const DocumentType &doc_type, int32_t id) const;
    const DataType *getDataType(const DocumentType &doc_type, vespalib::stringref name) const;
    const AnnotationType *getAnnotationType(const DocumentType &doc_type, int32_t id) const;
    void forEachDocumentType(Handler & handler) const;
    const DocumentType *getDefaultDocType() const { return _default; }
private:
    template <class FunctionType>
    class LambdaHandler : public Handler {
        FunctionType _func;
    public:
        LambdaHandler(FunctionType &&func) : _func(std::move(func)) {}
        LambdaHandler(const LambdaHandler &) = delete;
        LambdaHandler & operator = (const LambdaHandler &) = delete;
        ~LambdaHandler() override = default;
        void handle(const DocumentType & type) override { _func(type); }
    };

    std::unique_ptr<internal::DocumentTypeMap> _doc_types;
    const DocumentType * _default;
};

}  // namespace document

