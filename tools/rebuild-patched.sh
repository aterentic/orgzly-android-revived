#!/usr/bin/env bash
#
# Rebuild the `patched` branch: a base ref plus every open pull request, merged.
#
# `patched` is derived rather than authored, so this discards whatever is there and
# regenerates it. The open pull requests are the patch set — see FORK.md.
#
set -euo pipefail

BASE="${1:-}"
REMOTE="${REMOTE:-origin}"
BRANCH="${BRANCH:-patched}"
LABEL="${LABEL:-patched}"

die() { echo "error: $*" >&2; exit 1; }

# gh defaults to the upstream repository inside a fork, which would list *their* pull
# requests. Resolve this fork from the remote instead.
repo_from_remote() {
    git remote get-url "$REMOTE" \
        | sed -E 's#^(git@|ssh://git@|https://)github\.com[:/]##; s#\.git$##'
}

[ -n "$BASE" ] || die "usage: $(basename "$0") <base-ref>   e.g. $(basename "$0") v1.23.0"
command -v gh >/dev/null || die "gh is required to list open pull requests"
[ -z "$(git status --porcelain)" ] || die "working tree is dirty"

git rev-parse --verify --quiet "$BASE^{commit}" >/dev/null || die "unknown base ref: $BASE"

# Head branches of open pull requests, oldest first, so the result is reproducible.
# Kept newline-separated rather than an array: macOS ships bash 3.2, which has no mapfile.
REPO="${REPO:-$(repo_from_remote)}"

# Only labelled pull requests are carried. Open-but-unlabelled is how a change stays under
# review without landing in the installed build.
#
# `fork/*` carries the tooling and build changes everything else may depend on, so it merges
# first; the rest follow by pull request number, which keeps the result reproducible.
HEADS=$(gh pr list -R "$REPO" --state open --label "$LABEL" --json number,headRefName \
    --jq 'sort_by(.number)
          | (map(select(.headRefName | startswith("fork/")))
             + map(select(.headRefName | startswith("fork/") | not)))
          | .[].headRefName')

[ -n "$HEADS" ] || die "no open pull requests labelled '$LABEL', nothing to merge"

echo "repo:    $REPO"
echo "label:   $LABEL"
echo "base:    $BASE"
echo "merging:"; echo "$HEADS" | sed 's/^/  /'
echo

while read -r head; do
    git rev-parse --verify --quiet "$head^{commit}" >/dev/null \
        || die "branch missing locally: $head (git fetch $REMOTE)"
done <<< "$HEADS"

git switch -C "$BRANCH" "$BASE" >/dev/null

while read -r head; do
    # --no-ff keeps one merge commit per pull request, so the branch shows what it carries.
    if ! git merge --no-ff -m "Merge pull request: $head" "$head"; then
        die "conflict merging $head — resolve, commit, then re-run for the remaining branches"
    fi
done <<< "$HEADS"

echo
echo "$BRANCH rebuilt on $BASE:"
git --no-pager log --oneline --graph "$BASE..$BRANCH"
echo
echo "not pushed. when satisfied:  git push --force-with-lease $REMOTE $BRANCH"
