#!/usr/bin/env node
/*
 * Believable family demo data for the store screenshots: groups (each with its calendar, lists and
 * messages) as JSON files in the app's storage format, plus an identity prefs file per phone. Used by
 * scripts/screenshots.sh.
 *
 *   node scripts/demo_data.js OUT_DIR
 * Writes OUT_DIR/{me,sarah}/lists/*.json, OUT_DIR/{me,sarah}/identity.xml.
 */
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const out = process.argv[2];
if (!out) { console.error("usage: node demo_data.js OUT_DIR"); process.exit(1); }
const now = Date.now();
const MIN = 60_000;

const ME = "demo-device-me", SARAH = "demo-device-sarah", TOM = "demo-device-tom", NAN = "demo-device-nan";
const PRIYA = "demo-device-priya", DAVE = "demo-device-dave", JO = "demo-device-jo";
const NAMES = { [ME]: "James", [SARAH]: "Sarah", [TOM]: "Tom", [NAN]: "Nan", [PRIYA]: "Priya", [DAVE]: "Dave", [JO]: "Jo" };
// Each person's colour, as the app shows them on what they do.
const COLORS = { [ME]: 0xFF24594F, [SARAH]: 0xFFD2558F, [TOM]: 0xFF3C78D8, [NAN]: 0xFFE07B2E, [PRIYA]: 0xFF8A5BC6, [DAVE]: 0xFF3F9A4E, [JO]: 0xFF23968A };

// Fresh every run: a fixed key would put each run on the same relay topic, and the relays would replay
// earlier runs' edits and members into the screenshots.
const secret = () => crypto.randomBytes(32).toString("base64url");
const uuid = () => crypto.randomUUID();
const stableId = (s) => { const h = crypto.createHash("sha1").update(s).digest("hex"); return `${h.slice(0, 8)}-${h.slice(8, 12)}-5${h.slice(13, 16)}-a${h.slice(17, 20)}-${h.slice(20, 32)}`; };

const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const day = (offset) => { const d = new Date(); d.setDate(d.getDate() + offset); return iso(d); };
const DAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"], MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const whenText = (item) => {
  if (!item.date) return "";
  const d = new Date(item.date + "T12:00:00");
  return ` on ${DAYS[d.getDay()]} ${d.getDate()} ${MONTHS[d.getMonth()]}` + (item.time ? ` at ${item.time}` : "");
};

/**
 * A space (list, diary or messages). entries: [text, by, minutesAgo, extra]; extra may hold
 * date/time (diary), heading/color, note, section (a heading's text), image, and tickedBy/tickedAgo.
 */
function space(name, kind, entries, { createdAgo = 20000 } = {}) {
  const id = uuid();
  const items = {}, audit = {};
  const headingIds = {};
  const log = (iid, ts, by, description) => {
    const aid = `${iid}/${ts}/${by}`;
    audit[aid] = { id: aid, itemId: iid, ts, by, byName: NAMES[by], description };
  };
  entries.forEach(([text, by, ago, extra = {}], i) => {
    const iid = kind === "chat" ? uuid() : stableId(`demo/${name}/${i}/${text}`);
    const added = now - ago * MIN;
    const { tickedBy, tickedAgo, section, image, ...rest } = extra;
    const lastBy = tickedBy || by, lastTs = tickedBy ? now - tickedAgo * MIN : added;
    const item = {
      id: iid, text, createdAt: added, version: { ts: lastTs, by: lastBy }, editor: NAMES[lastBy],
      pos: i + 1, posVersion: { ts: added, by }, ...rest,
    };
    if (tickedBy) item.checked = true;
    if (section) item.section = headingIds[section];
    if (item.heading) headingIds[text] = iid;
    if (item.color) item.colorVersion = { ts: added, by };
    if (item.note) item.noteVersion = { ts: added, by };
    if (image) {
      // A photo travels in base64 pieces, stored as deleted items (see Images.kt).
      const imageId = crypto.randomBytes(4).toString("hex");
      const b64 = fs.readFileSync(image).toString("base64");
      const parts = b64.match(/.{1,16000}/g);
      parts.forEach((t, n) => {
        const pid = `img:${imageId}:${n}`;
        items[pid] = { id: pid, text: t, deleted: true, createdAt: added, version: { ts: added, by }, editor: NAMES[by] };
      });
      Object.assign(item, { image: imageId, imageParts: parts.length, imageVersion: { ts: added, by } });
    }
    items[iid] = item;
    if (kind === "chat") return;
    log(iid, added, by, item.heading ? `added the heading ${text}` : `added ${text}${whenText(item)}`);
    if (item.note) log(iid, added + 1, by, `added a note to ${text}: ${item.note}`);
    if (image) log(iid, added + 2, by, `added a photo to ${text}`);
    if (tickedBy) log(iid, lastTs, tickedBy, `ticked ${text}`);
  });
  return { id, name, secret: secret(), items, audit, kind, createdHere: true, fullSynced: true, sectioned: true, addedAt: now - createdAgo * MIN };
}

