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

from generate_tmgrammar import LSP, TMGRAMMAR, _LSP_TO_TM

YQL_CCC = LSP / "src/main/ccc/yqlplus/YQLPlus.ccc"
GROUPING_CCC = LSP / "src/main/ccc/grouping/GroupingParser.ccc"
SEMANTIC_DIR = LSP / "src/main/java/ai/vespa/schemals/lsp/yqlplus/semantictokens"
YQL_CFG = SEMANTIC_DIR / "YQLPlusSemanticTokenConfig.java"
GROUPING_CFG = SEMANTIC_DIR / "VespaGroupingSemanticTokenConfig.java"
OUTPUT = TMGRAMMAR / "grammars/vespa-yql.tmLanguage.json"

# LSP SemanticTokenType → TextMate scope, extended with the types the YQL configs use.
LSP_TO_TM = {**_LSP_TO_TM, "Variable": "variable.other", "Method": "entity.name.function", "Class": "support.class"}

# YQL names may contain "-": a word ends at the first character that is neither a word character nor
# "-". It may start right after "-", as in -price, since names containing "-" are matched from their
# first character. Grouping names may contain "@" but not "-", which is always minus there.
IDENT, YQL = r"[a-zA-Z_][\w-]*", (r"(?<!\w)", r"(?![\w-])")
GROUPING_IDENT, GROUPING = r"[a-zA-Z][\w@]*", (r"(?<![\w@])", r"(?![\w@])")


# ---------------------------------------------------------------------------
# 1  Read the parsers and the LSP token configuration
# ---------------------------------------------------------------------------

def _without_comments(path: Path) -> str:
    return re.sub(r"UNPARSED\s*:.*?;", "", path.read_text(), flags=re.DOTALL)


def parse_yql_tokens(path: Path) -> dict[str, str]:
    """Literal YQL tokens, name → literal, with tokens built from others resolved:
    < ORDERBY: <ORDER> ' ' <BY> > becomes "order by"."""
    definitions = dict(re.findall(r"<\s*([A-Z_][A-Z_0-9]*)\s*:\s*((?:'[^']*'|<[A-Z_]+>|\s)+?)\s*>",
                                  _without_comments(path)))

    def resolve(name: str, seen: frozenset = frozenset()) -> str | None:
        if name not in definitions or name in seen:
            return None
        parts = [lit if not ref else resolve(ref, seen | {name})
                 for lit, ref in re.findall(r"'([^']*)'|<([A-Z_]+)>", definitions[name])]
        return None if None in parts else "".join(parts)

    return {name: lit for name in definitions if (lit := resolve(name)) is not None}


def parse_grouping_tokens(path: Path) -> dict[str, str]:
    """Literal grouping tokens, name → literal: < AND: "and" >, < INFIX_ADD: "+" >."""
    return dict(re.findall(r'<\s*([A-Z_][A-Z_0-9]*)\s*:\s*"([^"]+)"\s*>', _without_comments(path)))


def production_tokens(path: Path, production: str) -> list[str]:
    """Names of the tokens referenced in a production, e.g. mult_op → STAR, DIV, MODULO."""
    m = re.search(rf"^{production}\s*:(.*?)^;", path.read_text(), flags=re.DOTALL | re.MULTILINE)
    return re.findall(r"<([A-Z_]+)>", m.group(1)) if m else []


def parse_class_config(path: Path) -> tuple[set[str], dict[str, str], dict[str, str]]:
    """From a YQL semantic token config: the keyword classes, class → LSP type, and LSP type for
    tokens colored by their parent (AND in andPredicate; the first parent wins). Types may be given
    as SemanticTokenTypes.X or as a String constant defined as one."""
    java = path.read_text()
    constants = dict(re.findall(r"String\s+(\w+)\s*=\s*SemanticTokenTypes\.(\w+)\s*;", java))

    def lsp_type(value: str) -> str:
        return constants.get(value, value.removeprefix("SemanticTokenTypes."))

    keywords = set(re.findall(r"add\((\w+)\.class\)", java))
    tokens = {cls: lsp_type(v) for cls, v in re.findall(r"put\((\w+)\.class\s*,\s*([\w.]+)\s*\)", java)}
    by_parent: dict[str, str] = {}
    for cls, v in re.findall(r"new Pair<>\((\w+)\.class\s*,\s*\w+\.class\)\s*,\s*([\w.]+)\s*\)", java):
        by_parent.setdefault(cls, lsp_type(v))
    return keywords, tokens, by_parent


