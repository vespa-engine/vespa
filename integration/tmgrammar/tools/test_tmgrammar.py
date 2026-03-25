# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# /// script
# requires-python = ">=3.10"
# ///
"""
Validate the auto-generated TextMate grammar for the Vespa schema language.

Tests:
  1. Keyword completeness — every .ccc literal keyword appears in the grammar
  2. Structural validity — JSON structure, include resolution, regex compilation
  3. Scope assignment spot-checks — known inputs get expected scopes
  4. Parse all .sd test files — grammar patterns don't error on real files

Usage:
    uv run tools/test_tmgrammar.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

TMGRAMMAR = Path(__file__).resolve().parent.parent
VESPA_ROOT = TMGRAMMAR.parents[1]
LSP = VESPA_ROOT / "integration" / "schema-language-server" / "language-server"
GRAMMAR_PATH = TMGRAMMAR / "grammar/vespa-schema.tmLanguage.json"
SCHEMA_CCC = LSP / "src/main/ccc/SchemaParser.ccc"
INDEXING_CCC = LSP / "src/main/ccc/indexinglanguage/IndexingParser.ccc"
RANKING_CCC = LSP / "src/main/ccc/rankingexpression/RankingExpressionParser.ccc"
SD_TEST_DIR = LSP / "src/test/sdfiles"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

_LITERAL_TOKEN_RE = re.compile(
    r'<\s*([A-Z_][A-Z_0-9]*)\s*:\s*"([^"]+)"\s*>'
)

# Tokens that are handled specially (not as keywords)
_SPECIAL_HANDLED = {
    # Numeric types: matched by numeric-literal pattern
    "DOUBLE", "INTEGER", "LONG", "FLOAT",
    # String types: matched by string patterns
    "DOUBLEQUOTEDSTRING", "SINGLEQUOTEDSTRING", "STRING",
    # Complex patterns: handled by dedicated rules
    "TENSOR_TYPE", "TENSOR_VALUE_SL", "TENSOR_VALUE_ML",
    # Reference tokens
    "VARIABLE", "IDENTIFIER", "IDENTIFIER_WITH_DASH", "CONTEXT",
    "FILE_PATH", "HTTP", "URI_PATH",
    # Newline
    "NL",
    # Numeric keyword
    "INFINITY",
}


def _is_keyword_literal(literal: str) -> bool:
    return bool(re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_-]*", literal))


def extract_ccc_keywords(path: Path) -> dict[str, str]:
    """Return {token_name: literal} for all keyword tokens in a .ccc file."""
    text = path.read_text()
    text = re.sub(r"UNPARSED\s*:.*?;", "", text, flags=re.DOTALL)
    result = {}
    for m in _LITERAL_TOKEN_RE.finditer(text):
        name, literal = m.group(1), m.group(2)
        if name.startswith("#") or name in _SPECIAL_HANDLED:
            continue
        if not _is_keyword_literal(literal):
            continue
        result[name] = literal
    return result


def extract_grammar_words(grammar: dict) -> set[str]:
    """Extract all words that appear in match patterns in the grammar."""
    words: set[str] = set()
    _collect_words(grammar, words)
    return words


def _extract_group_content(pattern: str) -> list[str]:
    """Extract content of top-level parenthesized groups, handling nested parens."""
    groups = []
    depth = 0
    start = -1
    for i, ch in enumerate(pattern):
        if ch == '(' and (i == 0 or pattern[i-1] != '\\'):
            if depth == 0:
                start = i + 1
            depth += 1
        elif ch == ')' and (i == 0 or pattern[i-1] != '\\'):
            depth -= 1
            if depth == 0 and start >= 0:
                groups.append(pattern[start:i])
                start = -1
    return groups


def _collect_words(obj, words: set[str]) -> None:
    if isinstance(obj, dict):
        if "match" in obj:
            # Extract words from regex alternation groups
            pattern = obj["match"]
            # Find words inside top-level (...|...) groups (handles nested parens)
            for group in _extract_group_content(pattern):
                for alt in group.split("|"):
                    # Strip inline lookaheads like (?!-) and non-capturing group prefixes
                    clean = re.sub(r"\(\?[!=][^)]*\)", "", alt)
                    clean = re.sub(r"\(\?:", "", clean)
                    # Remove regex escaping
                    clean = clean.replace("\\-", "-").replace("\\.", ".").replace("\\b", "")
                    clean = re.sub(r"\\[{}()\[\]]", "", clean)
                    clean = clean.strip()
                    if clean and re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_-]*", clean):
                        words.add(clean)
        for v in obj.values():
            _collect_words(v, words)
    elif isinstance(obj, list):
        for item in obj:
            _collect_words(item, words)


def collect_includes(obj, includes: set[str]) -> None:
    """Collect all #reference includes from the grammar."""
    if isinstance(obj, dict):
        if "include" in obj:
            ref = obj["include"]
            if ref.startswith("#"):
                includes.add(ref[1:])
        for v in obj.values():
            collect_includes(v, includes)
    elif isinstance(obj, list):
        for item in obj:
            collect_includes(item, includes)


