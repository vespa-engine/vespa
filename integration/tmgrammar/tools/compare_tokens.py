# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# /// script
# requires-python = ">=3.10"
# ///
"""
Compare Java LSP semantic tokens with TextMate grammar tokens.

Reads java_tokens.json and tm_tokens.json (produced by SemanticTokenDumper.java
and tm_tokenize.mjs respectively) and produces a human-readable diff report.

Usage:
    uv run tools/compare_tokens.py [--json] [--file NAME] [--show-symbols] [--fixable-only]
"""
import argparse
import json
import sys
from collections import Counter
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent

# --- Scope normalization ---
# Maps Java LSP type names to TM scope prefixes they should match.
SCOPE_NORM: dict[str, list[str]] = {
    "keyword":    ["keyword."],
    "number":     ["constant.numeric"],
    "string":     ["string.quoted"],
    "function":   ["entity.name.function"],
    "operator":   ["keyword.operator"],
    "type":       ["support.type", "storage.type"],
    "enumMember": ["variable.other.enummember"],
    "property":   ["variable.other.property"],
    "comment":    ["comment."],
    "macro":      ["entity.name.function"],
}

# LSP types that come from symbol resolution (TM can't do these)
SYMBOL_TYPES = {
    "namespace",  # schema name
    "class",      # document name
    "variable",   # field, struct, annotation, rank-profile, etc.
    "parameter",  # function parameters
}

# LSP types that are symbol-resolved ONLY when TM doesn't also match them.
# E.g. "function" for user-defined function names — built-in functions
# like "embed" are matched by TM, but user-defined "foo" is not.
SYMBOL_WHEN_UNMATCHED = {"function"}


def lsp_matches_tm(lsp_type: str, tm_scope: str | None, tm_scopes: list[str] | None = None) -> bool:
    """Check if an LSP type matches a TM scope via the normalization map.

    Checks all scopes in the TM scope stack (if available), not just the
    most specific one.  This handles cases where a punctuation capture
    (e.g. punctuation.definition.comment) is nested inside a broader scope
    (e.g. comment.line) that matches the LSP type.
    """
    scopes_to_check = tm_scopes if tm_scopes else ([tm_scope] if tm_scope else [])
    if not scopes_to_check:
        return False
    prefixes = SCOPE_NORM.get(lsp_type, [])
    for scope in scopes_to_check:
        if any(scope.startswith(p) for p in prefixes):
            return True
        # Java LSP emits overlapping keyword+type tokens for container types
        # (array, map, weightedset).  Accept storage.type as matching keyword.
        if lsp_type == "keyword" and scope.startswith("storage.type"):
            return True
    return False


def is_symbol_type(lsp_type: str, modifiers: list[str]) -> bool:
    """Check if this token comes from symbol resolution."""
    if lsp_type in SYMBOL_TYPES:
        return True
    # definition/defaultLibrary modifiers indicate symbol-resolved tokens
    if "definition" in modifiers or "defaultLibrary" in modifiers:
        return True
    return False


