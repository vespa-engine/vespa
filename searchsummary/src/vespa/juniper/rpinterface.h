// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/* $Id$ */

#pragma once

#include "IJuniperProperties.h"
#include "rewriter.h"
#include <memory>

/** @file rpinterface.h This file is the main include file for the advanced
 *    result processing interface to Juniper. The complete set of new interfaces
 *    to Juniper as of Juniper v.2.x.x is contained in the juniper namespace.
 *    This file together with query.h is the result processing part of these interfaces.
 *    The other part is the indexing/document processing interface with main include file
 *    dpinterface.h
 */

/** This define will be changed only in case of backward incompatible
 * API changes - we use 2 initially to avoid confusion with Juniper 1.0.x..
 */
#define JUNIPER_RP_ABI_VERSION 3

/* Changes to this version number indicates minor interface additions
 * where the original interface is kept unchanged. Can be used to test for features.
 */
#define JUNIPER_RP_API_MINOR_VERSION 1

class Fast_WordFolder;

/** This is the new query/document provider interface to Juniper as of Juniper 2.0.x
 *  It replaces the Juniper 1.0.x interface on the result processing side, previously
 *  defined by simpledynsum.h .
 *  While the old interface (simpledynsum.h) is kept for backward compatibility, it is
 *  depreciated as it allows less flexibility and thus provides lower quality teasers.
 */
namespace juniper
{

/** Version tag generated from Makefile/configure system */
extern const char* version_tag;

/** Opaque object encapsulating a default configuration set for Juniper.
 *  Multiple such configurations can co-exist, for instance to allow different
 *  summary fields to use different teaser configurations.
 *  Note that in addition to this (relatively static) configuration set,
 *  configuration parameters can be overridden on a per query basis by means of the
 *  juniperoptions query parameter. See the Juniper 2.x.x documentation for details.
 */
class Config;

/** Opaque object encapsulating state associated with a particular query
 */
class QueryHandle;

/** Opaque object encapsulating the result of a partial or full Juniper
 *  analysis of a document.
 */
class Result;

class QueryModifier;

class Summary
{
public:
    virtual ~Summary() {}
    // The textual representation of the generated summary
    virtual const char* Text() const = 0;
    virtual size_t Length() const = 0;
};

class Juniper {
public:
    /**
     * Convenience typedefs.
     */
    using UP = std::unique_ptr<Juniper>;
    using SP = std::shared_ptr<Juniper>;

    /** Initialize the Juniper subsystem.
     * @param props A pointer to the object containing all available configuration
     *   property values for the Juniper parameters.
     * @param wordfolder A pointer to a custom wordfolder object to use. If
     *  NULL, a default wordfolder will be maintained by Juniper if necessary.
     *  In case of errors during initialization or config object creation,
     *  the cause will be appropriately reported to the @param log object
     *  with status ELOG_CRITICAL
     * @param api_version Version check parameter
     *   - should always be left with the default value to ensure binary backward
     *     compatibility between versions.
     */
    Juniper(IJuniperProperties* props,
          const Fast_WordFolder* wordfolder, int api_version = JUNIPER_RP_ABI_VERSION);
    /** Deinitialize the Juniper subsystem. Release all remaining resources
     *  associated with Juniper - reverse the effect of the Init function.
     *  Assumes that all Result objects have been released.
     */
    ~Juniper();

    const Fast_WordFolder & getWordFolder() const noexcept { return *_wordfolder; }
    const IJuniperProperties & getProp() const noexcept { return *_props; }
    QueryModifier & getModifier() { return *_modifier; }

    /** Create a result processing configuration of Juniper for subsequent use
     * @param config_name a symbolic prefix to be used in the fsearch configuration file
     *  (fsearchrc/fsearch.addon*). The default value reflects the Juniper 1.x.x usage where
     *  Juniper configuration variables are supplied as "juniper.dynsum.length value" pairs.
     *  If a configuration object gets a config name of "mysummaryfield", then
     *  if "mysummaryfield.dynsum.length exists as a property in the config file,
     *  then that value is used, otherwise the default "juniper.dynsum.length" value is used.
     * @return a nonzero object for subsequent reference if initialization is done,
     *  NULL if an error occurred.
     */

