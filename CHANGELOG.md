# Changelog

Release notes for each version. `scripts/release.ps1` publishes the **Unreleased** section as the
GitHub release notes and renames it to the version, so this file and the releases always match.

## Unreleased

**Our own relay.** Intack now has its own relay, relay.intack.app, alongside the public ones, so
changes get through even when the free relays are busy or slow. Like the others it only ever sees
encrypted data, and it refuses anything that isn't Intack's.

## v0.7.1 — 2026-10-17

**Phones can sleep.** When you change something, the other members' phones now get a tiny "something
changed" push and fetch the change, instead of each phone staying connected all the time. Messages
arrive within seconds, even when the other phone is asleep. The push carries no content, just a random
code only your group's phones know (see the privacy page). Because of this, **Sync in background** is
now off by default; turn it on in Settings if changes reach you late, or if your phone has no Google
Play services. Also: the app no longer keeps re-sending stale "I'm online" notes to a relay that has
asked it to slow down.

## v0.7.0 — 2026-10-16

**TwoDo is now Intack.** Same app, new name, installed as a new app (`app.intack`), so it starts
empty. To bring a group across, make an invite in TwoDo (open the group, Invite, Send link, Copy) and
paste it into Intack under Join with an invite. Its lists, calendar and messages come with it. Once
everything is there you can remove TwoDo. Invites now use intack.app links; old TwoDo invite links
still work.

## v0.6.37 — 2026-10-16

**TwoDo is now Intack.** This is the last TwoDo update. Intack is the same app under its new name,
and installs as a separate app: add github.com/rad-corps/intack in Obtainium. Then bring each group
across with an invite from TwoDo (open the group, Invite, Send link, Copy; in Intack, Join with an
invite and paste). Once your groups show up in Intack you can remove TwoDo.

## v0.6.36 — 2026-10-16

**One look everywhere.** The style you or your group choose (cards, lines, outlined or plain) now
applies to the groups on the home screen, the lists in a group, the calendar and group settings,
not just to list items. Cards no longer show a darker band inside them with see-through themes
such as Woodland.

## v0.6.35 — 2026-10-16

**When each list was made and last changed.** Lists now show "Created Mon 12 Oct · edited 5 min
ago" under their name, so this week's Groceries is easy to tell from last week's.

## v0.6.34 — 2026-10-15

**Much less battery in the background.** With the app closed, TwoDo kept direct connections to
everyone open, and they talked several times a second, so the phone's radio never rested. Now,
20 seconds after you leave the app, it keeps just two quiet relay connections, which still bring in
changes and messages for notifications. Measured on a test phone: about 150 times less network
traffic in the background. Opening the app reconnects directly as before.

## v0.6.33 — 2026-10-15

**Messages.** Each group has a Messages tab: a conversation for everyone in the group, newest at
the bottom. A badge on the tab counts messages you haven't seen, and new messages come as
notifications (turn them off under Notifications › Messages). Everyone in the group needs this
version: older versions show the messages as a list called "Messages".

## v0.6.32 — 2026-10-15

**Tidier Lists tab.** The "Move a list into …" link under New list is gone.

## v0.6.31 — 2026-10-15

**Notes on list items.** Tap an item to add a note: more detail, a link, or how it went. The start of
the note shows under the item, and everyone sees it. Writing a note never clashes with someone
ticking the item at the same time.

**Archiving a finished list closes it.** Tapping Archive on the "All done!" prompt now takes you
back to your lists, like Archive in the ⋮ menu. To bring an archived list back, open it and choose
**Bring back** from its ⋮ menu, so following the prompts can't archive and unarchive in a loop.

## v0.6.30 — 2026-10-13

**Group settings match your look.** The people and settings rows in a group's settings now take the
same style as list items: cards or outlines, borders, item colours, shadows and spacing. Section
titles use the heading colour.

**See-through items.** Item backgrounds are now half as opaque, so your background shows through a
little.

## v0.6.29 — 2026-10-13

**Sky Hills goes retro.** The Sky Hills starter theme now has its own pixel-art background: a deep
blue sky with blocky clouds above a grassy edge and brown checkerboard ground. Its cards are now navy
with grass-green edges, and its headings gold. The background is also available on its own as
"Pixel hills" under Nature.