def align_tokens(java_tokens: list[dict], tm_tokens: list[dict]) -> list[dict]:
    """
    Align Java and TM tokens by overlapping character ranges on the same line.

    Returns a list of alignment records:
      {"java": token|None, "tm": token|None, "status": str}
    """
    # Build line-indexed structures
    java_by_line: dict[int, list[dict]] = {}
    for t in java_tokens:
        java_by_line.setdefault(t["line"], []).append(t)

    tm_by_line: dict[int, list[dict]] = {}
    for t in tm_tokens:
        tm_by_line.setdefault(t["line"], []).append(t)

    all_lines = sorted(set(java_by_line.keys()) | set(tm_by_line.keys()))
    aligned = []

    for line in all_lines:
        jts = java_by_line.get(line, [])
        tts = tm_by_line.get(line, [])

        # For each Java token, find overlapping TM tokens
        used_tm = set()
        for jt in jts:
            j_start = jt["col"]
            j_end = jt["col"] + jt["len"]
            best_tm = None
            best_overlap = 0

            for idx, tt in enumerate(tts):
                if idx in used_tm:
                    continue
                t_start = tt["col"]
                t_end = tt["col"] + tt["len"]
                overlap = max(0, min(j_end, t_end) - max(j_start, t_start))
                if overlap > best_overlap:
                    best_overlap = overlap
                    best_tm = (idx, tt)

            if best_tm is not None:
                used_tm.add(best_tm[0])
                tm_tok = best_tm[1]
                tm_scope = tm_tok.get("scope")
                tm_scopes = tm_tok.get("scopes")

                if is_symbol_type(jt["type"], jt.get("modifiers", [])):
                    status = "symbol"
                elif lsp_matches_tm(jt["type"], tm_scope, tm_scopes):
                    status = "match"
                elif tm_scope is None:
                    if jt["type"] in SYMBOL_WHEN_UNMATCHED:
                        status = "symbol"
                    else:
                        status = "java_only"
                else:
                    if jt["type"] in SYMBOL_WHEN_UNMATCHED and not lsp_matches_tm(jt["type"], tm_scope, tm_scopes):
                        status = "symbol"
                    else:
                        status = "mismatch"

                aligned.append({"java": jt, "tm": tm_tok, "status": status})
            else:
                if is_symbol_type(jt["type"], jt.get("modifiers", [])) or jt["type"] in SYMBOL_WHEN_UNMATCHED:
                    status = "symbol"
                else:
                    status = "java_only"
                aligned.append({"java": jt, "tm": None, "status": status})

        # TM tokens not matched to any Java token
        for idx, tt in enumerate(tts):
            if idx not in used_tm:
                # Only report TM-only if it has a real scope
                if tt.get("scope") is not None:
                    aligned.append({"java": None, "tm": tt, "status": "tm_only"})

    return aligned


def compare_file(java_file_data: dict, tm_file_data: dict) -> list[dict]:
    """Compare tokens for a single file."""
    java_tokens = java_file_data.get("tokens", [])
    tm_tokens = tm_file_data.get("tokens", [])
    return align_tokens(java_tokens, tm_tokens)


def format_summary(all_alignments: dict[str, list[dict]], file_count: int) -> str:
    """Format Level 1 summary."""
    total_java = 0
    total_tm = 0
    counts = Counter()

    for filename, alignments in all_alignments.items():
        for a in alignments:
            if a["java"]:
                total_java += 1
            if a["tm"]:
                total_tm += 1
            counts[a["status"]] += 1

    total = sum(counts.values())
    lines = []
    lines.append(f"Files: {file_count} | Java tokens: {total_java:,} | TM tokens: {total_tm:,}")
    lines.append(f"  Matched:           {counts['match']:>5}  ({pct(counts['match'], total)})")
    lines.append(f"  Java-only symbol:  {counts['symbol']:>5}  ({pct(counts['symbol'], total)}) [expected - TM can't resolve symbols]")
    lines.append(f"  Java-only keyword: {counts['java_only']:>5}  ({pct(counts['java_only'], total)}) [FIXABLE - missing from grammar]")
    lines.append(f"  TM-only:           {counts['tm_only']:>5}  ({pct(counts['tm_only'], total)})")
    lines.append(f"  Color mismatch:    {counts['mismatch']:>5}  ({pct(counts['mismatch'], total)}) [FIXABLE - wrong scope]")
    return "\n".join(lines)


def pct(n: int, total: int) -> str:
    if total == 0:
        return " 0%"
    return f"{n * 100 / total:>2.0f}%"


def format_impact(all_alignments: dict[str, list[dict]], fixable_only: bool) -> str:
    """Format Level 2 impact-sorted mismatches."""
    # Group mismatches by (java_type -> tm_scope) pattern
    mismatch_groups: dict[str, list[str]] = {}
    java_only_groups: dict[str, list[str]] = {}

    for filename, alignments in all_alignments.items():
        for a in alignments:
            if a["status"] == "mismatch":
                java_type = a["java"]["type"]
                tm_scope = a["tm"]["scope"] if a["tm"] else "(none)"
                key = f"Java={java_type} -> TM={tm_scope}"
                mismatch_groups.setdefault(key, []).append(a["java"]["text"])
            elif a["status"] == "java_only":
                java_type = a["java"]["type"]
                key = f"Java={java_type} -> TM=none"
                java_only_groups.setdefault(key, []).append(a["java"]["text"])
            elif a["status"] == "tm_only" and not fixable_only:
                tm_scope = a["tm"]["scope"] if a["tm"] else "(none)"
                key = f"Java=none -> TM={tm_scope}"
                mismatch_groups.setdefault(key, []).append(a["tm"]["text"])

    lines = ["\nTOP FIXABLE MISMATCHES:"]

    # Combine and sort by count
    all_groups: list[tuple[str, list[str]]] = []
    all_groups.extend(java_only_groups.items())
    all_groups.extend(mismatch_groups.items())
    all_groups.sort(key=lambda x: -len(x[1]))

    for key, texts in all_groups[:20]:
        unique_texts = sorted(set(texts))
        sample = ", ".join(f'"{t}"' for t in unique_texts[:8])
        if len(unique_texts) > 8:
            sample += ", ..."
        lines.append(f"  {len(texts):>3}x  {key}")
        lines.append(f"       {sample}")

    if not all_groups:
        lines.append("  (none)")

    return "\n".join(lines)


