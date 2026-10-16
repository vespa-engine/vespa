# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# /// script
# requires-python = ">=3.10"
# ///
"""
Validate the auto-generated TextMate grammar for Vespa YQL.

Tests:
  1. Keyword completeness — every token the LSP configs classify appears in the grammar
  2. Structural validity — JSON structure, include resolution, regex compilation
  3. Scope spot-checks — known queries get the expected scopes

Usage:
    uv run tools/test_yql_tmgrammar.py
"""

from __future__ import annotations

import json
import re
import sys

from generate_yql_tmgrammar import (GROUPING_CCC, GROUPING_CFG, OUTPUT, YQL_CCC, YQL_CFG, parse_class_config,
                                    parse_grouping_tokens, parse_yql_tokens, production_tokens)

GROUPING = "select * from doc where true | "

# Query → [(text, expected scope without ".yql", occurrence of the text if not the first)]
SPOT_CHECKS = {
    "select id from music where ({targetHits: 10}nearestNeighbor(embedding, q)) and year >= 2000 order by year desc": [
        ("select", "keyword.control"), ("music", "variable.other"), ("targetHits", "variable.other"),
        ("10", "constant.numeric"), ("nearestNeighbor", "entity.name.function"), ("and", "keyword.operator.wordlike"),
        (">=", "keyword.operator"), ("order by", "keyword.control")],
    'SELECT * FROM sources * WHERE title CONTAINS "a"': [
        ("SELECT", "keyword.control"), ("CONTAINS", "keyword.control")],
    "select * from doc where a contains @query": [("@query", "variable.parameter")],
    "select * from doc where a != 1 and b not in (1, 2)": [("!=", "keyword.operator"), ("not in", "keyword.operator.wordlike")],
    "select * from doc where title contains 'x' // comment": [(" comment", "comment.line.double-slash")],
    # Comments start with // or #, as in the query parser
    "select foo from bar where # false | x\n true": [
        (" false | x", "comment.line.number-sign"), ("true", "support.type")],
    # Unary minus before a name or call, and names containing "-"
    "select * from doc where -price < 0 and !(-userQuery()) and my-field contains 'x' and from-date > 1": [
        ("price", "variable.other"), ("userQuery", "entity.name.function"), ("my-field", "variable.other"),
        ("from-date", "variable.other")],
    # Grouping, and a query after it, which is YQL again
    GROUPING + "all(group(artist) max(5) each(output(count(), avg(math.sqrt(price)))))\nselect * from doc where a": [
        ("true", "support.type"), ("all", "keyword.control"), ("group", "entity.name.function"),
        ("each", "keyword.control"), ("count", "entity.name.function"), ("math", "support.class"),
        ("sqrt", "entity.name.function"), ("select", "keyword.control", 2)],
    # Grouping has # comments, "-" as an operator, numbers right after operators, and its own number syntax
    GROUPING + "all(group(a) # it's a comment": [(" it's a comment", "comment.line.number-sign")],
    GROUPING + "all(group(1-2) order(-count()) each(output(sum(foo-bar))))": [
        ("2", "constant.numeric"), ("count", "entity.name.function"), ("foo", "variable.other"),
        ("-", "keyword.operator", 3)],
    GROUPING + "all(group(a) max(1.5f) precision(2.0D) each(output(sum(0x1F))))": [
        ("1.5f", "constant.numeric"), ("2.0D", "constant.numeric"), ("0x1F", "constant.numeric")],
    # A line starting with select inside grouping parentheses does not end grouping
    GROUPING + "all(group(\n  select\n) # comment\n each(output(count())))": [
        ("select", "variable.other", 2), (" comment", "comment.line.number-sign"), ("each", "keyword.control")],
    # Bucket ranges may mix delimiters, and the query after them is YQL again. Kept apart, since a range
    # left open and one closed too early would cancel out.
    GROUPING + "all(group(predefined(foo, bucket[1, 2>, bucket(3, 4>)) each(output(count())))\nselect * from doc": [
        ("select", "keyword.control", 2)],
    GROUPING + "all(group(predefined(foo, bucket<0, 100))) each(output(count())))\nselect * from doc": [
        ("each", "keyword.control"), ("select", "keyword.control", 2)],
    # Query parameters in grouping
    GROUPING + "all(group(f) filter(regex(@pattern, f)) each(output(sum(@my-param_1))))": [
        ("@pattern", "variable.parameter"), ("@my-param_1", "variable.parameter")],
    # Grouping on its own, without a query and "|" before it, starts at a line beginning with all( or each(
    'all(group(a) filter(in(a, "x", "y") and istrue(b)) each(output(argmax(c, count()))))': [
        ("all", "keyword.control"), ("in", "keyword.operator.wordlike"), ("and", "keyword.operator.wordlike"),
        ("istrue", "keyword.operator.wordlike"), ("argmax", "entity.name.function"), ("each", "keyword.control")],
    "  each(output(count())) as(total)": [("each", "keyword.control"), ("as", "keyword.control")],
    # but not in the middle of a line
    "select * from doc where all(a)": [("all", "entity.name.function")],
}


# ---------------------------------------------------------------------------
# A minimal TextMate tokenizer: match and begin/end rules, captures and includes, line by line
# ---------------------------------------------------------------------------

