# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# /// script
# requires-python = ">=3.10"
# ///
"""
Auto-generate a TextMate grammar for the Vespa schema language.

Reads token declarations from the CongoCC parser grammars and scope
classifications from SchemaSemanticTokenConfig.java, then emits
grammar/vespa-schema.tmLanguage.json.

The generated scopes are chosen to match the colors produced by the
Java LSP semantic tokens in VS Code's default Dark+ theme:

  LSP SemanticTokenType  →  TextMate scope
  ─────────────────────────────────────────
  Keyword                →  keyword.control
  Number                 →  constant.numeric
  String                 →  string.quoted
  Function               →  entity.name.function
  Operator               →  keyword.operator
  Type                   →  support.type
  EnumMember             →  variable.other.enummember
  Macro                  →  entity.name.function
  Comment                →  comment.line

Tokens that the LSP leaves uncolored are also left uncolored here.

Usage:
    uv run tools/generate_tmgrammar.py
"""

from __future__ import annotations

import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

TMGRAMMAR = Path(__file__).resolve().parent.parent
VESPA_ROOT = TMGRAMMAR.parents[1]
LSP = VESPA_ROOT / "integration" / "schema-language-server" / "language-server"
SCHEMA_CCC = LSP / "src/main/ccc/SchemaParser.ccc"
INDEXING_CCC = LSP / "src/main/ccc/indexinglanguage/IndexingParser.ccc"
RANKING_CCC = LSP / "src/main/ccc/rankingexpression/RankingExpressionParser.ccc"
SEMANTIC_CFG = LSP / "src/main/java/ai/vespa/schemals/lsp/schema/semantictokens/SchemaSemanticTokenConfig.java"
BUILTIN_FUNCS = LSP / "src/main/java/ai/vespa/schemals/schemadocument/resolvers/RankExpression/BuiltInFunctions.java"
OUTPUT = TMGRAMMAR / "grammar/vespa-schema.tmLanguage.json"

# ---------------------------------------------------------------------------
# 1a  Parse .ccc token declarations
# ---------------------------------------------------------------------------

# Matches simple literal tokens: < TOKEN_NAME: "literal" >
# Does NOT match complex tokens that have additional regex after the literal.
_LITERAL_TOKEN_RE = re.compile(
    r'<\s*([A-Z_][A-Z_0-9]*)\s*:\s*"([^"]+)"\s*>'
)

_SKIP_TOKEN_NAMES = {"NL"}


@dataclass
class TokenInfo:
    name: str
    literal: str
    source: str  # "schema", "indexing", "ranking"


def _is_keyword_literal(literal: str) -> bool:
    """Return True if the literal looks like a keyword (alphabetic, hyphens, underscores)."""
    return bool(re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_-]*", literal))


def parse_ccc_tokens(path: Path, source: str) -> list[TokenInfo]:
    """Extract literal keyword tokens from a .ccc file."""
    text = path.read_text()

    # Remove UNPARSED sections (comments) so we don't pick up comment tokens
    text = re.sub(r"UNPARSED\s*:.*?;", "", text, flags=re.DOTALL)

    tokens = []
    for m in _LITERAL_TOKEN_RE.finditer(text):
        name, literal = m.group(1), m.group(2)
        if name.startswith("#"):
            continue
        if name in _SKIP_TOKEN_NAMES:
            continue
        if not _is_keyword_literal(literal):
            continue
        tokens.append(TokenInfo(name=name, literal=literal, source=source))
    return tokens


# ---------------------------------------------------------------------------
# 1b  Parse SemanticTokenConfig.java for scope classification
# ---------------------------------------------------------------------------

def _extract_token_names(java: str, list_pattern: str) -> set[str]:
    """Extract TokenType.XXX names from a Java list/set initializer block."""
    pattern = re.compile(
        rf"{re.escape(list_pattern)}\s*=\s*new\s+\w+[^{{]*\{{\{{\s*(.*?)\}}\}};",
        re.DOTALL,
    )
    m = pattern.search(java)
    if not m:
        return set()
    block = m.group(1)
    return set(re.findall(r"TokenType\.(\w+)", block))


def _extract_token_map(java: str, map_pattern: str) -> dict[str, str]:
    """Extract put(TokenType.XXX, SemanticTokenTypes.YYY) or put(..., "yyy") from a Java map."""
    pattern = re.compile(
        rf"{re.escape(map_pattern)}\s*=\s*new\s+\w+[^{{]*\{{\{{\s*(.*?)\}}\}};",
        re.DOTALL,
    )
    m = pattern.search(java)
    if not m:
        return {}
    block = m.group(1)
    result = {}
    for pm in re.finditer(
        r'put\([^,]*TokenType\.(\w+)\s*,\s*(?:SemanticTokenTypes\.)?["\']?(\w+)["\']?\s*\)',
        block,
    ):
        result[pm.group(1)] = pm.group(2)
    return result


@dataclass
class Classification:
    schema_keywords: set[str] = field(default_factory=set)
    schema_type_map: dict[str, str] = field(default_factory=dict)
    indexing_keywords: set[str] = field(default_factory=set)
    indexing_outputs: set[str] = field(default_factory=set)
    indexing_operators: set[str] = field(default_factory=set)
    ranking_keywords: set[str] = field(default_factory=set)
    ranking_functions: set[str] = field(default_factory=set)
    ranking_operators: set[str] = field(default_factory=set)
    ranking_type_map: dict[str, str] = field(default_factory=dict)


