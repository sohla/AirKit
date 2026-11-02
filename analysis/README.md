# analysis/

Seven Python scripts mine this repo's git history and its personality files,
write JSON into `pages/output/`, and nine standalone D3 pages in `pages/` render it. Two
further modules support the archive page: `synthdef_graph.py` (a SuperCollider
signal-flow parser, imported by the history analyzer) and
`validate_synthdef_graph.py` (a development check for it — not part of the
pipeline).

This file is how to *run* it. For what the analyses mean, see
`description.md`.

---

## Run it

```sh
cd analysis
python3 update.py                      # regenerate everything into pages/output/
python3 serve.py                       # then open http://localhost:8000/
```

`update.py` runs all seven analyzers, prints how long each took, and ends with
a freshness table — the newest commit date found inside each artefact. If those
dates trail the repo HEAD printed at the top, something failed quietly and the
pages are showing you old data. It exits non-zero if any analyzer fails.

The analyzers are independent, so you can also run one on its own
(`python3 analyze_git.py`) when you only need to refresh a single page.

**Requirements:** Python 3 and `git`. No pip install — the scripts are
stdlib-only. The pages load D3, three.js and compromise from CDNs, so viewing
them needs a network connection.

## The server is not optional

Every page loads its data with `d3.json()` or `fetch()`. Both are XHR, and
browsers block XHR against `file://` under the same-origin policy — opening a
page by double-clicking it gets you a blank screen with a CORS error in the
console and nothing else. Serve `pages/`, which holds the pages and their
data together.

## What produces what

| script | writes to `pages/output/` | rendered by | cost |
|---|---|---|---|
| `analyze_git.py` | `commits_data.json` | `timeline_streamGraph`, `timeline_wordCloud`, `commit_activity` | <1s |
| `analyze_file_networks.py` | `file_networks.json` | `file_networks` | <1s |
| `analyze_personality_code.py` | `personality_code_data.json` | `personality_synth_code_timeline` | ~20s |
| `work_type_analyzer.py` | `work_type_data.json` | `work_type_timeline` | <1s |
| `personality_analyzer.py` | `personality_similarities.json` | `personality_similarities` | <1s |
| `analyze_personality_history.py` | `personality_history.json` | `personality_archive` | ~19s |
| `analyze_sensor_matrix.py` | `sensor_matrix.json` | `sensor_matrix` | <1s |
| `analyze_sound_vocabulary.py` | `sound_vocabulary.json` | `sound_vocabulary` | <1s |

`analyze_personality_code.py` is the one slow analyzer left: it shells out to
`git show` once per commit. `analyze_git.py` used to as well and now takes the
whole history in a single `git log --name-only`; the same rewrite would apply.
`analyze_personality_history.py` spends its time in the rival search, not in
git.

## The archive page

`personality_archive.html` is the odd one out: every other page aggregates
across the repo, this one answers questions about a **single p-file**. Pick one
from the dropdown and it shows where it came from, how it evolved, what shipped
alongside it, and what it currently sounds like.

Two things it knows that no other analyzer here does:

- **Lineage, followed all the way back.** It is the only script that passes
  `-M -C --find-copies-harder`, so it can see that `pluck1.sc` was born as a
  67% copy of `sheet2.sc`. **123 of the 183 p-files have a detectable
  ancestor** — most of this corpus was duplicated from a working file rather
  than written fresh, and until now that was invisible repo-wide.

  The chain is walked transitively, not one hop: `mel1` reports
  `animalMat → mattCello → jamesCello → brenton1 → mel1`, five generations
  over fifteen months, with each hop's date, percentage and copy-vs-rename.
  Chains run up to **eight** deep (`cazBathPlug`, `heatherBath`, `nic1`).

  Ancestors that were later deleted are still in the chain — `mattCello` is not
  in the working tree — and render greyed as `(gone)`, because a lineage that
  silently skips its dead links is worse than no lineage.

- **Contested parents.** Git names exactly one source. When a family of
  near-identical patches is in flight, which is normal here, that choice is
  close to arbitrary. The script recomputes the field with a line-set Jaccard
  and says so when the runner-up was within `RIVAL_MARGIN`. **33 of 183 have a
  contested parent** — for those, trust the family, not the exact arrow.

  Note the two metrics disagree in scale: git's percentage and the Jaccard
  score are not directly comparable, and only the *ranking* is used.
- **Generated prose.** The three narratives (evolution, sound, gesture) are
  composed in Python from the evidence, not authored per-file, so they
  regenerate with the data and cannot drift from what the files say. The
  composers are the `narrate_*` functions at the bottom of the script; that is
  the place to change the wording. The evolution account is deliberately a
  chronology rather than a period-by-period table — the counts are already in
  the chart and the commit list directly beneath it, and what prose is for is
  the shape: where the mass of the work sits, how long the silences run, and
  when the sound stopped changing and the playing started.
