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
                                    parse_yql_tokens, production_tokens)
from generate_tmgrammar import parse_ccc_tokens


# ---------------------------------------------------------------------------
# A minimal TextMate tokenizer: match and begin/end rules, captures and includes, line by line
# ---------------------------------------------------------------------------

def _rules(grammar: dict, patterns: list) -> list:
    out = []
    for p in patterns:
        if "include" in p:
            entry = grammar["repository"][p["include"].lstrip("#")]
            out.extend(_rules(grammar, entry["patterns"]) if "patterns" in entry and "begin" not in entry else [entry])
        elif "patterns" in p and "match" not in p and "begin" not in p:
            out.extend(_rules(grammar, p["patterns"]))
        else:
            out.append(p)
    return out


def tokenize(grammar: dict, text: str) -> list[tuple[str, str | None]]:
    """(text, innermost scope) for each piece; text no rule matches gets the enclosing rule's name."""
    result = []
    stack = []  # open begin/end rules

    def emit(m, name, captures):
        pos = m.start()
        for idx, cap in sorted(captures.items(), key=lambda c: m.start(int(c[0]))):
            s, e = m.span(int(idx))
            if s < pos or s == e:
                continue
            if s > pos:
                result.append((m.string[pos:s], name))
            result.append((m.string[s:e], cap.get("name") or name))
            pos = e
        if m.end() > pos:
            result.append((m.string[pos:m.end()], name))

    for line in text.split("\n"):
        pos = 0
        while pos <= len(line):
            enclosing = stack[-1].get("name") if stack else None
            rules = _rules(grammar, stack[-1].get("patterns", []) if stack else grammar["patterns"])
            candidates = []
            if stack and (m := re.compile(stack[-1]["end"]).search(line, pos)):
                candidates.append((m.start(), 0, "end", stack[-1], m))
            for i, rule in enumerate(rules):
                if m := re.compile(rule.get("match") or rule["begin"]).search(line, pos):
                    candidates.append((m.start(), i + 1, "begin" if "begin" in rule else "match", rule, m))
            if not candidates:
                if pos < len(line):
                    result.append((line[pos:], enclosing))
                break
            start, _, kind, rule, m = min(candidates, key=lambda c: (c[0], c[1]))
            if start > pos:
                result.append((line[pos:start], enclosing))
            if kind == "end":
                emit(m, enclosing, rule.get("endCaptures", {}))
                stack.pop()
            elif kind == "begin":
                emit(m, rule.get("name") or enclosing, rule.get("beginCaptures", {}))
                stack.append(rule)
            else:
                emit(m, rule.get("name") or enclosing, rule.get("captures", {}))
            pos = m.end()
    return result


def scope_of(grammar: dict, text: str, target: str, occurrence: int = 1) -> str | None:
    seen = 0
    for piece, scope in tokenize(grammar, text):
        if piece == target:
            seen += 1
            if seen == occurrence:
                return scope
    return "(not found)"


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

def test_keyword_completeness(grammar: dict) -> tuple[bool, list[str]]:
    errors = []
    text = json.dumps(grammar)
    yql_tokens = parse_yql_tokens(YQL_CCC)
    keywords, yql_map = parse_class_config(YQL_CFG)
    expected = set(keywords) | {t for cls in yql_map for t in (production_tokens(YQL_CCC, cls) or [cls])}
    for name in sorted(expected):
        literal = yql_tokens.get(name)
        if literal and re.fullmatch(r"[a-z ]+", literal) and re.escape(literal).replace(r"\ ", r"\\s+") not in text:
            errors.append(f"  yql/{name}: {literal!r} classified in Java but missing from grammar")
    grouping = {t.name: t.literal for t in parse_ccc_tokens(GROUPING_CCC, "grouping")}
    _, grouping_map = parse_class_config(GROUPING_CFG)
    for name in sorted(grouping_map):
        literal = grouping.get(name)
        if literal and literal.isalpha() and literal not in text:
            errors.append(f"  grouping/{name}: {literal!r} classified in Java but missing from grammar")
    return not errors, errors