def parse_semantic_config(path: Path) -> Classification:
    java = path.read_text()
    c = Classification()

    c.schema_keywords = _extract_token_names(java, "keywordTokens")
    c.schema_type_map = _extract_token_map(java, "schemaTokenTypeLSPNameMap")

    c.indexing_keywords = _extract_token_names(java, "indexingLanguageKeywords")
    c.indexing_outputs = _extract_token_names(java, "indexingLanguageOutputs")
    c.indexing_operators = _extract_token_names(java, "indexingLanguageOperators")

    c.ranking_keywords = _extract_token_names(java, "rankingExpressionKeywordTokens")
    c.ranking_functions = _extract_token_names(java, "rankingExpressioFunctionTokens")
    c.ranking_operators = _extract_token_names(java, "rankingExpressionOperationTokens")
    c.ranking_type_map = _extract_token_map(java, "rankingExpressionTokenTypeLSPNameMap")

    return c


def parse_builtin_functions(path: Path) -> list[str]:
    """Extract built-in rank feature function names from BuiltInFunctions.java."""
    java = path.read_text()
    # Match both put("name", ...) in the map and add("name") in the set
    names: set[str] = set()
    for m in re.finditer(r'put\(\s*"([a-zA-Z_]\w*)"', java):
        names.add(m.group(1))
    for m in re.finditer(r'add\(\s*"([a-zA-Z_]\w*)"', java):
        names.add(m.group(1))
    return sorted(names)


# ---------------------------------------------------------------------------
# 1c  Classify each token → TextMate scope (matching LSP semantic tokens)
#
# The Java LSP in SchemaSemanticTokens.traverseCST() applies tokens as:
#
#   Schema:
#     schemaTokenTypeMap  = keywordTokens → Keyword
#                         + schemaTokenTypeLSPNameMap entries
#     Tokens not in schemaTokenTypeMap → no color
#
#   Indexing:
#     indexingLanguageOutputs  → Type
#     indexingLanguageKeywords → Keyword
#     indexingLanguageOperators → Function
#     Others → no color
#
#   Ranking:
#     rankExpressionTokenTypeMap = rankingExpressionKeywordTokens → Keyword
#                                + rankingExpressionOperationTokens → Operator
#                                + rankingExpressioFunctionTokens → Function
#                                + rankingExpressionTokenTypeLSPNameMap entries
#     Others → no color
# ---------------------------------------------------------------------------

# Map from LSP SemanticTokenType name → TextMate scope
_LSP_TO_TM = {
    "Keyword":    "keyword.control",
    "Number":     "constant.numeric",
    "String":     "string.quoted",
    "Function":   "entity.name.function",
    "Operator":   "keyword.operator",
    "Type":       "support.type",
    "EnumMember": "variable.other.enummember",
    "Macro":      "entity.name.function",
}


# Hyphenated tokens the LSP doesn't color but that TextMate must claim as
# keywords to prevent sub-word mismatches (e.g. "on-match" splitting into
# "on" (boolean) + "match" (keyword)).
_TM_EXTRA_SCHEMA_KEYWORDS = {
    "ON_MATCH", "ON_FIRST_PHASE", "ON_SECOND_PHASE", "ON_SUMMARY",
    # Sub-block property keywords needed for TextMate
    "MIN_GROUPS", "ORDER", "MAX_HITS", "TOTAL_MAX_HITS",
    "MAX_FILTER_COVERAGE", "RERANK_COUNT", "TOTAL_RERANK_COUNT",
    "KEEP_RANK_COUNT", "TOTAL_KEEP_RANK_COUNT",
    "MIN_HITS_PER_THREAD", "FILTER_FIRST_THRESHOLD",
    "FILTER_FIRST_EXPLORATION", "EXPLORATION_SLACK",
    "PREFETCH_TENSORS",
    "GRAM_SIZE", "MAX_LENGTH", "MAX_OCCURRENCES", "MAX_TOKEN_LENGTH",
    "EXACT_TERMINATOR",
    "SOURCE", "SSCONTEXTUAL", "SSOVERRIDE", "SSTITLE", "SSURL",
    "CREATE_IF_NONEXISTENT", "REMOVE_IF_ZERO",
    "MATCHED_ELEMENTS_ONLY",
    "PROPERTIES",
    "CUTOFF_FACTOR", "CUTOFF_STRATEGY",
    "EVALUATION_POINT", "PRE_POST_FILTER_TIPPING_POINT",
    "ENABLE_BIT_VECTORS", "ENABLE_ONLY_BIT_VECTOR",
    "MULTI_THREADED_INDEXING",
    "SELECT_ELEMENTS_BY",
    "INLINE",
}

# Ranking math functions the LSP doesn't color but that should be highlighted
# as functions in TextMate for readability (log, sigmoid, sin, cos, etc.).
_TM_EXTRA_RANKING_FUNCTIONS = {
    "ABS", "ACOS", "ASIN", "ATAN", "CEIL", "COS", "COSH",
    "ELU", "EXP", "FABS", "FLOOR", "LOG", "LOG10",
    "RELU", "ROUND", "SIGMOID", "SIGN", "SIN", "SINH",
    "SQUARE", "SQRT", "TAN", "TANH", "ERF",
    "FILTER_SUBSPACES", "CELL_ORDER", "TOP",
    "F",
}


