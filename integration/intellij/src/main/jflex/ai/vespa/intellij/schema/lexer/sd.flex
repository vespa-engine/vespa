// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.components.MultiColumnList;
import ai.vespa.intellij.schema.psi.SdTokenType;

import static ai.vespa.intellij.schema.psi.SdTypes.*; // That is the class which is specified as `elementTypeHolderClass` in bnf
                                                       // grammar file. This will contain all other tokens which we will use.
import static com.intellij.psi.TokenType.BAD_CHARACTER; // Pre-defined bad character token.
import static com.intellij.psi.TokenType.WHITE_SPACE; // Pre-defined whitespace character token.

/*
 * Vespa schema parser lexer
 *
 * @author Shahar Ariel
 */

%%

%public
%class SdLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

//**--------- REGEXES ---------**//
// If some character sequence is matched to this regex, it will be treated as an IDENTIFIER.
ID=[a-zA-Z_][a-zA-Z0-9_]*
// If some character sequence is matched to this regex, it will be treated as a WHITE_SPACE.
WHITE_SPACE=[ \t\x0B\f\r]+
NL=[\n]+

COMMENT=#.*
SYMBOL= [;!$|:{}().\[\]]
COMMA= [,]
//BLOCK_START= \{
//BLOCK_END= \}
INTEGER = [0-9]+
FLOAT = {INTEGER}[.][0-9]+([eE][-]?[0-9]+)?
STRING = \"([^\"\\]*(\\.[^\"\\]*)*)\"
STRING_SINGLE_QUOTE = '([^'\\]*(\\.[^'\\]*)*)'
WORD = \w+


%%

<YYINITIAL> {
  /**
   Here we match keywords. If a keyword is found, this returns a token which corresponds to that keyword.
   These tokens are generated using the 'sd.bnf' file and located in the SdTypes class.
   These tokens are Parsed uses these return values to match token squence to a parser rule.

   This list of keywords has to be synchronized with sd.bnf file. If you add a keyword here, you should add it to the
   sd.bnf file as well (to the rule KeywordOrIdentifier / KeywordNotIdentifier).
   */
  
  "search"                   { return SEARCH; }
  "schema"                   { return SCHEMA; }
  "document"                 { return DOCUMENT; }
  "inherits"                 { return INHERITS; }
  "struct"                   { return STRUCT; }
  "field"                    { return FIELD; } 
  "type"                     { return TYPE; }
  "struct-field"             { return STRUCT_FIELD; }
  "match"                    { return MATCH; }
  
  "indexing"                 { return INDEXING; }
  "summary"                  { return SUMMARY; }
  "attribute"                { return ATTRIBUTE; }
  "set_language"             { return SET_LANGUAGE; }
  
  "array"                    { return ARRAY; }
  "raw"                      { return RAW; }
  "uri"                      { return URI; }
  "reference"                { return REFERENCE; }
  "annotationreference"      { return ANNOTATIONREFERENCE; }
  "weightedset"              { return WEIGHTEDSET; }
  "map"                      { return MAP; }

  "text"                     { return TEXT; }
  "exact"                    { return EXACT; }
  "exact-terminator"         { return EXACT_TERMINATOR; }
  "word"                     { return WORD; }
  "prefix"                   { return PREFIX; }
  "cased"                    { return CASED; }
  "uncased"                  { return UNCASED; }
  "substring"                { return SUBSTRING; }
  "suffix"                   { return SUFFIX; }
  "max-length"               { return MAX_LENGTH; }
  "gram"                     { return GRAM; }
  "gram-size"                { return GRAM_SIZE; }

  "fast-search"              { return FAST_SEARCH; }
  "fast-rank"                { return FAST_RANK; }
  "fast-access"              { return FAST_ACCESS; }
  "alias"                    { return ALIAS; }
  "sorting"                  { return SORTING; }
  "uca"                      { return UCA; }
  "lowercase"                { return LOWERCASE; }
  "paged"                    { return PAGED; }
  "strength"                 { return STRENGTH; }
  "primary"                  { return PRIMARY; }
  "secondary"                { return SECONDARY; }
  "tertiary"                 { return TERTIARY; }
  "quaternary"               { return QUATERNARY; }
  "identical"                { return IDENTICAL; }
  "distance-metric"          { return DISTANCE_METRIC; }

  "rank"                     { return RANK; }
  "filter"                   { return FILTER; }
  "normal"                   { return NORMAL; }
  "literal"                  { return LITERAL; }
  "indexing-rewrite"         { return INDEXING_REWRITE; }
  "none"                     { return NONE; }
  "query-command"            { return QUERY_COMMAND; }
  "full"                     { return FULL; }
  "static"                   { return STATIC; }
  "dynamic"                  { return DYNAMIC; }
  "source"                   { return SOURCE; }
  "to"                       { return TO; }
  "matched-elements-only"    { return MATCHED_ELEMENTS_ONLY; }

  "input"                    { return INPUT; }
  "mutable"                  { return MUTABLE; }
  "enable-bit-vectors"       { return ENABLE_BIT_VECTORS; }
  "enable-only-bit-vector"   { return ENABLE_ONLY_BIT_VECTOR; }
  "document-summary"         { return DOCUMENT_SUMMARY; }
  "from-disk"                { return FROM_DISK; }
  "omit-summary-features"    { return OMIT_SUMMARY_FEATURES; }
  "import"                   { return IMPORT; }
  "as"                       { return AS; }

  "rank-profile"             { return RANK_PROFILE; }
  "model"                    { return MODEL; }
  "match-phase"              { return MATCH_PHASE; }
  "order"                    { return ORDER; }
  "ascending"                { return ASCENDING; }
  "descending"               { return DESCENDING; }
  "locale"                   { return LOCALE; }
  "max-hits"                 { return MAX_HITS; }
  "diversity"                { return DIVERSITY; }
  "min-groups"               { return MIN_GROUPS; }
  "cutoff-factor"            { return CUTOFF_FACTOR; }
  "cutoff-strategy"          { return CUTOFF_STRATEGY; }
  "loose"                    { return LOOSE; }
  "strict"                   { return STRICT; }
  "rank-properties"          { return RANK_PROPERTIES; }
  "inputs"                   { return INPUTS; }

  "significance"             { return SIGNIFICANCE; }
  "first-phase"              { return FIRST_PHASE; }
  "keep-rank-count"          { return KEEP_RANK_COUNT; }
  "total-keep-rank-count"    { return TOTAL_KEEP_RANK_COUNT; }
  "rank-score-drop-limit"    { return RANK_SCORE_DROP_LIMIT; }
  "expression"               { return EXPRESSION; }
  "file"                     { return FILE; }
  "expression"               { return EXPRESSION; }
  "num-threads-per-search"   { return NUM_THREADS_PER_SEARCH; }
  "termwise-limit"           { return TERMWISE_LIMIT; }
  "ignore-default-rank-features" { return IGNORE_DEFAULT_RANK_FEATURES; }
  "min-hits-per-thread"      { return MIN_HITS_PER_THREAD; }
  "num-search-partitions"    { return NUM_SEARCH_PARTITIONS; }
  "constants"                { return CONSTANTS; }
  "second-phase"             { return SECOND_PHASE; }
  "rerank-count"             { return RERANK_COUNT; }
  "total-rerank-count"       { return TOTAL_RERANK_COUNT; }
  "rank-features"            { return RANK_FEATURES; }

  "weight"                   { return WEIGHT; }
  "index"                    { return INDEX; }
  "bolding"                  { return BOLDING; }
  "on"                       { return ON; }
  "off"                      { return OFF; }
  "true"                     { return TRUE; }
  "false"                    { return FALSE; }
  "id"                       { return ID; }
  "normalizing"              { return NORMALIZING; }
  "stemming"                 { return STEMMING; }
  "arity"                    { return ARITY; }
  "lower-bound"              { return LOWER_BOUND; }
  "upper-bound"              { return UPPER_BOUND; }
  "dense-posting-list-threshold" {return DENSE_POSTING_LIST_THRESHOLD; }
  "enable-bm25"              { return ENABLE_BM25; }
  "hnsw"                     { return HNSW; }
  "max-links-per-node"       { return MAX_LINKS_PER_NODE; }
  "neighbors-to-explore-at-insert" { return NEIGHBORS_TO_EXPLORE_AT_INSERT; }
  "multi-threaded-indexing"  { return MULTI_THREADED_INDEXING; }
  "create-if-nonexistent"    { return CREATE_IF_NONEXISTENT; }
  "remove-if-zero"           { return REMOVE_IF_ZERO; }
  "dictionary"               { return DICTIONARY; }
  "hash"                     { return HASH; }
  "btree"                    { return BTREE; }
      
  "fieldset"                 { return FIELDSET; }
  "fields"                   { return FIELDS; }
  "constant"                 { return CONSTANT; }
  "output"                   { return OUTPUT; }
      
  "annotation"               { return ANNOTATION; }
  "rank-type"                { return RANK_TYPE; }
  "onnx-model"               { return ONNX_MODEL; }
  "raw-as-base64-in-summary" { return RAW_AS_BASE64_IN_SUMMARY; }
  "on-match"                 { return ON_MATCH; }
  "on-rank"                  { return ON_RANK; }
  "on-summary"               { return ON_SUMMARY; }

  "function"                 { return FUNCTION; }
  "macro"                    { return MACRO; }
  "inline"                   { return INLINE; }

  "summary-features"         { return SUMMARY_FEATURES; }
  "match-features"           { return MATCH_FEATURES; }
  "rank-features"            { return RANK_FEATURES; }
  
  "body"                     { return BODY; }
  "header"                   { return HEADER; }
  "summary-to"               { return SUMMARY_TO; }
      
  "evaluation-point"         { return EVALUATION_POINT; }
  "pre-post-filter-tipping-point" { return PRE_POST_FILTER_TIPPING_POINT; }

  "<"                        { return COMPARISON_OPERATOR; }
  ">"                        { return COMPARISON_OPERATOR; }
  "=="                       { return COMPARISON_OPERATOR; }
  "<="                       { return COMPARISON_OPERATOR; }
  ">="                       { return COMPARISON_OPERATOR; }
  "~="                       { return COMPARISON_OPERATOR; }
  "!="                       { return COMPARISON_OPERATOR; }

  "+"                        { return ARITHMETIC_OPERATOR; }
  "-"                        { return ARITHMETIC_OPERATOR; }
  "*"                        { return ARITHMETIC_OPERATOR; }
  "/"                        { return ARITHMETIC_OPERATOR; }
  "%"                        { return ARITHMETIC_OPERATOR; }
  "^"                        { return ARITHMETIC_OPERATOR; }
  "||"                       { return ARITHMETIC_OPERATOR; }
  "&&"                       { return ARITHMETIC_OPERATOR; }

  // Here we check for character sequences which matches regular expressions defined above.
  {ID}                       { return ID_REG; }

  {WHITE_SPACE}              { return WHITE_SPACE; }
  {NL}                       { return NL; }

  {COMMENT}                  { return COMMENT; }  
  {SYMBOL}                   { return SYMBOL; }  
  {COMMA}                    { return COMMA; }
  //{BLOCK_START}              { return BLOCK_START; }
  //{BLOCK_END}                { return BLOCK_END; }
  {INTEGER}                  { return INTEGER_REG; }
  {FLOAT}                    { return FLOAT_REG; }
  {WORD}                     { return WORD_REG; }
  {STRING}                   { return STRING_REG; }  
  {STRING_SINGLE_QUOTE}      { return STRING_REG_SINGLE_QUOTE; }

}

// If the character sequence does not match any of the above rules, we return BAD_CHARACTER which indicates that
// there is an error in the character sequence. This is used to highlight errors.
[^] { return BAD_CHARACTER; }