    std::unique_ptr<Config> CreateConfig(const char* config_name = "juniper") const;
    /** Allocate a query handle for the given query for subsequent calls to Analyse
     *  for different hits. Performs the necessary per query processing for Juniper.
     * @param query A query to start result processing for.
     * @param juniperoptions The value of the special juniperoption URL parameter
     *   provided for this search. This parameter is parsed by Juniper to support optional
     *   behaviour such as user customization of teaser parameters, selectively
     *   enabling of Juniper debugging/tracing features and to support Juniper extensions
     *   to the query language.
     * @return A unique pointer to a QueryHandle.
     */
    std::unique_ptr<QueryHandle> CreateQueryHandle(const IQuery& query, const char* juniperoptions) const;

    /** Add an rewriter for all terms that are prefixed with the given index.
     *  When Juniper encounter a term in the query tagged with this index,
     *  Juniper assumes that that term has been subject to expansion, and will
     *  apply the rewriter to all terms in all analysed documents before
     *  matching with the query.
     */
    void AddRewriter(const char* index_name, IRewriter* rewriter, bool for_query, bool for_document);

    // Mostly for testing - being able to start with clean sheets for each test:
    void FlushRewriters();

private:
    IJuniperProperties * _props;
    const Fast_WordFolder  * _wordfolder;
    std::unique_ptr<QueryModifier>      _modifier;
};

/** This function defines an equality relation over Juniper configs,
 *  @return true if a previously acquired result handle (through use of
 *     one of the Config objects can be reused (typically to produce a
 *     differently looking teaser) with the other Config object.
 *     This is the case if the two config objects only differ in the teaser
 *     parameters (eg. those named *.dynsum.*)
 */
bool AnalyseCompatible(Config* conf1, Config* conf2);

/** Perform initial content analysis on a query/content pair.
 *  Note that the content may either be a simple UTF-8 encoded string or a
 *  more advanced representation including document structure elements, as provided
 *  by the Juniper document processing interface (see dpinterface.h)
 * @param config A valid pointer to the parameter configuration to use for the analysis
 * @param query The query, represented by a QueryHandle to base the analysis on.
 *    (previously generated by CreateQueryHandle)
 * @param docsum A reference to a document summary to be analysed.
 * @param docsum_len The length in bytes of the document summary, including
 any meta information.
 * @param docid A 32 bit number uniquely identifying the document to be analysed
 * @param langid A unique 32 bit id representing the language which
 this document summary is to be analysed in context of.
 * @return A unique pointer to a Result
 */
std::unique_ptr<Result> Analyse(const Config& config, QueryHandle& query,
                const char* docsum, size_t docsum_len,
                uint32_t docid,
                uint32_t langid);

/** Get the computed relevancy of the processed content from the result.
 *  @param result_handle The result to retrieve from
 *  @return The relevancy (proximitymetric) of the processed content.
 */
long GetRelevancy(Result& result_handle);

/** Generate a teaser based on the provided analysis result
 *  @param result_handle a handle obtained by a previous call to Analyse
 *  @param alt_config An optional alternate config to use for this teaser generation
 *    The purpose of alt_config is to allow generation of multiple teasers
 *    based on the same content and analysis.
 *  @return The generated Teaser object. This object is valid until result_handle is deleted.
 */
Summary* GetTeaser(Result& result_handle, const Config* alt_config = NULL);

/** Retrieve log information based on the previous calls to this result handle.
 *  Note that for the log to be complete, the juniper log override entry in
 *  the summary field map must be placed after any other juniper override directives.
 * @param result_handle a handle obtained by a previous call to Analyse.
 * @return value: a summary description containing the Juniper log as a text field
 *   if any log information is available, or else an empty summary.
 *   This object is valid until result_handle is deleted.
 */
Summary* GetLog(Result& result_handle);

} // end namespace juniper