def _rules(grammar: dict, patterns: list) -> list:
    out = []
    for p in patterns:
        if "include" in p:
            p = grammar["repository"][p["include"].lstrip("#")]
        if "patterns" in p and "match" not in p and "begin" not in p:
            out.extend(_rules(grammar, p["patterns"]))
        else:
            out.append(p)
    return out


def tokenize(grammar: dict, text: str) -> list[tuple[str, str | None]]:
    """(text, innermost scope) for each piece; text no rule matches gets the enclosing rule's name."""
    result, stack = [], []  # stack: open begin/end rules

    def emit(m, name, captures):
        pos = m.start()
        for idx, cap in sorted(captures.items(), key=lambda c: m.start(int(c[0]))):
            s, e = m.span(int(idx))
            if s >= pos and s < e:
                result.extend([(m.string[pos:s], name)] if s > pos else [])
                result.append((m.string[s:e], cap.get("name") or name))
                pos = e
        if m.end() > pos:
            result.append((m.string[pos:m.end()], name))

    for line in text.split("\n"):
        pos = 0
        while pos <= len(line):
            enclosing = stack[-1].get("name") if stack else None
            candidates = [(m.start(), 0, "end", stack[-1], m)
                          for m in [stack and re.compile(stack[-1]["end"]).search(line, pos)] if m]
            for i, rule in enumerate(_rules(grammar, stack[-1].get("patterns", []) if stack else grammar["patterns"])):
                if m := re.compile(rule.get("match") or rule["begin"]).search(line, pos):
                    candidates.append((m.start(), i + 1, "begin" if "begin" in rule else "match", rule, m))
            if not candidates:
                result.extend([(line[pos:], enclosing)] if pos < len(line) else [])
                break
            start, _, kind, rule, m = min(candidates, key=lambda c: c[:2])
            if start > pos:
                result.append((line[pos:start], enclosing))
            if kind == "end":
                emit(m, enclosing, rule.get("endCaptures", {}))
                stack.pop()
            else:
                emit(m, rule.get("name") or enclosing, rule.get("beginCaptures" if kind == "begin" else "captures", {}))
                stack += [rule] if kind == "begin" else []
            pos = m.end()
    return result


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

def test_keyword_completeness(grammar: dict) -> tuple[bool, list[str]]:
    text = json.dumps(grammar)
    yql_tokens, grouping_tokens = parse_yql_tokens(YQL_CCC), parse_grouping_tokens(GROUPING_CCC)
    keywords, yql_map, _ = parse_class_config(YQL_CFG)
    _, grouping_map, _ = parse_class_config(GROUPING_CFG)
    yql = {n: yql_tokens.get(n) for n in keywords | {t for c in yql_map for t in production_tokens(YQL_CCC, c) or [c]}}
    errors = [f"  yql/{n}: {lit!r} classified in Java but missing from grammar" for n, lit in sorted(yql.items())
              if lit and re.fullmatch(r"[a-z ]+", lit) and re.escape(lit).replace(r"\ ", r"\\s+") not in text]
    errors += [f"  grouping/{n}: {grouping_tokens[n]!r} classified in Java but missing from grammar"
               for n in sorted(grouping_map) if grouping_tokens.get(n, "").isalpha() and grouping_tokens[n] not in text]
    return not errors, errors


def test_structural_validity(grammar: dict) -> tuple[bool, list[str]]:
    required = ("scopeName", "name", "patterns", "repository")
    errors = [f"  Missing required field: {f}" for f in required if f not in grammar]

    def walk(node):
        if isinstance(node, list):
            for value in node:
                walk(value)
        elif isinstance(node, dict):
            if node.get("include", "#").lstrip("#") not in {"", *grammar.get("repository", {})}:
                errors.append(f"  Unresolved include: {node['include']}")
            for key in ("match", "begin", "end"):
                try:
                    re.compile(node.get(key, ""))
                except re.error as e:
                    errors.append(f"  Invalid regex in {key}: {node[key]!r}: {e}")
            for value in node.values():
                walk(value)

    walk(grammar)
    return not errors, errors


def test_scope_spotchecks(grammar: dict) -> tuple[bool, list[str]]:
    errors = []
    for query, checks in SPOT_CHECKS.items():
        scopes = [scope for piece, scope in tokenize(grammar, query)]
        pieces = [piece for piece, scope in tokenize(grammar, query)]
        for target, expected, *occurrence in checks:
            matches = [i for i, piece in enumerate(pieces) if piece == target]
            n = occurrence[0] if occurrence else 1
            actual = scopes[matches[n - 1]] if len(matches) >= n else "(not found)"
            if actual != expected + ".yql":
                errors.append(f"  {target!r} in {query[:60]!r}: expected {expected}.yql, got {actual}")
    return not errors, errors


def main() -> int:
    if not OUTPUT.exists():
        print(f"FAIL: Grammar file not found: {OUTPUT}\n      Run: uv run tools/generate_yql_tmgrammar.py")
        return 1
    grammar = json.loads(OUTPUT.read_text())
    all_passed = True
    for name, test in (("Test 1: Keyword completeness", test_keyword_completeness),
                       ("Test 2: Structural validity", test_structural_validity),
                       ("Test 3: Scope spot-checks", test_scope_spotchecks)):
        passed, messages = test(grammar)
        print(f"{'PASS' if passed else 'FAIL'}: {name}", *messages, sep="\n")
        all_passed = all_passed and passed
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