def format_file_detail(filename: str, alignments: list[dict], show_symbols: bool) -> str:
    """Format Level 3 per-file detail."""
    lines = []
    for a in alignments:
        if not show_symbols and a["status"] == "symbol":
            continue

        java = a.get("java")
        tm = a.get("tm")

        line_num = java["line"] if java else tm["line"]
        text = java["text"] if java else tm["text"]
        java_type = java["type"] if java else "(none)"
        tm_scope = tm["scope"] if tm and tm.get("scope") else "(none)"

        if a["status"] == "match":
            mark = "ok"
        elif a["status"] == "symbol":
            mark = "~ symbol"
        elif a["status"] == "mismatch":
            mark = "MISMATCH"
        elif a["status"] == "java_only":
            mark = "JAVA-ONLY"
        elif a["status"] == "tm_only":
            mark = "TM-ONLY"
        else:
            mark = a["status"]

        text_display = text[:20].ljust(20)
        lines.append(
            f"{filename} L{line_num + 1}: {text_display} Java={java_type:<16} TM={tm_scope:<30} {mark}"
        )

    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description="Compare Java LSP and TextMate tokens")
    parser.add_argument("--json", action="store_true", help="Machine-readable JSON output")
    parser.add_argument("--file", type=str, help="Filter to single file (partial match)")
    parser.add_argument("--show-symbols", action="store_true", help="Include symbol-resolved tokens in detail")
    parser.add_argument("--fixable-only", action="store_true", help="Only show mismatches we can act on")
    args = parser.parse_args()

    java_path = SCRIPT_DIR / "java_tokens.json"
    tm_path = SCRIPT_DIR / "tm_tokens.json"

    if not java_path.exists():
        print(f"Error: {java_path} not found. Run: cd integration/schema-language-server/language-server && mvn test-compile exec:java -Dexec.mainClass=ai.vespa.schemals.SemanticTokenDumper -Dexec.classpathScope=test", file=sys.stderr)
        sys.exit(1)
    if not tm_path.exists():
        print(f"Error: {tm_path} not found. Run: cd tools && npm install && node tm_tokenize.mjs", file=sys.stderr)
        sys.exit(1)

    with open(java_path) as f:
        java_data = json.load(f)
    with open(tm_path) as f:
        tm_data = json.load(f)

    java_files = java_data.get("files", {})
    tm_files = tm_data.get("files", {})

    # Find common files
    all_file_keys = sorted(set(java_files.keys()) | set(tm_files.keys()))

    if args.file:
        all_file_keys = [k for k in all_file_keys if args.file in k]
        if not all_file_keys:
            print(f"No files matching '{args.file}'", file=sys.stderr)
            sys.exit(1)

    all_alignments: dict[str, list[dict]] = {}
    for key in all_file_keys:
        java_file = java_files.get(key, {"tokens": []})
        tm_file = tm_files.get(key, {"tokens": []})
        all_alignments[key] = compare_file(java_file, tm_file)

    if args.json:
        # JSON output mode
        json_out = {
            "files": {
                k: [
                    {
                        "status": a["status"],
                        "java": a.get("java"),
                        "tm": a.get("tm"),
                    }
                    for a in alignments
                    if args.show_symbols or a["status"] != "symbol"
                ]
                for k, alignments in all_alignments.items()
            }
        }
        json.dump(json_out, sys.stdout, indent=2)
        print()
        return

    # Text output
    print(format_summary(all_alignments, len(all_file_keys)))
    print(format_impact(all_alignments, args.fixable_only))

    if args.file:
        print()
        for key in all_file_keys:
            print(format_file_detail(key, all_alignments[key], args.show_symbols))


if __name__ == "__main__":
    main()