def classify_token(tok: TokenInfo, cls: Classification) -> str | None:
    """Return the TextMate scope for a token, or None if the LSP doesn't color it."""
    name = tok.name

    if tok.source == "schema":
        # keywordTokens → Keyword
        if name in cls.schema_keywords:
            return "keyword.control.vespa"
        # Extra keywords needed by TextMate to prevent sub-word mismatches
        if name in _TM_EXTRA_SCHEMA_KEYWORDS:
            return "keyword.control.vespa"
        # schemaTokenTypeLSPNameMap → mapped type
        lsp = cls.schema_type_map.get(name)
        if lsp:
            tm = _LSP_TO_TM.get(lsp)
            if tm:
                return tm + ".vespa"
        # Not in schemaTokenTypeMap → no color
        return None

    if tok.source == "indexing":
        # Order matches Java: outputs → keywords → operators
        if name in cls.indexing_outputs:
            return "support.type.indexing.vespa"
        if name in cls.indexing_keywords:
            return "keyword.control.indexing.vespa"
        if name in cls.indexing_operators:
            return "entity.name.function.indexing.vespa"
        return None

    if tok.source == "ranking":
        if name in cls.ranking_keywords:
            return "keyword.control.ranking.vespa"
        if name in cls.ranking_operators:
            return "keyword.operator.ranking.vespa"
        if name in cls.ranking_functions:
            return "entity.name.function.ranking.vespa"
        # Extra math functions for TextMate readability
        if name in _TM_EXTRA_RANKING_FUNCTIONS:
            return "entity.name.function.ranking.vespa"
        lsp = cls.ranking_type_map.get(name)
        if lsp:
            # "function" → entity.name.function, "number"/"string" handled by patterns
            if lsp == "function":
                return "entity.name.function.ranking.vespa"
            # number and string are handled by generic patterns, not keyword rules
        return None

    return None


# ---------------------------------------------------------------------------
# 1d  Build TextMate regex patterns from keyword groups
# ---------------------------------------------------------------------------

def _has_hyphen(s: str) -> bool:
    return "-" in s


# Keywords that are also container-type prefixes need (?!\s*<) to avoid
# consuming the keyword before the container-type begin pattern can fire.
_CONTAINER_TYPE_KEYWORDS = {"array", "weightedset", "map", "reference", "annotationreference"}


def _keyword_pattern(words: list[str], hyphenated_words: list[str] | None = None) -> str:
    r"""Build a \b(word1|word2|...)\b pattern for simple keywords.

    If *hyphenated_words* is given, any simple word that is a prefix of a
    hyphenated keyword gets a negative lookahead ``(?!-)`` so that e.g.
    ``rank`` does not greedily match the ``rank`` inside ``rank-profile``.
    """
    escaped = sorted(set(words), key=lambda w: (-len(w), w))
    # Identify simple words that are prefixes of hyphenated keywords
    hyph_prefixes: set[str] = set()
    if hyphenated_words:
        for hw in hyphenated_words:
            first_part = hw.split("-")[0]
            if first_part in set(words):
                hyph_prefixes.add(first_part)
    parts: list[str] = []
    for w in escaped:
        esc = re.escape(w)
        lookaheads = ""
        if w in hyph_prefixes:
            lookaheads += r"(?!-)"
        if w in _CONTAINER_TYPE_KEYWORDS:
            lookaheads += r"(?!\s*<)"
        parts.append(esc + lookaheads if lookaheads else esc)
    return r"\b(" + "|".join(parts) + r")\b"


def _hyphenated_pattern(words: list[str]) -> str:
    """Build a pattern for hyphenated keywords that can't use \\b."""
    escaped = sorted(set(words), key=lambda w: (-len(w), w))
    return r"(?<![a-zA-Z0-9_-])(" + "|".join(re.escape(w) for w in escaped) + r")(?![a-zA-Z0-9_-])"


# ---------------------------------------------------------------------------
# 1e  Build the complete TextMate grammar JSON
# ---------------------------------------------------------------------------