# ---------------------------------------------------------------------------
# 2  Build the grammar
# ---------------------------------------------------------------------------

def scope(lsp_type: str) -> str:
    return LSP_TO_TM[lsp_type] + ".yql"


def alternatives(literals) -> str:
    """Escaped alternatives, longest first, with spaces matching any whitespace."""
    return "|".join(re.escape(w).replace(r"\ ", r"\s+") for w in sorted(set(literals), key=lambda w: (-len(w), w)))


def words(literals, bounds=YQL, ignore_case: bool = False) -> str:
    pattern = f"{bounds[0]}({alternatives(literals)}){bounds[1]}"
    return f"(?i:{pattern})" if ignore_case else pattern


def line_comment(marker: str, kind: str) -> dict:
    return {"name": f"comment.line.{kind}.yql", "match": rf"({marker}).*$",
            "captures": {"1": {"name": "punctuation.definition.comment.yql"}}}


def delimited(begin: str, end: str, punctuation: str, name: str | None = None, patterns: list | None = None) -> dict:
    """A begin/end rule whose delimiters are scoped punctuation.<punctuation>.begin/end."""
    rule = {"name": name} if name else {}
    rule.update({"begin": begin, "end": end,
                 "beginCaptures": {"0": {"name": f"punctuation.{punctuation}.begin.yql"}},
                 "endCaptures": {"0": {"name": f"punctuation.{punctuation}.end.yql"}}})
    if patterns is not None:
        rule["patterns"] = patterns
    return rule