/** A group holding [spaces]: one item for its name, one for its look, one per space. */
function group(name, look, spaces, people) {
  const g = space(name, "group", []);
  const by = ME;
  const items = {
    "group-name": { id: "group-name", text: name, createdAt: 0, version: { ts: now - 30000 * MIN, by }, editor: NAMES[by] },
    "group-look": { id: "group-look", text: JSON.stringify(look), createdAt: 0, version: { ts: now - 29000 * MIN, by }, editor: NAMES[by] },
  };
  spaces.forEach((s, i) => {
    s.groupId = g.id;
    items[s.id] = {
      id: s.id, text: s.name, createdAt: now - (30000 - i) * MIN, version: { ts: now - (30000 - i) * MIN, by }, editor: NAMES[by],
      spaceId: s.id, spaceSecret: s.secret, spaceKind: s.kind === "chat" ? "list" : s.kind, ...(s.kind === "chat" ? { chat: true } : {}),
    };
  });
  g.items = items;
  return { group: g, spaces, people };
}

const asset = (n) => path.join(__dirname, "..", "app", "src", "main", "assets", "backgrounds", n);

// ---- The family: the group the screenshots are about ----
const familyCalendar = space("Calendar", "diary", [
  ["School drop-off", TOM, 300, { date: day(0), time: "08:15" }],
  ["Lily — swimming lesson", SARAH, 200, { date: day(0), time: "16:00" }],
  ["Pick up dry cleaning", ME, 150, { date: day(0) }],
  ["Book club at ours", SARAH, 100, { date: day(0), time: "19:30" }],
  ["Tom — guitar lesson", TOM, 400, { date: day(0), time: "17:30" }],
  ["Dentist — Lily", SARAH, 600, { date: day(1), time: "09:30" }],
  ["Bins out", ME, 700, { date: day(1) }],
  ["Tom — football practice", TOM, 800, { date: day(1), time: "17:00" }],
  ["Dinner at Nan's", NAN, 900, { date: day(2), time: "18:00" }],
  ["Parents' evening", SARAH, 1000, { date: day(3), time: "18:30" }],
  ["Car MOT", ME, 1200, { date: day(4), time: "10:00" }],
  ["Lily's birthday party 🎈", SARAH, 1400, { date: day(5), time: "14:00" }],
  ["Football", TOM, 1500, { date: day(-1), time: "17:00" }],
]);
const groceries = space("Groceries", "list", [
  ["Fruit & veg", SARAH, 300, { heading: true, color: "green" }],
  ["Bananas", SARAH, 290, { section: "Fruit & veg", tickedBy: ME, tickedAgo: 9 }],
  ["Avocados", TOM, 120, { section: "Fruit & veg" }],
  ["Cherry tomatoes", SARAH, 100, { section: "Fruit & veg", tickedBy: ME, tickedAgo: 6 }],
  ["Dairy", SARAH, 300, { heading: true, color: "blue" }],
  ["Milk", SARAH, 280, { section: "Dairy", tickedBy: ME, tickedAgo: 12 }],
  ["Greek yoghurt", NAN, 90, { section: "Dairy", note: "The big pot, not the little ones" }],
  ["Cheddar", ME, 60, { section: "Dairy", tickedBy: ME, tickedAgo: 5 }],
  ["Bakery", SARAH, 300, { heading: true, color: "orange" }],
  ["Sourdough bread", TOM, 40, { section: "Bakery" }],
  ["Birthday candles", SARAH, 30, { section: "Bakery", note: "Lily is 7!" }],
  ["Household", SARAH, 300, { heading: true, color: "purple" }],
  ["Dishwasher tablets", TOM, 70, { section: "Household" }],
  ["Coffee beans", ME, 55, { section: "Household" }],
], { createdAgo: 400 });
const packing = space("Holiday packing", "list", [
  ["Passports", SARAH, 3000, { tickedBy: SARAH, tickedAgo: 2000 }],
  ["Sunscreen", TOM, 2500, {}],
  ["Phone chargers", ME, 2400, { tickedBy: ME, tickedAgo: 1500 }],
  ["Beach towels", SARAH, 2300, {}],
  ["Travel adaptor", TOM, 2200, {}],
  ["Snorkels", TOM, 2100, {}],
], { createdAgo: 4000 });
const garden = space("Garden jobs", "list", [
  ["Sow wildflower seeds", SARAH, 1500, { image: asset("wildflowers.webp"), note: "By the back fence, like this one at the park" }],
  ["Mow the lawn", TOM, 1400, { note: "£5 to whoever does it before Saturday" }],
  ["Plant tulip bulbs", NAN, 1300, { tickedBy: SARAH, tickedAgo: 300 }],
  ["Prune the roses", SARAH, 1200, {}],
  ["Fix the gate latch", TOM, 2000, {}],
  ["Clean the gutters", ME, 3000, { tickedBy: ME, tickedAgo: 1000 }],
  ["Buy compost", ME, 900, { tickedBy: ME, tickedAgo: 200 }],
  ["Wash the patio", SARAH, 800, {}],
  ["Repaint the fence", SARAH, 4000, { note: "Sage green? Have a look at the testers in the shed" }],
  ["Put the bird feeder up", NAN, 700, {}],
], { createdAgo: 5000 });
const familyChat = space("Messages", "chat", [
  ["Can someone grab birthday candles? Lily wants pink ones 🎂", SARAH, 190],
  ["On it 👍", ME, 182],
  ["Nan says dinner Thursday is at 6, not 7", SARAH, 120],
  ["I'll put it on the calendar", TOM, 115],
  ["Has anyone seen my football boots?", TOM, 40],
  ["By the back door, where you left them 😄", SARAH, 36],
  ["Found them, thanks Mum ❤️", TOM, 30],
  ["Pizza tonight? 🍕", SARAH, 6],
  ["Yes please!", TOM, 4],
  ["I'll pick it up on the way home", ME, 2],
]);
familyChat.readUpTo = now - 10 * MIN; // a couple unread, so the Messages tab shows a badge

