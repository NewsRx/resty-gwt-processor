## Problem

TestNG `testLogging.showStandardStreams = true` in this repo's `build.gradle` floods the standard build output with captured stdout/stderr from every test class, making `./gradlew build` output noisy and real warnings hard to spot. The setting is a debugging leftover, not a deliverable.

## Occurrences (live grep of build.gradle)

`grep -n "showStandardStreams" build.gradle` → lines: 45

## Scope

Remove every `showStandardStreams = true` occurrence (and any enclosing now-empty `testLogging { }` block) from this repo's `build.gradle`. No other test-logging changes; no production code changes.

## Out of Scope

- Adding log capture or output redirects
- Changing test assertions
- Touching other repositories' build files

## Approach

Minimal deletion of the flagged lines/blocks, verified via the root Gradle build.

## Success Criteria

| ID | Criterion | Evidence Type | Verification |
|----|-----------|---------------|--------------|
| SC-1 | `grep -c "showStandardStreams" build.gradle` returns 0 for this repo | structural | Live grep of `build.gradle` |
| SC-2 | `unset DISPLAY && ./gradlew :resty-gwt-processor:test` from the Butter root `/home/michael/git/Butter` completes green with 0 skipped, 0 failed | behavioral | Root `./gradlew` test task output |

## Requirements

- R-1: Zero `showStandardStreams` occurrences remain in `build.gradle`
- R-2: Test suite still green, 0 skipped

*Co-authored with AI: OpenCode (huggingface/zai-org/GLM-5.3-Flash)*
