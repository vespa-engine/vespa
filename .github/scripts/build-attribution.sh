#!/usr/bin/env bash
# Build a deduplicated ATTRIBUTIONS.md from one or more inventory JSON files.
#
# Usage: build-attribution.sh <inventory.json> [<inventory.json> ...]
# Output: markdown attribution document on stdout.
#
# Each input is expected in the wrapped shape produced by the
# vespa-engine/gh-actions/mend-inventory action:
#   { response: [ { component, license, copyrights, extraData, ... }, ... ],
#     additionalData: { totalItems: N } }
set -euo pipefail

today=$(date -u +"%Y-%m-%d")

cat <<EOF
# Third-Party Software Attributions

This file is auto-generated nightly and lists the open-source software
dependencies of Vespa detected by scanning package manifests.

For the hand-maintained list of vendored C/C++ libraries (Boost, OpenSSL,
ICU, etc.), see [\`NOTICES\`](NOTICES).

Last updated: $today
EOF

# Skip entries that don't have an identified license (the scanner couldn't
# determine one, or the entry has no package identity to attribute).
skipped=$(jq -s '
  [.[] | (.response // [])[]
    | select(.license.name == "Requires Review" or (.component.groupId // "") == "")]
  | length
' "$@")
jq -s -r '
  [ .[] | (.response // [])[]
    | select(.license.name != "Requires Review" and (.component.groupId // "") != "")
  ]
  | group_by([.component.groupId, .component.version, .license.name])
  | map(.[0])
  | sort_by([((.component.groupId // "") | ascii_downcase), (.component.version // "")])
  | map(
      [
        "",
        "---",
        "",
        ("## " + .component.groupId + " " + .component.version + " — " + .license.name),
        ""
      ]
      + (if ((.extraData.homepage // "") | length) > 0
          then ["- Homepage: <" + .extraData.homepage + ">"]
          else [] end)
      + ((.copyrights // []) | map("- " + .))
      | join("\n")
    )
  | join("\n")
' "$@"

# Trailing newline so the file ends cleanly.
echo