const family = group("Parker family", { themeId: null, photoId: "builtin:abstract-sage", photoStrength: 0.5 },
  [familyCalendar, groceries, packing, garden, familyChat], [SARAH, TOM, NAN]);

// ---- Two more groups, so home shows more than one ----
const football = group("Saturday football", { themeId: "nord-light", photoId: "builtin:pixel-hills", photoStrength: 0.45 }, [
  space("Calendar", "diary", [
    ["Match vs Riverside (away)", DAVE, 2000, { date: day(1), time: "10:00" }],
    ["Training", DAVE, 3000, { date: day(3), time: "18:00" }],
    ["Kit wash — Priya's turn", PRIYA, 2500, { date: day(4) }],
    ["Match vs Hillside (home)", DAVE, 1900, { date: day(8), time: "09:30" }],
    ["Training", DAVE, 2900, { date: day(10), time: "18:00" }],
    ["End of season party 🏆", PRIYA, 1000, { date: day(15), time: "15:00" }],
  ]),
  space("Snack rota", "list", [
    ["Orange slices", PRIYA, 3000, {}],
    ["Water bottles", ME, 2500, { tickedBy: ME, tickedAgo: 2000 }],
    ["Half-time jelly babies", DAVE, 2000, {}],
  ]),
], [PRIYA, DAVE]);
const bookClub = group("Book club", { themeId: "rose-pine-dawn", photoId: "builtin:abstract-blush", photoStrength: 0.45 }, [
  space("Calendar", "diary", [
    ["Book club: Lessons in Chemistry", JO, 5000, { date: day(9), time: "19:30" }],
  ]),
  space("To read", "list", [
    ["The Thursday Murder Club", JO, 6000, { tickedBy: SARAH, tickedAgo: 4000 }],
    ["Lessons in Chemistry", SARAH, 5000, {}],
    ["Tomorrow, and Tomorrow, and Tomorrow", JO, 4000, {}],
  ]),
], [SARAH, JO]);

// ---- Write each phone's copy ----
for (const [who, self] of [["me", ME], ["sarah", SARAH]]) {
  fs.mkdirSync(path.join(out, who, "lists"), { recursive: true });
  for (const { group: g, spaces, people } of [family, football, bookClub]) {
    // Members are local: everyone else in the group, as this phone has seen them.
    const members = Object.fromEntries([ME, ...people].filter((d) => d !== self).map((d) => [d, NAMES[d]]));
    for (const s of [g, ...spaces]) {
      const copy = { ...s, members, readUpTo: s.kind === "chat" ? s.readUpTo || now : now };
      if (who !== "me") copy.createdHere = false;
      fs.writeFileSync(path.join(out, who, "lists", `${s.id}.json`), JSON.stringify(copy));
    }
  }
  const others = Object.fromEntries(Object.entries(NAMES).filter(([d]) => d !== self));
  const colors = Object.fromEntries(Object.entries(COLORS).filter(([d]) => d !== self));
  const esc = (o) => JSON.stringify(o).replace(/"/g, "&quot;");
  fs.writeFileSync(path.join(out, who, "identity.xml"), `<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="deviceId">${self}</string>
    <string name="deviceName">${NAMES[self]}</string>
    <string name="theme">twodo-light</string>
    <string name="knownNames">${esc(others)}</string>
    <string name="knownColors">${esc(colors)}</string>
    <long name="myColor" value="${COLORS[self]}" />
    <string name="lastView">home</string>
    <boolean name="listsSeenBaseline" value="true" />
</map>
`);
}
console.log(`wrote 3 groups for 2 phones to ${out}`);