def build_grammar() -> dict:
    yql_tokens = parse_yql_tokens(YQL_CCC)
    grouping_tokens = parse_grouping_tokens(GROUPING_CCC)
    yql_keywords, yql_map, _ = parse_class_config(YQL_CFG)
    _, grouping_map, grouping_parent = parse_class_config(GROUPING_CFG)

    def yql(names):
        return [yql_tokens[n] for n in names if n in yql_tokens]

    def grouping(lsp_type, source=grouping_map):
        return [grouping_tokens[c] for c, t in source.items() if t == lsp_type and c in grouping_tokens]

    keywords = yql(yql_keywords)
    # The LSP colors <, > and the ! of != as operators, but not = and !=. Color them all.
    operator_tokens = [t for cls, typ in yql_map.items() if typ == "Operator"
                       for t in production_tokens(YQL_CCC, cls) or [cls]]
    operators = [lit for lit in yql(operator_tokens + production_tokens(YQL_CCC, "equality_op")) if lit not in keywords]
    word_operators = [lit for lit in operators if re.fullmatch(r"[a-z ]+", lit)]
    grouping_operators = grouping("Operator") + grouping("Operator", grouping_parent)

    # Grouping methods by namespace: time.year(...), math.sqrt(...), zcurve.x(...)
    namespaces = {"time": [], "math": [], "zcurve": []}
    for cls, typ in grouping_map.items():
        if typ == "Method":
            ns = "time" if cls.startswith("TIME_") else "zcurve" if cls in ("X", "Y") else "math"
            namespaces[ns].append(grouping_tokens[cls])

    escape = {"name": "constant.character.escape.yql", "match": r"\\(?:u[0-9a-fA-F]{4}|.)"}
    repository = {
        # Line comments start with // or #, as in the query parser (yqlplus.g4); the LSP's YQL
        # grammar only has //. The grouping parser has both.
        "comment": {"patterns": [line_comment("//", "double-slash"), line_comment("#", "number-sign"),
                                 {"include": "#comment-block"}]},
        "comment-block": delimited(r"/\*", r"\*/", "definition.comment", name="comment.block.yql"),
        "string": {"patterns": [
            delimited(q, q, "definition.string", name=f"{LSP_TO_TM['String']}.{kind}.yql", patterns=[escape])
            for q, kind in (('"', "double"), ("'", "single"))]},
        "number": {"patterns": [
            {"name": scope("Number"), "match": r"(?<![\w.-])-?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][+-]?\d+)?[lL]?(?![\w.])"},
        ]},
        "parameter": {"match": rf"(@)({IDENT})",
                      "captures": {"1": {"name": scope(yql_map.get("AT", "Macro"))}, "2": {"name": scope("Variable")}}},
        "keyword": {"name": scope("Keyword"), "match": words(keywords, ignore_case=True)},
        "operator": {"patterns": [
            {"name": scope("Operator"), "match": words(word_operators, ignore_case=True)},
            {"name": scope("Operator"), "match": alternatives(lit for lit in operators if lit not in word_operators)},
        ]},
        "constant": {"name": scope("Type"),
                     "match": words(yql(c for c, t in yql_map.items() if t == "Type"), ignore_case=True)},
        "function-call": {"name": scope("Function"), "match": rf"{YQL[0]}{IDENT}(?=\s*\()"},
        "identifier": {"name": scope("Variable"), "match": rf"{YQL[0]}{IDENT}"},
        # Grouping runs from "|" to the end of the statement: a ";" or a line starting a new query.
        "grouping": {"name": "meta.grouping.yql", "begin": r"\|", "end": r"(?=;)|^(?=\s*(?i:select)(?![\w-]))",
                     "patterns": [{"include": "#grouping-expression"}]},
        # A bucket range opens with (, [ or < and closes with ), ] or > in any combination (bucketElm in
        # GroupingParser.ccc), so it is tracked separately from parentheses.
        "grouping-bucket": {
            "begin": rf"{GROUPING[0]}({re.escape(grouping_tokens['BUCKET'])})\s*([(\[<])", "end": r"[)\]>]",
            "beginCaptures": {"1": {"name": scope(grouping_map.get("BUCKET", "Macro"))},
                              "2": {"name": "punctuation.section.brackets.begin.yql"}},
            "endCaptures": {"0": {"name": "punctuation.section.brackets.end.yql"}},
            "patterns": [{"include": "#grouping-expression"}]},
        # Parentheses are tracked so that the end of grouping only applies outside them: an attribute
        # named select may start a line inside an expression.
        "grouping-parens": delimited(r"\(", r"\)", "section.parens", patterns=[{"include": "#grouping-expression"}]),
        # Grouping has its own names and numbers: "-" is always an operator, so a number may follow
        # it directly, as in 1-2. Query parameters are substituted in grouping too.
        "grouping-expression": {"patterns": [
            {"include": "#comment"},
            {"include": "#string"},
            {"include": "#grouping-bucket"},
            {"include": "#grouping-parens"},
            {"include": "#parameter"},
            {"name": scope("Number"), "match": rf"{GROUPING[0]}-?inf{GROUPING[1]}"},
            # INTEGER and FLOAT in GroupingParser.ccc
            {"name": scope("Number"),
             "match": r"(?<![\w.@])(?:0[xX][0-9a-fA-F]+[lL]?|\d+(?:\.\d*)?(?:[eE][+-]?\d+)?[fFdD]"
                      r"|\d+\.\d*(?:[eE][+-]?\d+)?|\d+[eE][+-]?\d+|\d+[lL]?)(?![\w.@])"},
            *({"match": rf"{GROUPING[0]}({ns})(\.)({alternatives(methods)}){GROUPING[1]}",
               "captures": {"1": {"name": scope("Class")}, "3": {"name": scope("Method")}}}
              for ns, methods in namespaces.items()),
            {"name": scope("Keyword"), "match": words(grouping("Keyword"), GROUPING)},
            {"name": scope("Operator"), "match": words([lit for lit in grouping_operators if lit.isalpha()], GROUPING)},
            {"name": scope("Type"), "match": words(grouping("Type"), GROUPING)},
            {"name": scope("Function"), "match": words(grouping("Function") + grouping("Macro"), GROUPING)},
            {"name": scope("Function"), "match": rf"{GROUPING[0]}{GROUPING_IDENT}(?=\s*\()"},
            {"name": scope("Variable"), "match": rf"{GROUPING[0]}{GROUPING_IDENT}"},
            {"name": scope("Operator"),
             "match": alternatives(lit for lit in grouping("Operator") if not lit.isalpha())},
        ]},
    }
    return {
        "$schema": "https://raw.githubusercontent.com/martinring/tmlanguage/master/tmlanguage.json",
        "name": "Vespa YQL",
        "scopeName": "source.vespaYQL",
        "patterns": [{"include": f"#{rule}"} for rule in (
            "comment", "string", "grouping", "parameter", "number", "keyword", "operator", "constant", "function-call",
            "identifier")],
        "repository": repository,
    }


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(build_grammar(), indent=2, ensure_ascii=False) + "\n")
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    sys.exit(main())
