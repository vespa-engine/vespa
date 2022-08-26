// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/searchlib/util/rawbuf.h>

namespace search::docsummary {

class IDocsumEnvironment;

class KeywordExtractor
{
public:

    class IndexPrefix
    {
        vespalib::string  _prefix;
    public:
        explicit IndexPrefix(const char *prefix);
        ~IndexPrefix();
        bool Match(const char *idxName) const;
        const vespalib::string& get_prefix() const noexcept { return _prefix; }
    };

private:
    typedef vespalib::hash_set<vespalib::string> Set;
    IDocsumEnvironment  *_env;
    std::vector<IndexPrefix> _legalPrefixes;
    Set                  _legalIndexes;

    bool IsLegalIndexPrefix(const char *idxName) const
    {
        for (auto& prefix : _legalPrefixes ) {
            if (prefix.Match(idxName)) {
                return true;
            }
        }
        return false;
    }

    bool IsLegalIndexName(const char *idxName) const;
public:
    explicit KeywordExtractor(IDocsumEnvironment * env);
    KeywordExtractor(const KeywordExtractor &) = delete;
    KeywordExtractor& operator=(const KeywordExtractor &) = delete;
    ~KeywordExtractor();


    /**
     * Add a prefix to the set of legal index name prefixes.
     *
     * @param prefix the index name prefix to add.
     **/
    void AddLegalIndexPrefix(const char *prefix)
    {
        _legalPrefixes.emplace_back(prefix);
    }


    /**
     * Add a name to the set of legal index names.
     *
     * @param idxName the index name to add.
     **/
    void AddLegalIndexName(const char *idxName)
    {
        _legalIndexes.insert(idxName);
    }


    /**
     * Parse the input string as a ';' separated list of index names and
     * index name prefixes. A '*' following a token in the list denotes
     * that the token is an index name prefix. Add the index names and
     * index name prefixes to the set of legal values.
     *
     * @param spec list of legal index names and prefixes.
     **/
    void AddLegalIndexSpec(const char *spec);


    /**
     * Create a spec on the same format as accepted by the @ref
     * AddLegalIndexSpec method. Freeing the returned spec is the
     * responsibility of the caller of this method.
     *
     * @return spec defining legal index names and prefixes.
     **/
    vespalib::string GetLegalIndexSpec();


    /**
     * Determine wether the given index name is legal by checking it
     * against the current set of legal index names and index name
     * prefixes held by this object.
     *
     * @return true if the given index name is legal.
     **/
    bool IsLegalIndex(vespalib::stringref idx) const;


    /**
     * Extract keywords from a stack dump of a SimpleQueryStack.
     *
     * The words are extracted as follows: For AND and OR operators, all
     * TERM items occuring in a legal index (the set of legal indexes is
     * defined by invoking the @ref AddLegalIndex and @ref
     * AddLegalIndexPrefix methods) are extracted.
     *
     * For PHRASE operators, the TERMS in a phrase are put together with
     * space between them.
     *
     * @todo For NOT operators, only the first operand is considered.
     *
     * @param buf Pointer to buffer with simple query stack dump.
     * @param bufLen Length of stack dump buffer
     * @return Pointer to a buffer containing zero-terminated keywords,
     * with an empty word at the end.
     */
    char *ExtractKeywords(vespalib::stringref buf) const;
};

}