def build_grammar(
    schema_tokens: list[TokenInfo],
    indexing_tokens: list[TokenInfo],
    ranking_tokens: list[TokenInfo],
    builtin_rank_features: list[str],
    cls: Classification,
) -> dict:
    """Build the complete TextMate grammar dictionary."""

    # Group classified tokens by (source, scope) → list of literals
    # Tokens with scope=None (unclassified) are skipped.
    groups: dict[tuple[str, str], list[str]] = {}
    for tok in schema_tokens + indexing_tokens + ranking_tokens:
        scope = classify_token(tok, cls)
        if scope is None:
            continue
        key = (tok.source, scope)
        groups.setdefault(key, []).append(tok.literal)

    def _rules_for_scope(source: str, scope: str) -> list[dict]:
        literals = groups.get((source, scope), [])
        if not literals:
            return []
        simple = [w for w in literals if not _has_hyphen(w)]
        hyphen = [w for w in literals if _has_hyphen(w)]
        rules = []
        # Hyphenated patterns MUST come first so that e.g. "rank-profile"
        # is matched before the simple keyword "rank".
        if hyphen:
            rules.append({"name": scope, "match": _hyphenated_pattern(hyphen)})
        if simple:
            rules.append({"name": scope, "match": _keyword_pattern(simple, hyphen)})
        return rules

    # =====================================================================
    #  Repository entries
    # =====================================================================

    repository: dict[str, dict] = {}

    # --- comment ---
    repository["comment"] = {
        "name": "comment.line.number-sign.vespa",
        "match": r"(#).*$",
        "captures": {
            "1": {"name": "punctuation.definition.comment.vespa"},
        },
    }

    # --- strings ---
    repository["string-double"] = {
        "name": "string.quoted.double.vespa",
        "begin": r'"',
        "end": r'"',
        "beginCaptures": {
            "0": {"name": "punctuation.definition.string.begin.vespa"},
        },
        "endCaptures": {
            "0": {"name": "punctuation.definition.string.end.vespa"},
        },
        "patterns": [
            {"name": "constant.character.escape.vespa", "match": r'\\.'},
        ],
    }
    repository["string-single"] = {
        "name": "string.quoted.single.vespa",
        "begin": r"'",
        "end": r"'",
        "beginCaptures": {
            "0": {"name": "punctuation.definition.string.begin.vespa"},
        },
        "endCaptures": {
            "0": {"name": "punctuation.definition.string.end.vespa"},
        },
        "patterns": [
            {"name": "constant.character.escape.vespa", "match": r'\\.'},
        ],
    }

    # --- tensor type: tensor<float>(x[384]) ---
    # Break the tensor type into subcomponents for rich highlighting:
    #   tensor  → keyword.control
    #   float   → storage.type.tensor (value type: float, double, bfloat16, int8, etc.)
    #   x       → variable.other.dimension (dimension name)
    #   384     → constant.numeric (dimension size)
    #   {}      → mapped dimension marker
    #   < > ( ) [ ] , → punctuation
    repository["tensor-type"] = {
        "begin": r"\b(tensor)\s*(<)",
        "end": r"(\))",
        "beginCaptures": {
            "1": {"name": "keyword.control.vespa"},
            "2": {"name": "punctuation.definition.typeparameters.begin.vespa"},
        },
        "endCaptures": {
            "1": {"name": "punctuation.definition.typeparameters.end.vespa"},
        },
        "patterns": [
            # Value type inside angle brackets: float, double, bfloat16, int8, etc.
            {
                "match": r"\b(float|double|bfloat16|int8)\b",
                "name": "storage.type.tensor.vespa",
            },
            # Closing > and opening (
            {
                "match": r">",
                "name": "punctuation.definition.typeparameters.vespa",
            },
            {
                "match": r"\(",
                "name": "punctuation.definition.typeparameters.vespa",
            },
            # Dimension size inside brackets: [384]
            {
                "match": r"\[(\d+)\]",
                "captures": {
                    "1": {"name": "constant.numeric.vespa"},
                },
            },
            # Mapped dimension marker: {}
            {
                "match": r"\{\}",
                "name": "punctuation.definition.dimension.vespa",
            },
            # Dimension name (identifier before [ or {)
            {
                "match": r"\b([a-zA-Z_][a-zA-Z_0-9]*)\b",
                "name": "support.variable.vespa",
            },
            # Comma separator
            {
                "match": r",",
                "name": "punctuation.separator.vespa",
            },
        ],
    }

    # --- numeric literals (LSP: Number → constant.numeric) ---
    # Lookbehind excludes alphanumerics, preventing matches inside identifiers like bm25, log10
    repository["numeric-literal"] = {
        "patterns": [
            {
                "name": "constant.numeric.vespa",
                "match": r"\b(infinity)\b",
            },
            {
                "name": "constant.numeric.float.vespa",
                "match": r"(?<![a-zA-Z_0-9])(-?\d+\.\d*(?:[eE][+-]?\d+)?[fFdD]?)\b",
            },
            {
                "name": "constant.numeric.integer.vespa",
                "match": r"(?<![a-zA-Z_0-9])(-?(?:0[xX][0-9a-fA-F]+|\d+)[lL]?)\b",
            },
        ],
    }

    # --- ON/OFF/TRUE/FALSE (LSP: Type → support.type, teal) ---
    # on(?!-) prevents matching "on" in "on-match", "on-first-phase" etc.
    repository["boolean-constants"] = {
        "name": "support.type.vespa",
        "match": r"\b(on(?!-)|off|true|false)\b",
    }

    # --- variable reference: $identifier ---
    repository["variable-reference"] = {
        "name": "variable.language.vespa",
        "match": r"(\$)([a-zA-Z_][a-zA-Z0-9_]*)",
        "captures": {
            "1": {"name": "punctuation.definition.variable.vespa"},
            "2": {"name": "variable.language.vespa"},
        },
    }

    # --- punctuation ---
    repository["punctuation"] = {
        "patterns": [
            {"name": "punctuation.section.block.begin.vespa", "match": r"\{"},
            {"name": "punctuation.section.block.end.vespa", "match": r"\}"},
            {"name": "punctuation.separator.colon.vespa", "match": r":"},
            {"name": "punctuation.separator.comma.vespa", "match": r","},
            {"name": "punctuation.separator.dot.vespa", "match": r"\."},
        ],
    }

    # --- container types: array<T>, weightedset<T>, map<K,V>, reference<T> ---
    # begin/end so that the angle brackets form a balanced scope and inner
    # types (including nested containers) are highlighted correctly.
    repository["container-type"] = {
        "begin": r"\b(array|weightedset|map|reference|annotationreference)\s*(<)",
        "beginCaptures": {
            "1": {"name": "storage.type.vespa"},
            "2": {"name": "punctuation.definition.typeparameters.begin.vespa"},
        },
        "end": r"(>)",
        "endCaptures": {
            "1": {"name": "punctuation.definition.typeparameters.end.vespa"},
        },
        "patterns": [
            {"include": "#container-type"},
            {"include": "#primitive-type"},
            {"include": "#tensor-type"},
            {"name": "punctuation.separator.vespa", "match": r","},
            # User-defined type names (person, mystruct, etc.) — must be last
            {"name": "entity.name.type.vespa", "match": r"\b[a-zA-Z_]\w*\b"},
        ],
    }

    # --- primitive types (LSP colors these via dataType/valueType AST nodes → Type) ---
    # NOTE: uri is excluded — Java LSP treats it as keyword, not type.
    # raw is excluded — it can appear in non-type contexts.
    repository["primitive-type"] = {
        "name": "support.type.vespa",
        "match": r"\b(int|byte|bool|string|double|float|long|position|tag|predicate)\b",
    }

    # =====================================================================
    #  Declaration name patterns (Phase 1+2+4)
    #  Captures keyword + name + optional inherits target
    # =====================================================================

    _IDENT = r"[a-zA-Z_]\w*"
    _IDENT_DASH = r"[a-zA-Z_][\w-]*"

    declaration_patterns: list[dict] = []

    # schema NAME [inherits PARENT]
    declaration_patterns.append({
        "match": r"\b(schema)\s+(" + _IDENT + r")(?:\s+(inherits)\s+(" + _IDENT + r"))?",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.type.vespa"},
            "3": {"name": "keyword.control.vespa"},
            "4": {"name": "entity.other.inherited-class.vespa"},
        },
    })

    # document NAME [inherits PARENT]
    declaration_patterns.append({
        "match": r"\b(document)\s+(" + _IDENT + r")(?:\s+(inherits)\s+(" + _IDENT + r"))?",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.type.vespa"},
            "3": {"name": "keyword.control.vespa"},
            "4": {"name": "entity.other.inherited-class.vespa"},
        },
    })

    # field NAME type
    declaration_patterns.append({
        "match": r"\b(field)\s+(" + _IDENT + r")\s+(type)\b",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.other.vespa"},
            "3": {"name": "keyword.control.vespa"},
        },
    })

    # struct NAME
    declaration_patterns.append({
        "match": r"\b(struct)\s+(" + _IDENT + r")",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.type.vespa"},
        },
    })

    # rank-profile NAME [inherits PARENT]
    declaration_patterns.append({
        "match": r"(?<![a-zA-Z0-9_-])(rank-profile)\s+(" + _IDENT_DASH + r")(?:\s+(inherits)\s+(" + _IDENT_DASH + r"))?",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.function.vespa"},
            "3": {"name": "keyword.control.vespa"},
            "4": {"name": "entity.other.inherited-class.vespa"},
        },
    })

    # function [inline] NAME(args)
    declaration_patterns.append({
        "match": r"\b(function)\s+(?:(inline)\s+)?(" + _IDENT + r")\s*(\([^)]*\))",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "storage.modifier.vespa"},
            "3": {"name": "entity.name.function.vespa"},
            "4": {"name": "variable.parameter.vespa"},
        },
    })

    # annotation NAME [inherits PARENT]
    declaration_patterns.append({
        "match": r"\b(annotation)\s+(" + _IDENT + r")(?:\s+(inherits)\s+(" + _IDENT + r"))?",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.type.vespa"},
            "3": {"name": "keyword.control.vespa"},
            "4": {"name": "entity.other.inherited-class.vespa"},
        },
    })

    # document-summary NAME [inherits PARENT]
    declaration_patterns.append({
        "match": r"(?<![a-zA-Z0-9_-])(document-summary)\s+(" + _IDENT_DASH + r")(?:\s+(inherits)\s+(" + _IDENT_DASH + r"))?",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.type.vespa"},
            "3": {"name": "keyword.control.vespa"},
            "4": {"name": "entity.other.inherited-class.vespa"},
        },
    })

    # fieldset NAME
    declaration_patterns.append({
        "match": r"\b(fieldset)\s+(" + _IDENT + r")",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.other.vespa"},
        },
    })

    # struct-field NAME
    declaration_patterns.append({
        "match": r"(?<![a-zA-Z0-9_-])(struct-field)\s+(" + _IDENT + r")",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.other.vespa"},
        },
    })

    # import field
    declaration_patterns.append({
        "match": r"\b(import)\s+(field)\b",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "keyword.declaration.vespa"},
        },
    })

    # constant NAME
    declaration_patterns.append({
        "match": r"\b(constant)\s+(" + _IDENT + r")",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.other.vespa"},
        },
    })

    # onnx-model NAME
    declaration_patterns.append({
        "match": r"(?<![a-zA-Z0-9_-])(onnx-model)\s+(" + _IDENT_DASH + r")",
        "captures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.other.vespa"},
        },
    })

    repository["declarations"] = {"patterns": declaration_patterns}

    # =====================================================================
    #  Indexing language rules (matches SchemaSemanticTokens lines 156-173)
    # =====================================================================

    indexing_patterns: list[dict] = [
        {"include": "#comment"},
        {"include": "#string-double"},
        {"include": "#string-single"},
        {"include": "#numeric-literal"},
    ]
    # Order matches Java: outputs (Type) → keywords (Keyword) → operators (Function)
    indexing_patterns.extend(_rules_for_scope("indexing", "support.type.indexing.vespa"))
    indexing_patterns.extend(_rules_for_scope("indexing", "keyword.control.indexing.vespa"))
    indexing_patterns.extend(_rules_for_scope("indexing", "entity.name.function.indexing.vespa"))
    # Pipe operator
    indexing_patterns.append({
        "name": "keyword.operator.pipe.indexing.vespa",
        "match": r"\|",
    })
    indexing_patterns.append({"include": "#punctuation"})

    repository["indexing-language"] = {"patterns": indexing_patterns}

    # --- indexing block: indexing { ... } ---
    repository["indexing-block"] = {
        "name": "meta.block.indexing.vespa",
        "begin": r"\b(indexing)\s*(\{)",
        "beginCaptures": {
            "1": {"name": "keyword.control.vespa"},
            "2": {"name": "punctuation.section.block.begin.vespa"},
        },
        "end": r"(\})",
        "endCaptures": {
            "1": {"name": "punctuation.section.block.end.vespa"},
        },
        "patterns": [{"include": "#indexing-language"}],
    }

    # --- indexing inline: indexing: ... $ ---
    repository["indexing-inline"] = {
        "name": "meta.inline.indexing.vespa",
        "begin": r"\b(indexing)\s*(:)",
        "beginCaptures": {
            "1": {"name": "keyword.control.vespa"},
            "2": {"name": "punctuation.separator.vespa"},
        },
        "end": r"$",
        "patterns": [{"include": "#indexing-language"}],
    }

    # =====================================================================
    #  Ranking expression rules (matches SchemaSemanticTokens lines 150-154)
    # =====================================================================

    ranking_patterns: list[dict] = [
        {"include": "#comment"},
        {"include": "#string-double"},
        {"include": "#string-single"},
        {"include": "#numeric-literal"},
        {"include": "#tensor-type"},
        {"include": "#primitive-type"},
        {"include": "#variable-reference"},
    ]
    # Keywords (Keyword → blue)
    ranking_patterns.extend(_rules_for_scope("ranking", "keyword.control.ranking.vespa"))
    # Functions (Function → yellow) — only the ones explicitly in Java lists
    ranking_patterns.extend(_rules_for_scope("ranking", "entity.name.function.ranking.vespa"))
    # Built-in rank features (bm25, fieldMatch, onnx, etc.)
    # These are resolved as FUNCTION symbols by the Java LSP; add them to
    # TM so they get colored even without a running language server.
    if builtin_rank_features:
        # Match feature_name followed by ( to avoid false positives
        escaped = sorted(builtin_rank_features, key=lambda w: (-len(w), w))
        simple = [w for w in escaped if "_" not in w]
        underscored = [w for w in escaped if "_" in w]
        if simple:
            ranking_patterns.append({
                "name": "entity.name.function.rank-feature.vespa",
                "match": r"\b(" + "|".join(simple) + r")\s*(?=\()",
            })
        if underscored:
            ranking_patterns.append({
                "name": "entity.name.function.rank-feature.vespa",
                "match": r"\b(" + "|".join(underscored) + r")\s*(?=\()",
            })
    # Symbolic operators (handled separately since they're not keyword literals)
    # Dot is an operator in ranking expressions (Java LSP: DOT → Operator)
    ranking_patterns.append({
        "name": "keyword.operator.ranking.vespa",
        "match": r"[+\-*/%^.]|>=|<=|!=|==|~=|&&|\|\||[<>!]",
    })
    # Parenthesized argument lists: (field, embedding), (title), etc.
    ranking_patterns.append({
        "begin": r"\(",
        "beginCaptures": {"0": {"name": "punctuation.section.parens.begin.vespa"}},
        "end": r"\)",
        "endCaptures": {"0": {"name": "punctuation.section.parens.end.vespa"}},
        "patterns": [{"include": "#ranking-expression"}],
    })
    # Brace groups for tensor literals / nested braces in ranking expressions
    ranking_patterns.append({
        "begin": r"\{",
        "beginCaptures": {"0": {"name": "punctuation.section.block.begin.vespa"}},
        "end": r"\}",
        "endCaptures": {"0": {"name": "punctuation.section.block.end.vespa"}},
        "patterns": [{"include": "#ranking-expression"}],
    })
    # Catch-all identifier — must be last so all specific rules take priority.
    # Colors user-defined function names, field references, etc.
    ranking_patterns.append({
        "name": "support.variable.vespa",
        "match": r"\b[a-zA-Z_]\w*\b",
    })
    ranking_patterns.append({"include": "#punctuation"})

    repository["ranking-expression"] = {"patterns": ranking_patterns}

    # --- expression block: expression { ... } ---
    repository["expression-block"] = {
        "name": "meta.block.expression.vespa",
        "begin": r"\b(expression)\s*(\{)",
        "beginCaptures": {
            "1": {"name": "keyword.control.vespa"},
            "2": {"name": "punctuation.section.block.begin.vespa"},
        },
        "end": r"(\})",
        "endCaptures": {
            "1": {"name": "punctuation.section.block.end.vespa"},
        },
        "patterns": [{"include": "#ranking-expression"}],
    }

    # --- expression inline: expression: ... $ ---
    repository["expression-inline"] = {
        "name": "meta.inline.expression.vespa",
        "begin": r"\b(expression)\s*(:)",
        "beginCaptures": {
            "1": {"name": "keyword.control.vespa"},
            "2": {"name": "punctuation.separator.vespa"},
        },
        "end": r"$",
        "patterns": [{"include": "#ranking-expression"}],
    }

    # --- feature list block: match-features { ... } ---
    feature_kws = "match-features|summary-features|rank-features"
    feature_lookbehind = r"(?<![a-zA-Z0-9_-])"
    repository["feature-list-block"] = {
        "name": "meta.block.feature-list.vespa",
        "begin": feature_lookbehind + r"(" + feature_kws + r")(?:\s+(inherits)\s+(" + _IDENT_DASH + r"))?\s*(\{)",
        "beginCaptures": {
            "1": {"name": "keyword.control.vespa"},
            "2": {"name": "keyword.control.vespa"},
            "3": {"name": "entity.other.inherited-class.vespa"},
            "4": {"name": "punctuation.section.block.begin.vespa"},
        },
        "end": r"(\})",
        "endCaptures": {
            "1": {"name": "punctuation.section.block.end.vespa"},
        },
        "patterns": [{"include": "#ranking-expression"}],
    }

    # --- feature list inline: match-features: ... $ ---
    repository["feature-list-inline"] = {
        "name": "meta.inline.feature-list.vespa",
        "begin": feature_lookbehind + r"(" + feature_kws + r")\s*(:)",
        "beginCaptures": {
            "1": {"name": "keyword.control.vespa"},
            "2": {"name": "punctuation.separator.vespa"},
        },
        "end": r"$",
        "patterns": [{"include": "#ranking-expression"}],
    }

    # --- rank-properties block: rank-properties { ... } ---
    # Content is feature.property: value pairs.  Java LSP treats dots
    # as operators here.
    rank_props_lookbehind = r"(?<![a-zA-Z0-9_-])"
    repository["rank-properties-block"] = {
        "name": "meta.block.rank-properties.vespa",
        "begin": rank_props_lookbehind + r"(rank\-properties)\s*(\{)",
        "beginCaptures": {
            "1": {"name": "keyword.control.vespa"},
            "2": {"name": "punctuation.section.block.begin.vespa"},
        },
        "end": r"(\})",
        "endCaptures": {
            "1": {"name": "punctuation.section.block.end.vespa"},
        },
        "patterns": [
            {"include": "#comment"},
            {"include": "#string-double"},
            {"include": "#string-single"},
            {"include": "#numeric-literal"},
            {"include": "#primitive-type"},
            {"include": "#ranking-expression"},
        ],
    }

    # =====================================================================
    #  Schema-level rules
    # =====================================================================

    # Keywords (LSP: Keyword → blue)
    schema_keyword_rules = _rules_for_scope("schema", "keyword.control.vespa")
    repository["schema-keywords"] = {"patterns": schema_keyword_rules}

    # query → Function (LSP: Function → yellow)
    schema_fn_rules = _rules_for_scope("schema", "entity.name.function.vespa")
    if schema_fn_rules:
        repository["schema-functions"] = {"patterns": schema_fn_rules}

    # parallel, sequential → EnumMember (LSP: EnumMember → light blue)
    schema_enum_rules = _rules_for_scope("schema", "variable.other.enummember.vespa")
    if schema_enum_rules:
        repository["schema-enum-members"] = {"patterns": schema_enum_rules}

    # --- enum-like inline values (Java isEnumLike → Property + Readonly) ---
    # Patterns like "match: word", "rank: literal", "rank-type: identity"
    # where the value after the colon is a property-scoped enum value.
    # Also handles match { token } block form.
    enum_value_kws = (
        "token|word|exact|text|gram|prefix|substring|suffix|cased|uncased"  # matchType / matchItem
        "|literal|identity|tags"  # rankTypeElm / fieldRankType
        "|source|bolding|full|static|dynamic|tokens|matched-elements-only"  # summaryItem
        "|angular|dotproduct|euclidean|prenormalized-angular|hamming|geodegrees"  # distance-metric values
        "|best|shortest|multiple|none"  # stemming values
        "|primary|secondary|tertiary|quaternary|identical"  # sorting strength
        "|lowercase|raw"  # sorting function
        "|ascending|descending"  # order values
        "|always|on-demand|never"  # summary-to values
        "|normal|contextual"  # summary override values
    )
    # The left side covers all keyword: value patterns seen in schemas
    enum_value_lhs = (
        r"match|rank|rank-type|sorting|bolding|stemming|summary-to"
        r"|distance-metric|normalizing|function|locale|strength|order"
    )
    repository["enum-value-inline"] = {
        "match": r"(?<![a-zA-Z0-9_-])(" + enum_value_lhs + r")(:\s*)(" + enum_value_kws + r")(?![a-zA-Z0-9_-])",
        "captures": {
            "1": {"name": "keyword.control.vespa"},
            "2": {"name": "punctuation.separator.vespa"},
            "3": {"name": "variable.other.enummember.vespa"},
        },
    }

    # Match block: match { token } — value on its own line
    repository["match-block"] = {
        "name": "meta.block.match.vespa",
        "begin": r"\b(match)\s*(\{)",
        "beginCaptures": {
            "1": {"name": "keyword.control.vespa"},
            "2": {"name": "punctuation.section.block.begin.vespa"},
        },
        "end": r"(\})",
        "endCaptures": {
            "1": {"name": "punctuation.section.block.end.vespa"},
        },
        "patterns": [
            {"include": "#comment"},
            {"include": "#string-double"},
            {"include": "#string-single"},
            {"include": "#numeric-literal"},
            {"include": "#enum-value-inline"},
            {
                "name": "variable.other.enummember.vespa",
                "match": r"\b(" + enum_value_kws + r")\b",
            },
            {"include": "#schema-keywords"},
            {"include": "#punctuation"},
        ],
    }

    # Summary block: summary { bolding: on, source: ... }
    # Matches "summary {", "summary name {", "summary name inherits parent {"
    # Captures name and inherits target for highlighting.
    repository["summary-block"] = {
        "name": "meta.block.summary.vespa",
        "begin": r"\b(summary)\s+(?:([a-zA-Z_]\w*)\s+(?:(inherits)\s+([a-zA-Z_]\w*)\s*)?)?(\{)",
        "beginCaptures": {
            "1": {"name": "keyword.declaration.vespa"},
            "2": {"name": "entity.name.other.vespa"},
            "3": {"name": "keyword.control.vespa"},
            "4": {"name": "entity.other.inherited-class.vespa"},
            "5": {"name": "punctuation.section.block.begin.vespa"},
        },
        "end": r"(\})",
        "endCaptures": {
            "1": {"name": "punctuation.section.block.end.vespa"},
        },
        "patterns": [
            {"include": "#comment"},
            {"include": "#string-double"},
            {"include": "#string-single"},
            {"include": "#numeric-literal"},
            {"include": "#boolean-constants"},
            {"include": "#feature-list-block"},
            {"include": "#feature-list-inline"},
            {"include": "#enum-value-inline"},
            {
                "name": "variable.other.enummember.vespa",
                "match": r"\b(" + enum_value_kws + r")\b",
            },
            {"include": "#schema-keywords"},
            {"include": "#punctuation"},
        ],
    }

    # --- assignment operator (mutate blocks: timestamp += 3600) ---
    repository["assignment-operator"] = {
        "name": "keyword.operator.assignment.vespa",
        "match": r"[+\-*/]?=",
    }

    # --- user-defined type after 'type' keyword ---
    # Catches type names that aren't primitive, container, or tensor
    # e.g. "field author type person {" → "person" gets colored
    repository["user-type"] = {
        "match": r"(?<=\btype\s)(\s*[a-zA-Z_]\w*)",
        "captures": {
            "1": {"name": "entity.name.type.vespa"},
        },
    }

    # =====================================================================
    #  Top-level patterns (order matters!)
    # =====================================================================

    patterns: list[dict] = [
        {"include": "#comment"},
        {"include": "#string-double"},
        {"include": "#string-single"},
        {"include": "#tensor-type"},
        {"include": "#indexing-block"},
        {"include": "#indexing-inline"},
        {"include": "#expression-block"},
        {"include": "#expression-inline"},
        {"include": "#feature-list-block"},
        {"include": "#feature-list-inline"},
        {"include": "#rank-properties-block"},
        {"include": "#match-block"},
        {"include": "#summary-block"},
        {"include": "#enum-value-inline"},
        {"include": "#container-type"},
        {"include": "#primitive-type"},
        {"include": "#numeric-literal"},
        {"include": "#boolean-constants"},
        {"include": "#declarations"},
        {"include": "#schema-keywords"},
        {"include": "#user-type"},
        {"include": "#assignment-operator"},
    ]
    if schema_fn_rules:
        patterns.append({"include": "#schema-functions"})
    if schema_enum_rules:
        patterns.append({"include": "#schema-enum-members"})
    patterns.extend([
        {"include": "#variable-reference"},
        {"include": "#punctuation"},
    ])

    return {
        "$schema": "https://raw.githubusercontent.com/martinring/tmlanguage/master/tmlanguage.json",
        "name": "Vespa Schema",
        "scopeName": "source.vespaSchema",
        "patterns": patterns,
        "repository": repository,
    }


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    # Parse tokens
    schema_tokens = parse_ccc_tokens(SCHEMA_CCC, "schema")
    indexing_tokens = parse_ccc_tokens(INDEXING_CCC, "indexing")
    ranking_tokens = parse_ccc_tokens(RANKING_CCC, "ranking")

    print(f"Schema tokens:  {len(schema_tokens)} keywords extracted")
    print(f"Indexing tokens: {len(indexing_tokens)} keywords extracted")
    print(f"Ranking tokens: {len(ranking_tokens)} keywords extracted")

    # Parse classification
    cls = parse_semantic_config(SEMANTIC_CFG)
    print(f"\nClassification from SemanticTokenConfig.java:")
    print(f"  Schema keywords:        {len(cls.schema_keywords)}")
    print(f"  Schema type map:        {len(cls.schema_type_map)}")
    print(f"  Indexing keywords:      {len(cls.indexing_keywords)}")
    print(f"  Indexing outputs:       {len(cls.indexing_outputs)}")
    print(f"  Indexing operators:     {len(cls.indexing_operators)}")
    print(f"  Ranking keywords:       {len(cls.ranking_keywords)}")
    print(f"  Ranking functions:      {len(cls.ranking_functions)}")
    print(f"  Ranking operators:      {len(cls.ranking_operators)}")
    print(f"  Ranking type map:       {len(cls.ranking_type_map)}")

    # Parse built-in rank features
    builtin_rank_features = parse_builtin_functions(BUILTIN_FUNCS)
    print(f"  Built-in rank features: {len(builtin_rank_features)}")

    # Report classified vs unclassified
    classified = []
    unclassified = []
    for tok in schema_tokens + indexing_tokens + ranking_tokens:
        scope = classify_token(tok, cls)
        if scope is not None:
            classified.append((tok, scope))
        else:
            unclassified.append(tok)

    print(f"\nClassified tokens:   {len(classified)} (will be colored)")
    print(f"Unclassified tokens: {len(unclassified)} (no color, matching LSP)")
    if unclassified:
        for tok in unclassified:
            print(f"  {tok.source}/{tok.name} ({tok.literal!r})")

    # Build grammar
    grammar = build_grammar(schema_tokens, indexing_tokens, ranking_tokens, builtin_rank_features, cls=cls)

    # Write output
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(grammar, indent=2, ensure_ascii=False) + "\n")
    print(f"\nWrote {OUTPUT}")


if __name__ == "__main__":
    main()
