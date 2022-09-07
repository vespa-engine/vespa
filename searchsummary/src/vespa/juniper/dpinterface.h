// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <cstdio>

/** @file dpinterface.h This file is the main include file for inetgrators of the document
 *    processing/indexing stages of Juniper specific processing. For integrating
 *    result processing (core Juniper - highlighting/proximity metric computation) refer to
 *    rpinterface.h
 */

namespace juniper {

/** class Tokentype Hint as to which type of token this is.
 *  If this information is already aggregated by the caller
 *  it allows us to save som extra computation in Juniper.
 */
enum Tokentype
{
    TOKEN_UNKNOWN,  // token type info not present.
    TOKEN_WORD,     // This is a word token
    TOKEN_SEP,      // This is a separator token
    TOKEN_MARKUP,   // This token contains general unspecified markup
    TOKEN_OTHER,    // This token is something else than any of the above
    TOKEN_MAX       // Max token types currently supported.
};

/** Opaque reference to the Juniper internal representation of a document summary
 *  Allows transport of Juniper information between different stages of the
 *  document processing without having to serialize/deserialize for each such step.
 */
class Docsum;

/** @class DocsumProcessor
 *  Interface for Document processors specific for and
 *  implemented in Juniper.
 *  that operate on doc summaries (at proper places in the document processing pipelines)
 *  to enhance and annotate the source for Juniper result processing (see rpinterface.h)
 */
class DocsumProcessor
{
public:
    virtual ~DocsumProcessor() {}

    /** Process a docsum with this processor. Processing can in the cases where
     *  token based processing is necessary just be implemented as setting
     *  the document summary to do processing for, but can also yield a complete
     *  processing.
     *  @param docsum_input a previously serialized Docsum object or an UTF-8 string
     *  @param length Length in bytes of the docsum_input object
     *  @return false if the operation failed, true otherwise
     */
    virtual bool Process(const char* docsum_input, size_t length) = 0;

    /** Process a docsum with this processor
     *  @param docsum an input Docsum to process. This DocsumProcessor
     *    also takes responsibility for releasing the Docsum object if necessary, that is
     *    GetDocsum has not been called when this object is deleted,
     *    the Docsum gets released as well.
     *  @return false if the operation failed, true otherwise
     */
    virtual bool Process(Docsum* docsum) = 0;

    /** Low level document processing
     *  @param rep A textual representation of the token to process
     *  @param start The start position of this token within the original text
     *  @param len Length of the token representation
     *  @param type The token type in question (to allow saving of
     *     processing time in Juniper)
     *  @return true if operation ok, false if failure to process
     */
    virtual bool ProcessToken(const char* rep, off_t start, size_t len, Tokentype type) = 0;

    /** Retrieve a reference to the docsum representation
     *  @return The Docsum object including the current state of the docsum.
     *    This Docsum object must later be released by the caller using ReleaseDocsum
     *    or handed over to a subsequent processor.
     */
    virtual Docsum* GetDocsum() = 0;

    /** Create a textual representation of the annotated docsum suitable for disk storage
     *  for later usage by Juniper result processing.
     * @param length The length of the serialized docsum
     * @return A pointer to the text representation of the docsum. This object
     *    is valid throughout the life of this document processor or until
     *    the next call to Serialize() for this processor.
     */
    virtual const char* Serialize(size_t& length) = 0;
};

void ReleaseDocsum(Docsum* docsum);

} // end namespace juniper