## v0.6.28 — 2026-10-13

**Starter themes.** Not sure where to begin? The Look editor now has six ready-made themes, each a
single tap:
- **Cyberdeck:** LED green on black, like an old desktop media player.
- **Woodland:** forest greens and bark browns.
- **Constructivist:** red and black on aged paper, with bold rules.
- **Sunshine:** bright and cheerful.
- **Sky Hills:** a big blue sky with grassy green.
- **Party:** hot pink and purple disco.

Each one is just theme code, so open the code afterwards to see what it set and tweak it.

The "Lines" item style now uses the border thickness, so lines can be bold.

## v0.6.27 — 2026-10-13

**Make it yours.** The Look editor has a new Style section:
- **Spacing:** compact, comfortable or roomy.
- **Items:** plain, with lines between, as cards, or outlined, with rounder or squarer corners.
- **Borders:** solid, dashed or dotted, any thickness and colour.
- **Shadows** under cards.
- **Colours:** item background, headings (secondary), highlights (tertiary) and the page itself.
- **Font:** default, serif, mono or handwritten.

The preview now stays at the top while you scroll through the options. Groups share their style with
everyone in them, like the rest of their look.

**Theme code, for tinkerers.** Write or paste a whole look like old-school CSS, for example
`background: #1b1b2f; items: cards; border: 2px dashed hotpink; font: mono;`. Copy yours to share
it. It can only set the options above, so a pasted theme is always safe.

## v0.6.26 — 2026-10-13

**A tidier look editor.** All the backgrounds are now in one row you swipe through, with each group
(Soft colours, Yours, Home, Nature, Seasons) marked by a slim divider with its name. The preview stays
in view while you choose. It opens on your current background.

**16 soft colours, first in line.** Twelve new colour washes (Blush, Peach, Butter, Sand, Mint, Sky,
Mist, Lilac, Ocean, Moss, Plum and Ember) join Dawn, Sage, Lagoon and Dusk, which are a little
smoother now. Light ones come first, then dark.

## v0.6.25 — 2026-10-12

**Dragging headings fixed properly.** Moving a heading a second time could put it in the wrong place
and leave the list looking jumbled; that's fixed. While you drag a heading, its items now travel with
it as one block, so you can see the whole section move. A heading dropped in the middle of another
section goes to whichever end of it is nearer (or the way you were dragging, if it's halfway).

**Sections repair themselves.** Phones that briefly ran an older version could lose which heading
items belong to. They now get it back from the others the next time they sync.

## v0.6.24 — 2026-10-12

**Items always stay under their heading.** Each item now remembers which heading it's under, so moving
a heading always brings its items, even if someone adds or moves items in that section at the same
moment on another phone. Everyone in a list needs this version for headings to show right.

**See where an item will land.** While you drag an item, the heading of the section it's about to join
lights up.

**Fixes**
- Someone who'd just changed their name could still show up under their old name (or their phone's
  model) on other phones. And a phone that hasn't been given a name yet no longer announces its model;
  it shows as "Someone new".
- Going back from a list in a group returns to the Lists tab, not the calendar.
- Leaving a group goes to the home screen.
- A list with only headings says "Nothing on it yet" rather than "All done".

## v0.6.23 — 2026-10-12

**No more "Unknown device".** For a moment after connecting, before the other phone has said who it
is, a list could show "In sync with Unknown device". It now says "Connecting…" until it knows.

## v0.6.22 — 2026-10-12

**Moving a heading moves its section.** Drag a heading and everything under it comes along: its items
tuck in under it while you drag and land with it. Dropping it in the middle of another section puts it
after that section rather than splitting it.

## v0.6.21 — 2026-10-12

**What's on, right on the home screen.** Home now opens with today's schedule from all your calendars.
On a quiet day it also shows the next few days. Tap an entry to open its calendar.

**Groups first.** Lists live in groups: make new ones from a group's Lists tab. Lists you already have
outside any group are still there, under the small "Lists outside groups" link at the bottom of home.

## v0.6.20 — 2026-10-11