- **Drawn mapping curves.** Every `m.sensor.lincurve(…)` in `~next` is parsed
  for its numeric arguments and plotted as its actual transfer function, with a
  dashed straight line behind it so the amount of bend is visible. `lincurve`
  is not a shape anyone can read off the digits — the sign of the last argument
  inverts the whole response — and it is how nearly every instrument here gets
  its feel. 218 of the 229 bindings plot; the other 11 have a variable
  somewhere in the arguments (`notes.size` and the like) and cannot be drawn
  from a static read, so they show the binding without a plot.

  The curve maths in the page is a port of SuperCollider's own
  `SimpleNumber.lincurve` / `linexp` / `explin`, **checked against `sclang`** —
  worst deviation 2×10⁻¹². If you change it, re-check it the same way rather
  than by eye.

### Signal flow graphs

`synthdef_graph.py` parses each SynthDef out of the source and draws it as a
signal flow chart — sources on the left, `Out` on the right, control-rate
inputs dashed, stages coloured by role.

**A node is one variable assignment, not one UGen.** `bongo1` comes out as
eight stages rather than sixty, which is the level a flow chart is read at, and
it is also what makes the parse robust: at assignment granularity, multichannel
expansion, nested expression trees and array arithmetic all stop mattering.
Reassignment (`sig = FreeVerb.ar(sig, …)`) becomes a new node, so the chain
stays visible. Median across the corpus is 7 nodes.

**The layout is deterministic** — layered by dependency depth, ordered by
declaration within a layer. That is deliberate: a force simulation would settle
differently at every timeline stop and make the diagrams impossible to compare,
which is the entire reason for drawing them over time. Identical topology
renders identically, so a change is unmissable.

**Why parse instead of asking SuperCollider.** sclang can dump a SynthDef's
true graph and `sc3-dot` renders it, but only for a SynthDef it can compile
*now*. The archive needs topology at every historical revision — 2024 files
referencing samples that are gone, against a core since refactored. Parsing
never runs anything, so it works uniformly across history. Supriya and
hsc3-dot were considered and do not fit at all: they graph SynthDefs *defined
in* Python or Haskell, and cannot read a `.sc` file.

#### Validating the parser

A parser can be confidently wrong, so `validate_synthdef_graph.py` checks it
against the authority:

```sh
python3 validate_synthdef_graph.py     # needs SuperCollider; ~1 min
```

It compiles all 207 SynthDef literals in a throwaway sclang, reads their real
UGen lists from `SynthDescLib`, and compares. **Currently 206 of 206 clean.**

The comparison is coarse because the parser is coarse — a flow chart at
assignment granularity has no counterpart for ten `BinaryOpUGen`s. What it does
check is that no *stage* was missed. Three exemption classes, and the middle
one matters:

- **plumbing** — arithmetic, controls, output proxies: never drawn.
- **expansions** — `DynKlank` compiles into `Ringz`, `Splay` into `Pan2`,
  `SoundIn` into `In`. A primitive is excused **only when the parser found a
  composite that produces it**, rather than being ignored outright, so a
  genuinely missed stage cannot hide behind the exemption.
- **operators** — `.lag`, `.clip`, `.blend` become UGens but modify a signal
  rather than forming a stage.

Running it caught four real parser bugs that would otherwise have shipped:
`Splay.arFill` (the rate suffix has a CamelCase tail), `Env.perc(…).ar`
(envelope as a method, so no `Class.ar` to match), demand UGens like `Dseq`
(constructed with no rate suffix at all), and `SelectX` expanding to `Select`.

Nothing in `update.py` depends on sclang; this is a development check only.

### The timeline scrubber

Under *step through the changes* the page reconstructs the file at each point
in its history and lets you scrub it. It reads every revision of the file from
git, extracts the same things the current-state analysis extracts, and keeps a
stop **only where something changed**.

That deduplication is the whole design. There are 1,411 (file, commit) pairs in
scope but only **633 stops**, because 98 of the 183 files never change their
mappings at all — scrubbing through 36 identical frames to discover that would
be worse than useless. 125 files have more than one stop.

Each stop shows what changed, colour-coded: green added, red removed, amber
reshaped, blue synthesis, purple comments. A binding that survived but whose
numbers moved is drawn **over a dashed ghost of its previous shape on the same
axes**, so a reshape reads as a movement rather than as two charts to compare
by eye. The signal flow graph is redrawn at each stop too — a diagram that
changes shape is legible as changed at a glance, where prose repeated across
four identical stops is not. 77 files change their topology at least once.

