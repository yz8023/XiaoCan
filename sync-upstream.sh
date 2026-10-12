#!/usr/bin/env bash
# Sync the local feature stack onto the latest upstream release.
# Local features live as commits on top of upstream/main (see README).
set -euo pipefail
cd "$(dirname "$0")"

if ! git remote get-url upstream >/dev/null 2>&1; then
    git remote add upstream https://github.com/Jessire/XiaoCanPurify.git
fi
git fetch upstream --tags

if [ "$(git merge-base HEAD upstream/main)" = "$(git rev-parse upstream/main)" ]; then
    echo "Already up to date with upstream/main."
    exit 0
fi

echo "Rebasing local feature stack onto upstream/main ..."
if git rebase upstream/main; then
    echo
    echo "Sync complete. Local feature commits on top of upstream:"
    git log --oneline upstream/main..HEAD
    echo
    echo "Next: git push --force-with-lease origin main"
else
    echo
    echo "Rebase stopped due to conflicts. Resolve them, then run:"
    echo "  git add <files> && git rebase --continue"
    echo
    echo "Known recurring conflict point:"
    echo "  app/src/main/java/io/github/jessire/xiaocanpurify/NetworkAdInterceptor.java"
    echo "  Keep the crash-safe invoke() and merge upstream's new keyword lists into"
    echo "  isWithdrawCardName / isAdOrTrackingUrl."
    exit 1
fi