def collect_all_regexes(obj, regexes: list[str]) -> None:
    """Collect all match/begin/end regex patterns."""
    if isinstance(obj, dict):
        for key in ("match", "begin", "end"):
            if key in obj and isinstance(obj[key], str):
                regexes.append(obj[key])
        for v in obj.values():
            collect_all_regexes(v, regexes)
    elif isinstance(obj, list):
        for item in obj:
            collect_all_regexes(item, regexes)


# ---------------------------------------------------------------------------
# Test 1: Classified keyword completeness
#
# Verifies that every token classified in SchemaSemanticTokenConfig.java
# appears in the generated grammar.  Unclassified tokens are intentionally
# omitted (the LSP doesn't color them either).
# ---------------------------------------------------------------------------

SEMANTIC_CFG = LSP / "src/main/java/ai/vespa/schemals/lsp/schema/semantictokens/SchemaSemanticTokenConfig.java"


def _parse_java_classified_tokens() -> dict[str, set[str]]:
    """Return {source: {token_name, ...}} for every token in a Java list."""
    java = SEMANTIC_CFG.read_text()

    def _names(pattern: str) -> set[str]:
        m = re.search(
            rf"{re.escape(pattern)}\s*=\s*new\s+\w+[^{{]*\{{\{{\s*(.*?)\}}\}};",
            java, re.DOTALL,
        )
        return set(re.findall(r"TokenType\.(\w+)", m.group(1))) if m else set()

    schema = _names("keywordTokens")
    for key in ("schemaTokenTypeLSPNameMap",):
        schema |= _names(key)

    indexing = _names("indexingLanguageKeywords") | _names("indexingLanguageOutputs") | _names("indexingLanguageOperators")
    ranking = _names("rankingExpressionKeywordTokens") | _names("rankingExpressioFunctionTokens") | _names("rankingExpressionOperationTokens") | _names("rankingExpressionTokenTypeLSPNameMap")

    return {"schema": schema, "indexing": indexing, "ranking": ranking}


def test_keyword_completeness(grammar: dict) -> tuple[bool, list[str]]:
    errors: list[str] = []
    grammar_words = extract_grammar_words(grammar)
    classified = _parse_java_classified_tokens()

    for ccc_path, label in [
        (SCHEMA_CCC, "schema"),
        (INDEXING_CCC, "indexing"),
        (RANKING_CCC, "ranking"),
    ]:
        keywords = extract_ccc_keywords(ccc_path)
        classified_names = classified.get(label, set())
        for name, literal in keywords.items():
            if name not in classified_names:
                continue  # intentionally uncolored
            if literal not in grammar_words:
                errors.append(f"  {label}/{name}: {literal!r} classified in Java but missing from grammar")

    return len(errors) == 0, errors


# ---------------------------------------------------------------------------
# Test 2: Structural validity
# ---------------------------------------------------------------------------

def test_structural_validity(grammar: dict) -> tuple[bool, list[str]]:
    errors: list[str] = []

    # Required top-level fields
    for field in ("scopeName", "name", "patterns", "repository"):
        if field not in grammar:
            errors.append(f"  Missing required field: {field}")

    # All #includes resolve
    includes: set[str] = set()
    collect_includes(grammar, includes)
    repo_keys = set(grammar.get("repository", {}).keys())
    for ref in sorted(includes):
        if ref not in repo_keys:
            errors.append(f"  Unresolved include: #{ref}")

    # All regexes compile
    regexes: list[str] = []
    collect_all_regexes(grammar, regexes)
    for pattern in regexes:
        try:
            re.compile(pattern)
        except re.error as e:
            errors.append(f"  Invalid regex {pattern!r}: {e}")

    # No empty alternation groups
    for pattern in regexes:
        if "||" in pattern and "\\|\\|" not in pattern:
            errors.append(f"  Empty alternation in: {pattern!r}")
        # Check for truly empty groups () but not lookaheads (?=...) or escaped parens \(\)
        if re.search(r'(?<!\\)\((?!\?)(?!\\)\)', pattern):
            errors.append(f"  Empty group in: {pattern!r}")

    return len(errors) == 0, errors