def test_structural_validity(grammar: dict) -> tuple[bool, list[str]]:
    errors = []
    for field in ("scopeName", "name", "patterns", "repository"):
        if field not in grammar:
            errors.append(f"  Missing required field: {field}")

    def walk(node):
        if isinstance(node, dict):
            if "include" in node and node["include"].lstrip("#") not in grammar.get("repository", {}):
                errors.append(f"  Unresolved include: {node['include']}")
            for key in ("match", "begin", "end"):
                if key in node:
                    try:
                        re.compile(node[key])
                    except re.error as e:
                        errors.append(f"  Invalid regex in {key}: {node[key]!r}: {e}")
            for value in node.values():
                walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(grammar)
    return not errors, errors


def test_scope_spotchecks(grammar: dict) -> tuple[bool, list[str]]:
    query = 'select id from music where ({targetHits: 10}nearestNeighbor(embedding, q)) and year >= 2000 order by year desc'
    grouping = 'select * from music where true | all(group(artist) max(5) each(output(count(), avg(math.sqrt(price)))))'
    checks = [
        (query, "select", 1, "keyword.control.yql"),
        (query, "music", 1, "variable.other.yql"),
        (query, "targetHits", 1, "variable.other.yql"),
        (query, "10", 1, "constant.numeric.yql"),
        (query, "nearestNeighbor", 1, "entity.name.function.yql"),
        (query, "and", 1, "keyword.operator.yql"),
        (query, ">=", 1, "keyword.operator.yql"),
        (query, "order by", 1, "keyword.control.yql"),
        ('SELECT * FROM sources * WHERE title CONTAINS "a"', "SELECT", 1, "keyword.control.yql"),
        ('SELECT * FROM sources * WHERE title CONTAINS "a"', "CONTAINS", 1, "keyword.control.yql"),
        ('select * from doc where a contains @query', "@", 1, "entity.name.function.yql"),
        ('select * from doc where a contains @query', "query", 1, "variable.other.yql"),
        ('select * from doc where a != 1 and b = 2', "!=", 1, "keyword.operator.yql"),
        ('select * from doc where a not in (1, 2)', "not in", 1, "keyword.operator.yql"),
        ("select * from doc where title contains 'x' // comment", " comment", 1, "comment.line.double-slash.yql"),
        (grouping, "true", 1, "support.type.yql"),
        (grouping, "all", 1, "keyword.control.yql"),
        (grouping, "group", 1, "entity.name.function.yql"),
        (grouping, "each", 1, "keyword.control.yql"),
        (grouping, "count", 1, "entity.name.function.yql"),
        (grouping, "math", 1, "support.class.yql"),
        (grouping, "sqrt", 1, "entity.name.function.yql"),
        # A query after a grouping expression is YQL again
        (grouping + "\nselect * from doc where a", "select", 2, "keyword.control.yql"),
        # Grouping has # comments, "-" as an operator, and numbers right after operators
        ("select * from doc where true | all(group(a) # it's a comment", " it's a comment", 1, "comment.line.number-sign.yql"),
        ("select * from doc where true | all(group(a) order(-count()))", "count", 1, "entity.name.function.yql"),
        ("select * from doc where true | all(group(a) each(output(sum(foo-bar))))", "foo", 1, "variable.other.yql"),
        ("select * from doc where true | all(group(a) each(output(sum(foo-bar))))", "-", 1, "keyword.operator.yql"),
        ("select * from doc where true | all(group(1-2))", "2", 1, "constant.numeric.yql"),
    ]
    errors = []
    for text, target, occurrence, expected in checks:
        actual = scope_of(grammar, text, target, occurrence)
        if actual != expected:
            errors.append(f"  {target!r} in {text[:60]!r}: expected {expected}, got {actual}")
    return not errors, errors


def main() -> int:
    if not OUTPUT.exists():
        print(f"FAIL: Grammar file not found: {OUTPUT}")
        print("      Run: uv run tools/generate_yql_tmgrammar.py")
        return 1

    grammar = json.loads(OUTPUT.read_text())

    all_passed = True
    tests = [
        ("Test 1: Keyword completeness", test_keyword_completeness),
        ("Test 2: Structural validity", test_structural_validity),
        ("Test 3: Scope spot-checks", test_scope_spotchecks),
    ]
    for name, test_fn in tests:
        passed, messages = test_fn(grammar)
        print(f"{'PASS' if passed else 'FAIL'}: {name}")
        for msg in messages:
            print(msg)
        all_passed = all_passed and passed
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
