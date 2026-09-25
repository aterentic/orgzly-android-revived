# About this fork

A personal fork of [orgzly-revived/orgzly-android-revived](https://github.com/orgzly-revived/orgzly-android-revived),
carrying patches that upstream has not taken yet, built and signed here, and installed instead
of the Play Store app.

The point is to run wanted changes without waiting for a release. The cost is that Play no
longer updates this app — see **Installing**.

It is `FORK.md` rather than a `README.md` section because upstream owns the readme: editing it
would conflict on every sync.

## Branches

| Branch | What it is |
|---|---|
| `master` | mirror of upstream, fast-forward only, never rewritten |
| `feat/*`, `fork/*` | one change each, branched from the **release tag** |
| `patched` | that release tag plus every open pull request, merged |

`patched` is **derived, not authored**: `tools/rebuild-patched.sh` regenerates it and it is
force-pushed. Never branch from it, never target a pull request at it.

**The open pull requests are the patch set.** Each carries one change and targets `master`,
and stays open for as long as the fork carries that patch. When upstream merges the change,
the pull request closes and the patch leaves on the next rebuild. Nothing else tracks what
this fork is carrying.

Branches are cut from the **release tag**, not from `master`, so they share a base with
`patched` and rebuilding is a merge rather than a cherry-pick across upstream's drift. The
pull request still targets `master`: GitHub diffs from the merge base, so only the branch's
own commits show.

A branch does not have to build on its own, and some cannot. A patch depending on the
`org-java` fork will not compile until it is merged with the branch that repoints the
dependency, so `patched` is the first place it builds. Red CI on such a branch is the
dependency showing through, not a broken branch.

One pull request is permanently open by design — the one carrying this file, the tooling, and
the build changes. It must never merge into `master`, and must always be in `patched`.

## Dependencies

`org-java` is resolved from [this fork](https://github.com/aterentic/org-java), which carries
a patch of its own. JitPack builds any tagged commit on demand, so the tag is the whole
release; nothing is published. The coordinate and version are in `build.gradle` and
`app/build.gradle`.

When the change lands upstream and they tag a release, point back at
`com.github.orgzly-revived:org-java` and drop the commit.

## Versioning

Upstream's `versionCode` is a literal, bumped once per release. Builds here must not reuse it:
Obtainium and Android compare `versionCode`, so two builds sharing one look like the same
version and **no update is offered, silently**.

    versionCode = <upstream versionCode> * 1000 + <build number>

Upstream's 292 becomes 292001, 292002…; their next release at 293 becomes 293000, still
higher. The ordering never inverts, so rebasing onto a newer release is always an upgrade.

## Syncing and rebuilding

```bash
git fetch upstream --tags
git merge --ff-only upstream/master     # on master; never merge into it
git push origin master --tags
```

A refused fast-forward means `master` has been written to; reset it to upstream.

When upstream tags a release:

1. Fast-forward `master`.
2. Close the pull requests upstream has taken; delete their branches.
3. Rebase each remaining branch onto the new tag.
4. `tools/rebuild-patched.sh <new-tag>` and force-push.
5. Tag a build; the release workflow publishes it.

Step 3 is the work, and it scales with how many patches are carried. That is the argument for
upstreaming everything that can be upstreamed.

## Installing

Debug builds install *alongside* the Play Store app under a separate application id — see
`CLAUDE.md`. This section is about the build that **replaces** it.

The first install needs the Play Store build uninstalled, because the signing key differs and
Android will not upgrade across keys. **App data is wiped.** Before starting:

- make sure notes are in a repository, not only on the device
- export settings to a note in a synced notebook

Repositories are not covered by that export — they live in the database, and credentials are
deliberately kept out of it — so re-add them by hand afterwards.

Every later rebuild upgrades in place, for as long as the same key signs it. Keep the keystore
and its password together in a password manager; losing either means wiping data again.

Updates come from this fork's GitHub releases, via Obtainium.

## Releasing

The workflow signs with a keystore in repository secrets: `APK_SIGNING_KEYSTORE_FILE`
(base64) and `APK_SIGNING_KEYSTORE_PASSWORD`.

Three things inherited from upstream still need changing before it produces something
installable from here:

- the signing alias is hardcoded and must match this keystore's
- releases are created as drafts, which Obtainium cannot see
- the "premium" flavour is built too, needing a Dropbox key a fork has no use for