**A home screen.** Pressing back in a group no longer drops you out of the app: it goes to a new home
screen showing all your groups (with what's on today, what's left to do and who's in each), your other
lists, all calendars, starting or joining a group, and settings. Back from there leaves the app as
usual. Home is also at the top of the group menu.

## v0.6.19 — 2026-10-11

**Ready-made lists.** A new list can start from Groceries (with headings Fresh, Bakery, Fridge, Freezer,
Pantry, Household), To do, Packing, Meal plan or Blank. A new group's shopping list starts as Groceries.
Headings are like any others: tap one to rename or remove it, and add your own from the ⋮ menu. If you'd
rather not see them, ⋮ › Hide headings hides them on your phone only.

**New items go to the top in lists with headings,** above the first heading, ready to drag or move
into a section, instead of landing in the last section.

## v0.6.18 — 2026-10-11

**Backgrounds.** Choose from 17 built-in backgrounds: corkboard, notepaper and linen, the sea, forest,
lakes and wildflowers, one for each season, and four soft colour washes. The app's colours are picked to
match. Your own photos still work as before. Backgrounds now go everywhere: the app's home screen
(Settings › Look), any list or diary outside a group (⋮ › Look, just on your phone), and groups, where
everyone sees the one chosen. A built-in background costs nothing to share with your group: only its
name is sent.

## v0.6.17 — 2026-10-11

**Archive finished lists.** Every list you have open keeps in touch with the others' phones, which uses
battery. Now, when everything on a list is ticked, the app offers to archive it, and any list can be
archived from its ⋮ menu. Archived lists stop syncing and wait under "Archived" at the bottom of the
screen; open one and tap Bring back to start syncing it again.

**Everyone's asked when a list is archived or deleted.** If someone archives or deletes a list you
share, your phone asks whether to archive it, delete it, or keep it (keeping it means it carries on
syncing, using some battery). Deleting a list outside a group now asks the others too; to just put it
out of the way, archive it instead.

## v0.6.16 — 2026-10-11

**Much less battery and data use.** Thanks to the new diagnostics, we found the app kept setting up
new connections for every list every 30 seconds, even when it was already connected, using up to a
core's worth of processor and a lot of mobile data. Now it only looks for the others when starting,
when the network or the app comes back, and less and less often while nobody answers. Once connected,
it just waits quietly. Changes still arrive straight away.

## v0.6.15 — 2026-10-11

**Battery diagnostics.** A new switch, Settings › Diagnostics, shows a small readout of how hard the
app is working on top of every screen. Tap it to see where the battery goes: processor time by part of
the app, on screen versus in the background, a per-minute history for the last hour, network use, and
how often it talks to the servers. With the switch on, leave your phone for a while, then open the app,
tap Copy and send us the text. It helps us track down battery drain. Turn it off again in Settings.

## v0.6.14 — 2026-10-11

**More useful crash reports.** When the app closes because of a crash in the connection code, the
crash report now says exactly where it happened (the crashing thread and the steps leading to it) and
which version was running, instead of a jumble of names. If you've opted in to crash reports, those are
what we need to fix the rest of these crashes.

## v0.6.13 — 2026-10-10

**Fixed: the app closing by itself, again and again.** When a connection between phones was shut down
while it was still being set up (e.g. both phones tried at once, or the network changed), the app could
crash in the connection code. It now waits until nothing is using a connection before freeing it.

**Crash reports include crashes in the connection code.** These closed the app without being noted;
they're now picked up from Android's record when the app next opens (Android 11 and later).

## v0.6.12 — 2026-10-10

**Times typed in calendar entries.** Type "Dentist 11:30am" (or "at 11:30 am", "7pm", "14:30", "3-4pm",
"at noon") and the time is set for you; "Dentist" is what's saved. Bare numbers ("at 3", "2-3 apples")
and prices ("11.30") are left alone. "3:30" with no am/pm means the afternoon. Setting the time
yourself always wins.

## v0.6.11 — 2026-10-10

**Edit list items.** Tap an item's words to change them, move it to a section, or delete it. Tick it
with its checkbox. (The ✕ on each row is gone: delete is in the item, so nothing goes by accident.)

**Sections in lists.** Add headings like "Dairy" or "Fruit & veg" (⋮ › Add a heading). Items below a
heading are in its section; put an item in a section from the item itself, or drag. Tap a heading to
rename or delete it — deleting a heading keeps its items. Headings don't count as things to do.
Older versions show headings as ordinary items.

## v0.6.10 — 2026-10-10

- The Notifications settings screen now uses the app's own theme instead of the look of the group you
  opened it from (its settings are for the whole app).

## v0.6.9 — 2026-10-10

**Your own colour.** Pick one in App settings (under your name). Everyone sees what you do in it: the
tick and "Sarah · 5 min ago" on items you tick, your name on calendar entries you add, in History, and
your circle in Group settings. Until you pick one, you have one chosen automatically.

## v0.6.8 — 2026-10-10

**Notification settings** (App settings › Notifications), for all your groups and lists:
- **Daily schedule** — what's on today, each morning at a time you choose (7:30 to start).
- **Reminders** before anything with a time: 10 minutes, 30 minutes or an hour before (off to start).
- Choose which changes by others to hear about: things added to the calendar, calendar changes, items
  added to lists, items ticked off (off to start), people joining or leaving, group changes, conflicts.
- Each kind has its own Android notification category, for sounds and vibration.

## v0.6.7 — 2026-10-10

- Fix: the app could crash in the background (seemingly at random) when Android restarted background
  sync after closing the app. Background sync now waits until the app is next opened instead.

## v0.6.6 — 2026-10-09

**Simpler calendar entries.** Adding or changing something on the calendar now uses one panel that slides
up from the bottom, wherever you start from:
- What's happening, then the day — **Today**, **Tomorrow** or **Pick a day…**
- **All day** or **At a time**, with big up and down buttons for the hour, minutes (in 15-minute steps)
  and AM/PM. Tap the time to type an exact one.
- One big **Add** / **Save** button; when changing an entry, **Delete** and its history are there too.

## v0.6.5 — 2026-10-09

- Lists: "Add an item" is now at the bottom of the screen, like the calendar, and the list scrolls to
  show what you've just added.
- **New list** is now at the bottom of the Lists tab.
- The People tab is now **Group settings** (still with the group's people and invites), and Settings in
  the group menu is now **App settings**.

## v0.6.4 — 2026-10-09

- **Optional crash reports.** Turn on Settings › Offer to email crash reports, and if the app closes
  unexpectedly it offers to email a report to the developer. It opens in your email app so you see what's
  sent (versions and where the error happened — nothing from your lists) and only goes if you send it.
- Settings switches now toggle when you tap anywhere on their row.

## v0.6.3 — 2026-10-09

- Calendars now open on the **Schedule** — everything coming up on one page. Tap a day's heading (or
  **Day**) to see a single day.
- A new calendar starts with one entry marking when it was created.

## v0.6.2 — 2026-10-09

- The calendar's "Add to …" box is now at the bottom of the screen, below the day's entries, and
  moves up above the keyboard while you type.

## v0.6.1 — 2026-10-09

- Fix: saving a group's background photo didn't take effect for most photos (and could clear the
  previous one). Group changes now always finish, even when the screen closes straight away.

## v0.6.0 — 2026-10-09

**Groups.** Share one calendar and your lists with the same people — your household, say — by
inviting them once. A group has three tabs: **Calendar**, **Lists** and **People**.
- Starting a group gives it a calendar and a shopping list; add more lists any time. Everyone in the
  group has the same calendar, so nobody ends up with two.
- **Invite** someone once and they get the calendar and every list, including ones added later.
- Already have lists? When you start a group you can use your diary as its calendar and bring your
  lists along. Lists outside groups are still there under **Other lists**.

**Make it yours.** Give a group a **background photo** — its colours can be taken from the photo — or
pick colours and a highlight colour. Everyone in the group sees the same look.

**Schedule view.** Calendars can show everything coming up on one page (Calendar › Schedule). In more
than one group? **All calendars** puts them together.

Also:
- **Text size** setting (Normal, Large, Larger).
- Simpler wording: "In sync with Sarah" instead of connection details.
- A welcome screen for new users.

## v0.5.1 — 2026-10-09

- Maintenance release to check the release pipeline from a new build machine. No changes to the app.

## v0.5.0 — 2026-10-09

**Themes.** Pick a colour theme for the app (Settings › Theme) and give any list or diary its own
(⋮ › Theme) to tell them apart at a glance. Fifteen to start: TwoDo Dark and Light, Solarized Dark and
Light, Nord and Nord Light, Dracula, Gruvbox Dark and Light, Catppuccin Mocha and Latte, Tokyo Night,
Rosé Pine and Rosé Pine Dawn, and One Dark. Themes are just for your phone — they aren't shared.

**Icons.** Lists and diaries now have their own icons (a checklist and a calendar), on the home screen —
in each one's theme colours — next to the name when it's open, and when creating something new.
The old dark mode switch is replaced by the theme setting (dark mode off becomes TwoDo Light).

## v0.4.0 — 2026-10-08

**Lists now sync even when the phones can't connect directly — and when one of them is off.**
- When two phones can't reach each other (common on mobile data), changes now go through free public
  Nostr relays instead. They're end-to-end encrypted: the relays only ever see scrambled data.
- Changes made while the other phone is off are kept on the relays for 14 days and arrive as soon as it
  comes back — the two phones no longer need to be online at the same time.
- Joining works through the relays too: the new phone asks, and anyone in the list who's online sends it.
- The status line shows "Syncing with Sam via relay" when that's the route being used.

Also:
- Fix: after a large sync, the last part could stay unsaved until the next sync.
- Settings > Connection log also shows relay activity.

## v0.3.3 — 2026-10-08

- **Much less background traffic and battery use.** When the other phone can't be reached, TwoDo now looks for it less and less often (up to every 2 minutes) instead of every 10 seconds, and looks quickly again as soon as you open the app, change network, or show a share code.
- Smoother connecting: cleaning up failed connection attempts no longer holds up the rest of the app.

## v0.3.2 — 2026-10-08

- **Faster joining when TwoDo is already running:** the app now keeps its connections to the meeting-point servers open and shares them between lists, so a newly opened share link connects in about a second.
- **The sharing phone looks harder while its QR/share dialog is open**, so the other phone is found straight away.
- **Settings > Connection log:** shows what happened while connecting (with timings). If joining is slow, tap Copy and send the log over so we can see where the time went.

## v0.3.1 — 2026-10-08

**Much faster joining.** Opening a shared list or diary now connects and syncs in about 2 seconds (was 30-60 s, and sometimes didn't finish). Big diaries sync in a few seconds.

**You can see what's happening while someone joins:**
- The joining phone shows "Connecting… / Looking for the other phone… / Getting the list…", then "Joined “Shop” with Adam".
- The sharing phone's QR dialog shows "Waiting for someone to scan…", then "✓ Sam joined".

**Sync in background is now on by default**, so the other phone can reach yours while TwoDo is closed (you can turn it off in Settings).

Also: connection attempts that stall are retried after 5 s instead of 15, and the status line only says "Offline" when the phone really has no network.

## v0.3.0 — 2026-10-08

**New: shared diaries.** New > Diary. Opens on today; swipe (or the arrows) to change day, tap a day in the week strip, or tap the date for a calendar where you can also type a date. Add entries with an optional time; tap one to edit, move or delete it.

**New: History.** Every list and diary keeps a shared log of who changed what and when (menu > History; diary entries also show their own history).

Also:
- Big lists and diaries now sync (updates over ~256 KB used to fail).
- Conflicting diary edits are reported with the day/time that won.
- Replaced two public meeting-point servers that had stopped working.
- Faster, smaller release build.

Update every phone before sharing a diary: 0.2.x doesn't know about diaries and could strip entries' dates when editing them.

## v0.2.1 — 2026-10-08

- Fix: "Remove from this phone" now really removes the list. On 0.2.0 the list came back (including after a restart). Lists you tried to remove on 0.2.0 are still there; remove them again after updating.

## v0.2.0 — 2026-10-08

- Notifications: someone joining or leaving a list, and a quiet summary of changes others make while TwoDo is closed (Settings toggle). Changed rows briefly light up while you're looking at the list.
- Share links are now normal web links (tappable in Messenger etc.) that open TwoDo. Older twodo:// links still work.
- Join a list from a screenshot of its QR code (Join list > Choose screenshot, or share the image to TwoDo).
- Removing a list tells the others.

Update all phones before sharing new lists: v0.1.0 doesn't understand the new links.

## v0.1.0 — 2026-10-08

First release: shared lists over peer-to-peer sync, QR sharing, drag to reorder, who-ticked-what, conflict notices, dark mode.
