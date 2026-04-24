Just forked Tempus to add a phone-first deterministic voice flow.

The target feature should be narrow:

“Tap a mic button, say a song or artist, resolve against Nextcloud/Subsonic, and start playback immediately.”

That is small enough to build, test, and iterate.

A good split is this:

Add yourself:

voice entry UI
speech-to-text
query resolver
result ranking
“play best match now”
optional clarification UI if confidence is low

So the architecture becomes:

voice input
→ transcript
→ search service
→ rank candidates
→ build play queue
→ hand off to existing Tempus playback service

That means you are not writing a player. You are writing a voice/search controller on top of an existing player.

What to inspect first in the Tempus codebase:

Search path
Find where free-text search already happens. You want the code that currently powers manual search in the app.
That is your entry point for voice queries.
Playback entry point
Find the narrowest method that says “play this track” or “replace queue with these tracks and start playback.”
Your new voice flow should call that directly instead of simulating UI clicks.
Queue construction
You will want two modes:
exact song result → play immediately
artist / album result → build a queue and start from track 1
Media service boundary
Your voice feature should not manipulate UI fragments. It should call into the same service/viewmodel/repository
path that normal playback uses.

Minimal feature set for v1:

Screen:

one mic button
one text field showing what was heard
one result line: “Playing …”
fallback list only when confidence is low

Logic:

if transcript matches one track strongly, play it
if multiple similar tracks, prefer:
exact title + artist
then exact title
then fuzzy title
if artist only, play all songs of it
if nothing matches, show top 5 results

Ranking rules for v1:

normalize case
remove punctuation
ignore file extension
bonus for exact title match
bonus for artist + title match
bonus for starts-with match

Keep it simple. You do not need AI first. A decent heuristic ranker will get you far.

Implementation phases:

Phase 1: text only
Add a hidden debug screen or simple activity where you type a query and it plays the best result.
This proves your search-to-play pipeline without speech.

Phase 2: speech input
Use Android’s built-in speech recognizer. Do not start with LLMs, cloud AI, or custom NLU.

Phase 3: headset/maps use
Make the feature callable while another app is foregrounded:

persistent notification action
quick settings tile
optional accessibility-free overlay button if needed

Phase 4: background robustness
Make sure it works while screen is off, Maps is open, app is backgrounded, and battery optimization is disabled.

The main new modules you would add are:

VoiceCaptureManager
Handles speech recognizer lifecycle.

VoiceQueryParser
Very light parsing. Extract patterns like:

“play song X by Y”
“play artist Y”
“play album Z”
For v1 this can be regex/string rules.

SearchAndRankUseCase
Calls existing search API and ranks candidates.

PlaybackCoordinator
Takes ranked result and invokes the existing Tempus playback path.

VoiceSearchActivity or VoiceCommandSheet
Tiny UI for mic + fallback confirmation.

Success criteria for v1:

from app foreground: say “play hurt by johnny cash” → starts playback
from Maps foreground via notification action: opens compact voice UI, hears query, starts playback
no manual tapping of search results in the common case

So the fork strategy is:

First, add a typed “search and play best match” action.
Then replace typed input with speech.
Then expose that from notification / quick action so it works while Maps is open.