# ---------------------------------------------------------------------------
# Test 3: Scope assignment spot-checks
# ---------------------------------------------------------------------------

def _find_match(grammar: dict, text: str, repo_key: str | None = None) -> str | None:
    """Try to match text against patterns. Returns the matched scope name or None."""
    if repo_key:
        entry = grammar.get("repository", {}).get(repo_key, {})
        # Handle begin/end patterns at the repository entry level
        if "begin" in entry:
            try:
                if re.search(entry["begin"], text):
                    return entry.get("name", "matched-no-name")
            except re.error:
                pass
            return None
        # Handle match patterns at the repository entry level
        if "match" in entry and "patterns" not in entry:
            try:
                if re.search(entry["match"], text):
                    return entry.get("name", "matched-no-name")
            except re.error:
                pass
            return None
        # Handle patterns container
        patterns = entry.get("patterns", [])
    else:
        patterns = grammar.get("patterns", [])

    return _match_patterns(grammar, patterns, text)


def _match_patterns(grammar: dict, patterns, text: str) -> str | None:
    if isinstance(patterns, dict):
        if "begin" in patterns:
            try:
                if re.search(patterns["begin"], text):
                    return patterns.get("name", "matched-no-name")
            except re.error:
                pass
            return None
        if "match" in patterns and "patterns" not in patterns:
            try:
                if re.search(patterns["match"], text):
                    return patterns.get("name", "matched-no-name")
            except re.error:
                pass
            return None
        patterns = patterns.get("patterns", [])

    for pat in patterns:
        if "include" in pat:
            ref = pat["include"]
            if ref.startswith("#"):
                repo_entry = grammar.get("repository", {}).get(ref[1:], {})
                result = _match_patterns(grammar, repo_entry, text)
                if result:
                    return result
            continue

        if "match" in pat:
            try:
                if re.search(pat["match"], text):
                    return pat.get("name", "matched-no-name")
            except re.error:
                continue

        if "begin" in pat:
            try:
                if re.search(pat["begin"], text):
                    return pat.get("name", "matched-no-name")
            except re.error:
                continue

    return None


def _find_capture_scope(grammar: dict, text: str, target: str, repo_key: str) -> str | None:
    """Find the scope assigned to `target` substring via captures in repo_key patterns."""
    entry = grammar.get("repository", {}).get(repo_key, {})

    # Check the entry itself first (handles begin/end patterns with beginCaptures)
    candidates = [entry]
    # Then check sub-patterns
    if "patterns" in entry:
        candidates.extend(entry["patterns"])
    elif "patterns" not in entry:
        # entry is a plain match pattern, already in candidates
        pass

    for pat in candidates:
        regex_str = pat.get("match") or pat.get("begin")
        captures = pat.get("captures") or pat.get("beginCaptures", {})
        if not regex_str or not captures:
            continue
        try:
            m = re.search(regex_str, text)
        except re.error:
            continue
        if not m:
            continue
        for idx_str, scope_info in captures.items():
            idx = int(idx_str)
            try:
                if m.group(idx) == target:
                    return scope_info.get("name")
            except IndexError:
                continue
    return None


