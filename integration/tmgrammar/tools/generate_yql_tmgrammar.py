# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# /// script
# requires-python = ">=3.10"
# ///
"""
Auto-generate a TextMate grammar for Vespa YQL, including the grouping language.

Reads token declarations from the CongoCC grammars of the YQL and grouping parsers, and scope
classifications from YQLPlusSemanticTokenConfig.java and VespaGroupingSemanticTokenConfig.java,
then emits grammars/vespa-yql.tmLanguage.json.

Like the schema grammar, the scopes follow the colors the Java LSP produces in VS Code's default
Dark+ theme, with two additions:
  - A name followed by "(" is colored as a function. The LSP colors function names such as
    nearestNeighbor or userQuery as plain variables.
  - = and != are colored as operators, like < and >. The LSP leaves them uncolored.

YQL keywords are case-insensitive, as in the query parser. The grouping language is case-sensitive.

Usage:
    uv run tools/generate_yql_tmgrammar.py
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from generate_tmgrammar import LSP, TMGRAMMAR, _LSP_TO_TM, parse_ccc_tokens

YQL_CCC = LSP / "src/main/ccc/yqlplus/YQLPlus.ccc"
GROUPING_CCC = LSP / "src/main/ccc/grouping/GroupingParser.ccc"
SEMANTIC_DIR = LSP / "src/main/java/ai/vespa/schemals/lsp/yqlplus/semantictokens"
YQL_CFG = SEMANTIC_DIR / "YQLPlusSemanticTokenConfig.java"
GROUPING_CFG = SEMANTIC_DIR / "VespaGroupingSemanticTokenConfig.java"
OUTPUT = TMGRAMMAR / "grammars/vespa-yql.tmLanguage.json"

# LSP SemanticTokenType → TextMate scope, extended with the types the YQL configs use.
LSP_TO_TM = {
    **_LSP_TO_TM,
    "Variable": "variable.other",
    "Method": "entity.name.function",
    "Class": "support.class",
}

IDENT = r"[a-zA-Z_][\w-]*"
# YQL identifiers may contain "-", so words are delimited by lookarounds rather than \b.
WORD_START = r"(?<![\w-])"
WORD_END = r"(?![\w-])"
# Grouping identifiers may contain "@" but not "-", which is the minus operator there.
GROUPING_IDENT = r"[a-zA-Z][\w@]*"
GROUPING_START = r"(?<![\w@])"
GROUPING_END = r"(?![\w@])"


# ---------------------------------------------------------------------------
# 1  Read the parsers and the LSP token configuration
# ---------------------------------------------------------------------------

_YQL_TOKEN_RE = re.compile(r"<\s*([A-Z_][A-Z_0-9]*)\s*:\s*((?:'[^']*'|<[A-Z_]+>|\s)+?)\s*>")


def parse_yql_tokens(path: Path) -> dict[str, str]:
    """Literal YQL tokens, name → literal. Tokens built from other tokens, like
    < ORDERBY: <ORDER> ' ' <BY> >, are resolved to their full literal ("order by")."""
    text = re.sub(r"UNPARSED\s*:.*?;", "", path.read_text(), flags=re.DOTALL)
    definitions = {m.group(1): m.group(2) for m in _YQL_TOKEN_RE.finditer(text)}
    resolved: dict[str, str] = {}

    def resolve(name: str, seen: frozenset = frozenset()) -> str | None:
        if name in resolved:
            return resolved[name]
        if name not in definitions or name in seen:
            return None
        parts = []
        for literal, ref in re.findall(r"'([^']*)'|<([A-Z_]+)>", definitions[name]):
            part = literal if not ref else resolve(ref, seen | {name})
            if part is None:
                return None
            parts.append(part)
        resolved[name] = "".join(parts)
        return resolved[name]

    for name in definitions:
        resolve(name)
    return resolved


def production_tokens(path: Path, production: str) -> list[str]:
    """Names of the tokens referenced in a production, e.g. mult_op → STAR, DIV, MODULO."""
    m = re.search(rf"^{production}\s*:(.*?)^;", path.read_text(), flags=re.DOTALL | re.MULTILINE)
    return re.findall(r"<([A-Z_]+)>", m.group(1)) if m else []


def parse_class_config(path: Path) -> tuple[set[str], dict[str, str]]:
    """Keyword classes and class → LSP token type from a YQL semantic token config. Values may be
    SemanticTokenTypes.X or a String constant defined as one."""
    java = path.read_text()
    constants = dict(re.findall(r"String\s+(\w+)\s*=\s*SemanticTokenTypes\.(\w+)\s*;", java))
    keywords = set(re.findall(r"add\((\w+)\.class\)", java))
    tokens = {}
    for cls, value in re.findall(r"put\((\w+)\.class\s*,\s*([\w.]+)\s*\)", java):
        tokens[cls] = constants.get(value, value.removeprefix("SemanticTokenTypes."))
    return keywords, tokens


def parse_parent_config(path: Path) -> dict[str, str]:
    """Token → LSP type for tokens colored by their parent, e.g. AND in andPredicate. A token with
    several parents gets the type of its first entry."""
    java = path.read_text()
    constants = dict(re.findall(r"String\s+(\w+)\s*=\s*SemanticTokenTypes\.(\w+)\s*;", java))
    result: dict[str, str] = {}
    for cls, value in re.findall(r"new Pair<>\((\w+)\.class\s*,\s*\w+\.class\)\s*,\s*([\w.]+)\s*\)", java):
        result.setdefault(cls, constants.get(value, value.removeprefix("SemanticTokenTypes.")))
    return result


# ---------------------------------------------------------------------------
# 2  Build the grammar
# ---------------------------------------------------------------------------

def scope(lsp_type: str) -> str:
    return LSP_TO_TM[lsp_type] + ".yql"


def words(literals, ignore_case: bool = False, start: str = WORD_START, end: str = WORD_END) -> str:
    """A pattern matching any of the words, longest first. Spaces in a word match any whitespace."""
    alternatives = sorted(set(literals), key=lambda w: (-len(w), w))
    body = "|".join(re.escape(w).replace(r"\ ", r"\s+") for w in alternatives)
    pattern = f"{start}({body}){end}"
    return f"(?i:{pattern})" if ignore_case else pattern


def symbols(literals) -> str:
    alternatives = sorted(set(literals), key=lambda s: (-len(s), s))
    return "|".join(re.escape(s) for s in alternatives)


def build_grammar() -> dict:
    yql_tokens = parse_yql_tokens(YQL_CCC)
    grouping_tokens = {t.name: t.literal for t in parse_ccc_tokens(GROUPING_CCC, "grouping")}
    grouping_tokens.update({"EQ": "=", "LT": "<", "GT": ">", "INFIX_ADD": "+", "INFIX_SUB": "-",
                            "INFIX_MUL": "*", "INFIX_DIV": "/", "INFIX_MOD": "%"})
    yql_keywords, yql_map = parse_class_config(YQL_CFG)
    _, grouping_map = parse_class_config(GROUPING_CFG)
    grouping_parent = parse_parent_config(GROUPING_CFG)

    def yql_literals(names):
        return [yql_tokens[n] for n in names if n in yql_tokens]

    # YQL: keywords, word and symbol operators, and boolean constants
    keyword_literals = yql_literals(yql_keywords)
    operator_tokens = [t for cls, typ in yql_map.items() if typ == "Operator"
                       for t in (production_tokens(YQL_CCC, cls) or [cls])]
    # The LSP does not color = and !=, but colors <, > and the ! of !=. Color them like the others.
    operator_tokens += production_tokens(YQL_CCC, "equality_op")
    operator_literals = [lit for lit in yql_literals(operator_tokens) if lit not in keyword_literals]
    word_operators = [lit for lit in operator_literals if re.fullmatch(r"[a-z ]+", lit)]
    symbol_operators = [lit for lit in operator_literals if lit not in word_operators]
    type_literals = yql_literals(cls for cls, typ in yql_map.items() if typ == "Type")

    # Grouping: tokens by LSP type
    def grouping_literals(typ, source=grouping_map):
        return [grouping_tokens[cls] for cls, t in source.items() if t == typ and cls in grouping_tokens]

    time_methods = [grouping_tokens[c] for c, t in grouping_map.items() if t == "Method" and c.startswith("TIME_")]
    math_methods = [grouping_tokens[c] for c, t in grouping_map.items()
                    if t == "Method" and not c.startswith("TIME_") and c not in ("X", "Y")]
    zcurve_methods = [grouping_tokens[c] for c in ("X", "Y") if grouping_map.get(c) == "Method"]
    grouping_functions = grouping_literals("Function") + grouping_literals("Macro")
    grouping_word_operators = [lit for lit in grouping_literals("Operator") + grouping_literals("Operator", grouping_parent)
                               if lit.isalpha()]
    grouping_symbol_operators = [lit for lit in grouping_literals("Operator") if not lit.isalpha()]

    repository = {
        # Line comments start with // or #, as in the query parser (yqlplus.g4). The LSP's YQL grammar
        # only has //.
        "comment": {"patterns": [
            {"name": "comment.line.double-slash.yql", "match": r"(//).*$",
             "captures": {"1": {"name": "punctuation.definition.comment.yql"}}},
            {"name": "comment.line.number-sign.yql", "match": r"(#).*$",
             "captures": {"1": {"name": "punctuation.definition.comment.yql"}}},
            {"include": "#comment-block"},
        ]},
        "comment-block": {"name": "comment.block.yql", "begin": r"/\*", "end": r"\*/",
                          "beginCaptures": {"0": {"name": "punctuation.definition.comment.begin.yql"}},
                          "endCaptures": {"0": {"name": "punctuation.definition.comment.end.yql"}}},
        "string": {"patterns": [
            {"name": f"{LSP_TO_TM['String']}.double.yql", "begin": '"', "end": '"',
             "beginCaptures": {"0": {"name": "punctuation.definition.string.begin.yql"}},
             "endCaptures": {"0": {"name": "punctuation.definition.string.end.yql"}},
             "patterns": [{"name": "constant.character.escape.yql", "match": r"\\(?:u[0-9a-fA-F]{4}|.)"}]},
            {"name": f"{LSP_TO_TM['String']}.single.yql", "begin": "'", "end": "'",
             "beginCaptures": {"0": {"name": "punctuation.definition.string.begin.yql"}},
             "endCaptures": {"0": {"name": "punctuation.definition.string.end.yql"}},
             "patterns": [{"name": "constant.character.escape.yql", "match": r"\\(?:u[0-9a-fA-F]{4}|.)"}]},
        ]},
        "number": {"patterns": [
            {"name": scope("Number"), "match": r"(?<![\w.-])-?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][+-]?\d+)?[lL]?(?![\w.])"},
        ]},
        "parameter": {
            "match": rf"(@)({IDENT})",
            "captures": {"1": {"name": scope(yql_map.get("AT", "Macro"))}, "2": {"name": scope("Variable")}},
        },
        "keyword": {"name": scope("Keyword"), "match": words(keyword_literals, ignore_case=True)},
        "operator": {"patterns": [
            {"name": scope("Operator"), "match": words(word_operators, ignore_case=True)},
            {"name": scope("Operator"), "match": symbols(symbol_operators)},
        ]},
        "constant": {"name": scope("Type"), "match": words(type_literals, ignore_case=True)},
        "function-call": {"name": scope("Function"), "match": rf"{WORD_START}{IDENT}(?=\s*\()"},
        "identifier": {"name": scope("Variable"), "match": rf"{WORD_START}{IDENT}"},
        # Grouping runs from "|" to the end of the statement: a ";" or a line starting a new query.
        "grouping": {
            "name": "meta.grouping.yql",
            "begin": r"\|",
            "end": r"(?=;)|^(?=\s*(?i:select)(?![\w-]))",
            "patterns": [{"include": "#grouping-expression"}],
        },
        # Parentheses are tracked so that the end of grouping above only applies outside them: an
        # attribute named select may start a line inside an expression.
        "grouping-parens": {
            "begin": r"\(",
            "end": r"\)",
            "beginCaptures": {"0": {"name": "punctuation.section.parens.begin.yql"}},
            "endCaptures": {"0": {"name": "punctuation.section.parens.end.yql"}},
            "patterns": [{"include": "#grouping-expression"}],
        },
        # The grouping language has its own comments, identifiers and numbers: # also starts a comment,
        # and "-" is always an operator, so a number may follow it directly, as in 1-2.
        "grouping-expression": {"patterns": [
            {"include": "#comment"},
            {"include": "#string"},
            {"include": "#grouping-parens"},
            {"name": scope("Number"), "match": rf"{GROUPING_START}-?inf{GROUPING_END}"},
            # INTEGER and FLOAT in GroupingParser.ccc: decimal, hex or octal with an optional l/L, and
            # floats with an optional f/F/d/D.
            {"name": scope("Number"),
             "match": r"(?<![\w.@])(?:0[xX][0-9a-fA-F]+[lL]?|\d+(?:\.\d*)?(?:[eE][+-]?\d+)?[fFdD]|\d+\.\d*(?:[eE][+-]?\d+)?|\d+[eE][+-]?\d+|\d+[lL]?)(?![\w.@])"},
            {"match": rf"{GROUPING_START}(time)(\.)({'|'.join(sorted(time_methods, key=lambda w: (-len(w), w)))}){GROUPING_END}",
             "captures": {"1": {"name": scope("Class")}, "3": {"name": scope("Method")}}},
            {"match": rf"{GROUPING_START}(math)(\.)({'|'.join(sorted(math_methods, key=lambda w: (-len(w), w)))}){GROUPING_END}",
             "captures": {"1": {"name": scope("Class")}, "3": {"name": scope("Method")}}},
            {"match": rf"{GROUPING_START}(zcurve)(\.)({'|'.join(sorted(zcurve_methods))}){GROUPING_END}",
             "captures": {"1": {"name": scope("Class")}, "3": {"name": scope("Method")}}},
            {"name": scope("Keyword"), "match": words(grouping_literals("Keyword"), start=GROUPING_START, end=GROUPING_END)},
            {"name": scope("Operator"), "match": words(grouping_word_operators, start=GROUPING_START, end=GROUPING_END)},
            {"name": scope("Type"), "match": words(grouping_literals("Type"), start=GROUPING_START, end=GROUPING_END)},
            {"name": scope("Function"), "match": words(grouping_functions, start=GROUPING_START, end=GROUPING_END)},
            {"name": scope("Function"), "match": rf"{GROUPING_START}{GROUPING_IDENT}(?=\s*\()"},
            {"name": scope("Variable"), "match": rf"{GROUPING_START}{GROUPING_IDENT}"},
            {"name": scope("Operator"), "match": symbols(grouping_symbol_operators)},
        ]},
    }
    return {
        "$schema": "https://raw.githubusercontent.com/martinring/tmlanguage/master/tmlanguage.json",
        "name": "Vespa YQL",
        "scopeName": "source.vespaYQL",
        "patterns": [
            {"include": "#comment"},
            {"include": "#string"},
            {"include": "#grouping"},
            {"include": "#parameter"},
            {"include": "#number"},
            {"include": "#keyword"},
            {"include": "#operator"},
            {"include": "#constant"},
            {"include": "#function-call"},
            {"include": "#identifier"},
        ],
        "repository": repository,
    }


def main() -> None:
    grammar = build_grammar()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(grammar, indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    sys.exit(main())
