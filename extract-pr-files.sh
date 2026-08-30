#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./extract-pr-files.sh [branch] [target-dir] [base-ref]

Copies files changed by a PR branch into another folder, preserving source paths.

Arguments:
  branch      Branch containing the PR changes.
  target-dir  Destination root folder. Default:
  base-ref    Base branch/ref for the PR diff. Default: main

Examples:
  ./extract-pr-files.sh
  ./extract-pr-files.sh feat/skill-injector-unload ../langgraph4j-1.9
  ./extract-pr-files.sh my-pr-branch ../target develop
EOF
}

branch="${1:-}"
target_dir="${2:-}"
base_ref="${3:-main}"

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "error: this script must be run inside a git work tree" >&2
  exit 1
fi

if ! git rev-parse --verify --quiet "$branch^{commit}" >/dev/null; then
  echo "error: branch/ref not found: $branch" >&2
  exit 1
fi

if ! git rev-parse --verify --quiet "$base_ref^{commit}" >/dev/null; then
  echo "error: base ref not found: $base_ref" >&2
  exit 1
fi

repo_root="$(git rev-parse --show-toplevel)"
merge_base="$(git merge-base "$base_ref" "$branch")"
target_abs="$(cd "$repo_root" && mkdir -p "$target_dir" && cd "$target_dir" && pwd)"

echo "Branch: $branch"
echo "Base: $base_ref"
echo "Merge base: $merge_base"
echo "Target: $target_abs"
echo

copied=0
skipped=0

while IFS=$'\t' read -r status path extra_path; do
  [[ -z "${status:-}" ]] && continue

  case "$status" in
    D*)
      echo "skip deleted: $path"
      skipped=$((skipped + 1))
      continue
      ;;
    R*|C*)
      src_path="$extra_path"
      ;;
    *)
      src_path="$path"
      ;;
  esac

  if [[ -z "${src_path:-}" ]]; then
    echo "skip unparsable diff entry: $status $path ${extra_path:-}" >&2
    skipped=$((skipped + 1))
    continue
  fi

  dest_path="$target_abs/$src_path"
  mkdir -p "$(dirname "$dest_path")"
  git show "$branch:$src_path" > "$dest_path"
  echo "copied: $src_path"
  copied=$((copied + 1))
done < <(git diff --name-status "$merge_base..$branch")

echo
echo "Done. Copied $copied file(s), skipped $skipped deleted file(s)."