def test_scope_spotchecks(grammar: dict) -> tuple[bool, list[str]]:
    errors: list[str] = []

    checks = [
        # Schema keywords → keyword.control (blue)
        ("schema foo {", "keyword.control.vespa", "schema-keywords"),
        # Comments → comment.line (green)
        ("# this is a comment", "comment.line.number-sign.vespa", "comment"),
        # Strings
        ('"hello world"', "string.quoted.double.vespa", "string-double"),
        ("'single quoted'", "string.quoted.single.vespa", "string-single"),
        # Tensor type — begin/end pattern, check via begin match
        ("tensor<float>(x[384])", "matched-no-name", "tensor-type"),
        # Numerics → constant.numeric (light green)
        ("42", "constant.numeric.integer.vespa", "numeric-literal"),
        ("3.14", "constant.numeric.float.vespa", "numeric-literal"),
        # Variables
        ("$myvar", "variable.language.vespa", "variable-reference"),
        # ON/OFF/TRUE/FALSE → support.type (teal, matching LSP Type)
        ("true", "support.type.vespa", "boolean-constants"),
    ]

    for text, expected_scope, repo_key in checks:
        result = _find_match(grammar, text, repo_key)
        if result != expected_scope:
            errors.append(f"  {text!r} in #{repo_key}: expected {expected_scope}, got {result}")

    # --- Capture-based checks ---
    capture_checks = [
        # Tensor keyword → keyword.control (begin capture)
        ("tensor<float>(x[384])", "tensor", "keyword.control.vespa", "tensor-type"),
        # Comment # → punctuation
        ("# comment", "#", "punctuation.definition.comment.vespa", "comment"),
        # Variable $ → punctuation
        ("$myvar", "$", "punctuation.definition.variable.vespa", "variable-reference"),

        # Phase 1: Declaration name highlighting
        ("schema foo {", "foo", "entity.name.type.vespa", "declarations"),
        ("document bar {", "bar", "entity.name.type.vespa", "declarations"),
        ("field myField type int", "myField", None, "declarations"),
        ("field myField type int", "type", "keyword.control.vespa", "declarations"),
        ("struct myStruct {", "myStruct", "entity.name.type.vespa", "declarations"),
        ("rank-profile prof {", "prof", "entity.name.function.vespa", "declarations"),
        ("function fn(x)", "fn", "entity.name.function.vespa", "declarations"),
        ("annotation myAnnotation {", "myAnnotation", "entity.name.type.vespa", "declarations"),
        ("document-summary ds {", "ds", "entity.name.type.vespa", "declarations"),
        ("fieldset fs {", "fs", None, "declarations"),
        ("struct-field sf {", "sf", None, "declarations"),
        ("constant myConst {", "myConst", None, "declarations"),
        ("onnx-model myModel {", "myModel", None, "declarations"),

        # Phase 2: Inherits target highlighting
        ("schema foo inherits bar", "bar", "entity.other.inherited-class.vespa", "declarations"),
        ("document doc inherits parent", "parent", "entity.other.inherited-class.vespa", "declarations"),
        ("rank-profile child inherits base", "base", "entity.other.inherited-class.vespa", "declarations"),
        ("document-summary ds inherits base-ds", "base-ds", "entity.other.inherited-class.vespa", "declarations"),

        # Phase 4: keyword.declaration for declaration keywords
        ("schema foo {", "schema", "keyword.declaration.vespa", "declarations"),
        ("document bar {", "document", "keyword.declaration.vespa", "declarations"),
        ("field x type int", "field", "keyword.declaration.vespa", "declarations"),
        ("rank-profile p {", "rank-profile", "keyword.declaration.vespa", "declarations"),
        ("function fn()", "function", "keyword.declaration.vespa", "declarations"),
        ("import field x", "import", "keyword.declaration.vespa", "declarations"),
    ]

    for text, target, expected_scope, repo_key in capture_checks:
        result = _find_capture_scope(grammar, text, target, repo_key)
        if result != expected_scope:
            errors.append(f"  capture {target!r} in {text!r} #{repo_key}: expected {expected_scope}, got {result}")

    # Check indexing context (matches Java: outputs→Type, keywords→Keyword, operators→Function)
    idx_checks = [
        ("index", "support.type.indexing.vespa"),          # output → Type (teal)
        ("summary", "support.type.indexing.vespa"),        # output → Type (teal)
        ("attribute", "support.type.indexing.vespa"),      # output → Type (teal)
        ("embed", "entity.name.function.indexing.vespa"),  # operator → Function (yellow)
        ("lowercase", "entity.name.function.indexing.vespa"),
        ("tokenize", "entity.name.function.indexing.vespa"),
        ("if", "keyword.control.indexing.vespa"),          # keyword → Keyword (blue)
        ("for_each", "keyword.control.indexing.vespa"),
    ]
    for text, expected_scope in idx_checks:
        result = _find_match(grammar, text, "indexing-language")
        if result != expected_scope:
            errors.append(f"  indexing {text!r}: expected {expected_scope}, got {result}")

    # Check ranking context
    rank_checks = [
        # Unary math functions → Function in TextMate (extra classification for readability)
        ("cos", "entity.name.function.ranking.vespa"),
        ("sigmoid", "entity.name.function.ranking.vespa"),
        # Binary functions → Function (yellow, explicitly in rankingExpressioFunctionTokens)
        ("atan2", "entity.name.function.ranking.vespa"),
        ("pow", "entity.name.function.ranking.vespa"),
        # Tensor ops → Keyword (blue)
        ("reduce", "keyword.control.ranking.vespa"),
        ("tensor", "keyword.control.ranking.vespa"),
        ("if", "keyword.control.ranking.vespa"),
        # isNan → Function (via rankingExpressionTokenTypeLSPNameMap)
        ("isNan", "entity.name.function.ranking.vespa"),
    ]
    for text, expected_scope in rank_checks:
        result = _find_match(grammar, text, "ranking-expression")
        if result != expected_scope:
            errors.append(f"  ranking {text!r}: expected {expected_scope}, got {result}")

    return len(errors) == 0, errors