The control is a **sticky footer**, not a widget inside one section, because
what it drives is spread down the whole page: the curves, the flow graph, a
playhead on the change-over-time chart, and the matching row in the commit
table. It turns amber the moment it leaves the present and offers *return to
now*, and anything showing a past state is outlined to match — a control that
silently changes things above it is how a 2024 state gets read as current.

The sections at the top of the page — lineage, the narratives, the current
curves — deliberately do **not** move. They are always "this file today".

`melbb1` is the example worth opening. Over two days in October 2025 it moves
from raw `d.sensors.gyroEvent` to the smoothed `gyroXFiltered`/`gyroYFiltered`,
reverts to raw the same day, then switches back two days later and keeps it —
a try, a revert, and a decision, none of which is visible from the current file
or from the commit subjects.

Deep-link a moment with `#name@state`, e.g.
`personality_archive.html#melbb1@11`.

### Writing notes for the archive

The generated prose reports evidence. It cannot tell you *why* a change was
made, what was learned in a session, or what an instrument is for. Write that
in the p-file itself, in a comment block starting with `note:`:

```supercollider
// note: inherited rather than composed. mattCello -> jamesCello is a copy at
// 100% - identical content under a new performer's name. The patch is not a
// piece so much as an instrument that gets handed on.
```

The archive promotes those to a **Note** panel above the generated prose,
marked "written in the p-file", with the line number. Any length works, and
`note -` and `note —` are accepted too.

It lives in the p-file rather than in a sidecar file deliberately: the note
travels with the code it describes, survives the copies and renames that
define this corpus, and is in front of you while you edit. There is no
`notes.json` and nothing to keep in sync.

This does not replace the comment convention in the repo's `CLAUDE.md` — the
block above a `~vdef` explaining the mark and its atlas lineage is still that.
`note:` is for the archival reading, and unmarked prose blocks of three or
more lines still appear further down the page under *what the file says about
itself*.

Its scope is the same `--all --since=2024-01-01` as everything else, which
costs almost nothing here — only 5 of the 183 p-files predate it, and those are
reported as `predates_window` with no birth commit rather than being given a
false origin date.

It covers the **working tree only**. Ancestors, descendants and co-committed
files routinely name p-files that were later deleted (11 ancestors currently);
those render greyed out and are not clickable, because there is no entry for
them.

One caveat worth knowing when reading the sound and gesture sections: they
describe the file **as it stands now**, not as it was at any point in its
history. Only the evolution narrative and the chart are historical.

## `pages/output/` is gitignored

The scripts are the source of truth; the JSON is disposable and is not
committed. If a page looks wrong or empty, run `update.py` before reading
anything into it.

## The scope invariant

Every analyzer must read the **same commit scope**, or the timelines describe
different populations of commits and cannot be read against one another.

It is currently `--all --since=2024-01-01 --no-merges`, declared at the top of
each script as `GIT_SCOPE`. Merges are excluded because they carry no files of
their own and their messages are boilerplate ("Merge branch ...").

This has already gone wrong once: `work_type_analyzer.py` was missing `--all`
and so analysed 585 commits while everything else analysed 801, and a
since-removed analyzer used a relative `"2 years ago"` window that slid forward
on every run. If you change the scope, change it in all of them.

## Known gaps

- **`sensor_matrix.json` describes the working tree, not the history.** Like
  `personality_similarities.json` it reads the p-files as they are now, so
  `GIT_SCOPE` does not apply; git is used only for each file's born and
  last-modified dates. Commented-out lines are not counted, and reads inside
  `~plot` are kept apart as probes.
- **`sound_vocabulary.json` likewise reads the working tree.** It counts live
  `Name.ar/kr/ir` calls only, so commented-out UGens are left out. The job
  groups are a hand-written table in the analyser; a UGen missing from it
  lands under "other" until it is filed.
- **The `2024-01-01` floor excludes 317 commits**, all of 2017–2023, about a
  quarter of the repo's history. Deliberate, but easy to forget when reading
  a chart that starts abruptly in 2024.
- **No rename detection, except in the archive.**
  `analyze_personality_history.py` passes `-M -C --find-copies-harder`, but it
  is the only one, and it looks at `personalities/` alone. Everywhere else the
  `code1.0 → code2.0 → code3.0` migration still appears as unrelated files
  rather than one lineage — a real limitation of `file_networks`.
- **`description.md` refers to `personality_code_timeline.html`**; the file is
  actually `personality_synth_code_timeline.html`.