# ---------------------------------------------------------------------------
# Test 4: Parse all .sd test files
# ---------------------------------------------------------------------------

def test_sd_file_coverage(grammar: dict) -> tuple[bool, list[str]]:
    """Check that grammar doesn't false-positive on identifiers inside .sd files.

    Specifically: numeric patterns must NOT match inside identifiers like bm25, log10.
    Also collects keyword match stats for sanity checking.
    """
    errors: list[str] = []

    # Collect keyword regexes (match patterns with keyword/function/type scopes)
    keyword_patterns: list[re.Pattern] = []
    _collect_keyword_patterns(grammar, keyword_patterns)

    # Collect numeric regexes
    numeric_patterns: list[re.Pattern] = []
    for pat_obj in grammar.get("repository", {}).get("numeric-literal", {}).get("patterns", []):
        if "match" in pat_obj:
            try:
                numeric_patterns.append(re.compile(pat_obj["match"]))
            except re.error:
                pass

    # Find .sd files
    sd_files = list(SD_TEST_DIR.rglob("*.sd")) if SD_TEST_DIR.exists() else []
    if not sd_files:
        errors.append("  No .sd test files found")
        return False, errors

    file_count = len(sd_files)
    total_keywords_found = 0
    false_numeric_hits = 0

    # Known identifiers that contain digits — numeric patterns must NOT match inside them
    digit_identifiers = {"bm25", "log10", "l1_normalize", "l2_normalize", "base64decode",
                         "base64encode"}

    for sd_file in sd_files:
        try:
            text = sd_file.read_text()
        except (UnicodeDecodeError, OSError):
            continue

        # Count keyword hits per file for sanity
        for kp in keyword_patterns:
            total_keywords_found += len(kp.findall(text))

        # Check for false numeric matches inside identifiers
        words = set(re.findall(r"[a-zA-Z_][a-zA-Z0-9_]*", text))
        for word in words & digit_identifiers:
            for np in numeric_patterns:
                m = np.search(word)
                if m:
                    false_numeric_hits += 1
                    errors.append(f"  Numeric false positive: {word!r} matched {m.group()!r} in {sd_file.name}")

    info = f"  Scanned {file_count} .sd files, {total_keywords_found} keyword matches"
    if false_numeric_hits:
        errors.insert(0, info)
        return False, errors

    errors.append(info)
    return True, errors


def _collect_keyword_patterns(obj, patterns: list[re.Pattern]) -> None:
    """Collect compiled keyword/function/type match patterns from grammar."""
    if isinstance(obj, dict):
        if "match" in obj and "name" in obj:
            name = obj["name"]
            if any(s in name for s in ("keyword.", "support.", "entity.", "storage.")):
                try:
                    patterns.append(re.compile(obj["match"]))
                except re.error:
                    pass
        for v in obj.values():
            _collect_keyword_patterns(v, patterns)
    elif isinstance(obj, list):
        for item in obj:
            _collect_keyword_patterns(item, patterns)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> int:
    if not GRAMMAR_PATH.exists():
        print(f"FAIL: Grammar file not found: {GRAMMAR_PATH}")
        print("      Run: uv run tools/generate_tmgrammar.py")
        return 1

    grammar = json.loads(GRAMMAR_PATH.read_text())

    all_passed = True
    tests = [
        ("Test 1: Keyword completeness", test_keyword_completeness),
        ("Test 2: Structural validity", test_structural_validity),
        ("Test 3: Scope assignment spot-checks", test_scope_spotchecks),
        ("Test 4: .sd file coverage", test_sd_file_coverage),
    ]

    for name, test_fn in tests:
        passed, messages = test_fn(grammar)
        status = "PASS" if passed else "FAIL"
        print(f"{status}: {name}")
        for msg in messages:
            print(msg)
        if not passed:
            all_passed = False

    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
