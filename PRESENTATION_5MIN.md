# QueueLess — 5-Minute Presentation Script

**Format:** live demo on emulator/phone · **Hard cap:** 5:00
**One-line goal:** the audience sees the app work, then understands the queue data structure and the self-correcting wait estimator behind it.
**Core message:** *A physical line, turned into a virtual token — with a live, self-correcting wait estimate.*

> [!tip] Before you start
> - Two Google accounts ready: one normal **Student**, one set to **Admin** in Firestore.
> - Log in on both (or one device + one emulator). Have the student already signed in.
> - Queues are auto-seeded on first launch (Exam Cell, HOD Consultation, Print Shop, Library Desk).
> - Put the phone on **Airplane mode for one second and back** only if you want the "offline" safety-net beat (optional, cut if tight).
> - Rehearse the click path once end-to-end. The live listeners are the star — do not rush past the number changing.

---

## Time budget

| # | Segment | Clock | What the audience gets |
|---|---------|-------|------------------------|
| 1 | Hook + problem | 0:00 – 0:30 | *Why lines hurt* |
| 2 | What QueueLess is | 0:30 – 1:00 | The pitch + the three tabs |
| 3 | Live demo | 1:00 – 2:45 | The app actually working |
| 4 | Under the hood (DSA) | 2:45 – 4:00 | FIFO queue + estimator + transactions |
| 5 | Impact + close | 4:00 – 5:00 | Value, future, memorable line |

---

## 1 — Hook + problem · 0:00–0:30

**On screen:** the Sign-in gate (or the Queues list, whatever you open with). Keep it still.

**Say:**
> "You need a hall ticket from the exam cell. You walk over — there's a line of twenty people. You've got no idea if that's five minutes or fifty. So you stand there, instead of studying, just to keep your place.
>
> That's the problem **QueueLess** solves."

**Delivery note:** pause after "just to keep your place." Let the frustration land.

---

## 2 — What QueueLess is · 0:30–1:00

**On screen:** the **Queues** tab (Home). Point at the four queue cards with their "N waiting" and minute estimates.

**Say:**
> "QueueLess is an Android app that turns a physical line into a virtual token. You join a queue from your phone, get a token number, and watch your live position and wait time — from anywhere on campus. The staff get a control panel that calls the next person with one tap.
>
> Three tabs: **Queues** to browse and join, **My Queues** to track your spot, and **History** to see where you've been."

**Delivery note:** keep this to 30 seconds. You want to reach the demo fast.

---

## 3 — Live demo · 1:00–2:45

> [!note] This is the core of the talk. Narrate what you tap, but let the live updates breathe.

### 3a — Student joins a queue (≈ 60 s)

**Click path:**
1. On **Queues**, tap **Print Shop** (3 min/person, capacity 25).
2. Detail screen appears → point at **"3 waiting"**, **"3 min per person"**, **"Capacity: 25"**, and the estimate **"About 9 min for a new joiner"**.
3. Tap **Join queue**.
4. Snackbar: *"You're in! Check My Queues."* Button flips to **Joined**.
5. Tap the **My Queues** tab.

**Say (as you go):**
> "Here's Print Shop. It's live — three people waiting, three minutes per person, so a new joiner waits about nine minutes. I join… and now I'm in.
>
> And here's **My Queues** — the hero screen. My card shows my **position in line**, my **ETA**, my status chip, and a cancel button. This is the whole point: I don't have to stand in the corridor anymore."

### 3b — The live moment (≈ 15 s)

**Click path:** stay on **My Queues** (or open the queue detail), and on the **admin** device tap **Call next**.

**Say:**
> "Watch — this number updates in real time. No refresh, no pull-to-reload. The moment staff call the next person, my position and my estimate move."

**Delivery note:** if you only have one device, show the admin screen first and come back; or mention that the same account can hold both roles. Do **not** skip this beat — real-time sync is half the wow.

### 3c — Admin calls the next person (≈ 30 s)

**On screen (Admin account):** Home in **Admin** mode → **Manage**.

**Click path:**
1. Point at **Admin** mode: you see *every* queue (open and closed) plus **Manage / Edit / Create** buttons — students only see open ones.
2. Tap **Manage** on Print Shop → the **control screen**.
3. Point at **Now serving**, the big **Call next** button, and the **Waiting (n)** list.
4. Tap **Call next** → the lowest-numbered waiting token jumps to **Now serving**.
5. Tap **Served** → the card moves into **History** (switch to the student's **History** tab to show it).

**Say:**
> "This is what the staff see. One screen, one tap: **Call next** promotes the lowest waiting number, **Served** finishes them, **Skip** handles a no-show. And because the role lives in the database, I can make someone an admin without a new build — the app notices instantly."

---

## 4 — Under the hood: the DSA · 2:45–4:00

**On screen:** you can stay on an app screen; this segment is mostly talking. Point at the queue list and a token card as you mention them.

**Say (five beats — keep each to ~12 seconds):**

> **1. It's a queue — FIFO.**
> "Strip away the UI and the core is a classic **queue**: first in, first out. Joining is *enqueue*; **Call next** is *dequeue*. The person with the lowest token number always goes first."

> **2. Order is a monotonic counter.**
> "Order isn't guessed — every token number comes from a counter stored on the queue, so numbers only ever go up. 'Next' is simply 'lowest number still waiting.'"

> **3. Position is computed, not stored.**
> "A card's position is derived on the fly: count the waiting tokens ahead of mine, plus one. That's a linear scan over one queue's tokens — **O(n)** — and it's always correct because it's recomputed from the live data."

> **4. The wait estimate self-corrects.**
> "The ETA starts from the staff's average service time — but once three people have been served, it switches to a **rolling average of the last five actual service durations**. So the estimate gets more honest as the day goes on. That's a sliding window, not a fixed guess."

> **5. Two operations must be atomic.**
> "Assigning a token number and promoting the next person both run inside a **database transaction**. So if two students tap *Join* at the same instant, the counter can't hand out the same number twice, and *Call next* can't skip or double-serve anyone."

**Optional (only if you have spare seconds):**
> "Structurally it's MVVM: screens talk to ViewModels, ViewModels talk to one repository, the repository talks to Firestore, and live listeners redraw the UI."

---

## 5 — Impact + close · 4:00–5:00

**On screen:** back on the **Queues** tab, app idle and live.

**Say:**
> "So what actually changes? Students stop physically waiting — they get an honest, live estimate and can study until it's their turn. Staff get a single screen and one-tap control. And the estimate improves the more the queue is used.
>
> QueueLess is built in **Java on Android**, with **Firebase Auth** for Google sign-in and **Firestore** for real-time data — and at its core, a **FIFO queue with a self-correcting estimator**.
>
> Join a queue. Skip the line. Thank you."

---

## DSA cheat card (if you blank)

| Concept | Where it lives | In plain words |
|---------|----------------|----------------|
| Queue (FIFO) | token numbers, `Call next` | join = enqueue, call next = dequeue |
| Monotonic counter | `queue.lastTokenNumber` | numbers only go up; lowest waiting goes next |
| Linear scan | `QueueMath.peopleAheadOf` | position = waiting people ahead + 1, O(n) |
| Sliding window | `QueueMath.minutesPerPerson` | average of last 5 served durations after 3 served |
| Atomicity | Firestore transactions | no duplicate token numbers, no double-serve |

---

## Likely questions — 15-second answers

- **"Why not just a simple list?"** → A list can't sync across phones in real time or stay consistent when two people act at once. Firestore gives both.
- **"How do you stop someone joining twice?"** → Before assigning a token we read the queue's active tokens; if you already hold one, we return it instead of creating a second.
- **"Two staff tap *Call next* at once?"** → It runs in a transaction that re-checks the token is still *Waiting*, so only one wins.
- **"How is the ETA calculated?"** → `QueueMath`: people-ahead × minutes-per-person, rounded up; minutes-per-person is the rolling average of the last five served durations once the queue warms up.
- **"What if the phone is offline?"** → The repository detects it and the UI shows a clear offline message instead of a blank screen.
- **"How do roles work?"** → A `role` field on `users/{uid}` in Firestore; the app only reads it, so admins are configured from the console, not the app.
- **"Complexity?"** → Position and ETA are O(n) over one queue's tokens; a join is O(1) inside the transaction plus one active-token read.

---

## Slide mapping (optional — if you show a deck)

| Slide | Ties to segment |
|-------|-----------------|
| 1. Title — "QueueLess — Join a queue. Skip the line." | 1 |
| 2. The problem (photo of a long line) | 1 |
| 3. What it is + 3 tabs | 2 |
| 4. *(live demo — blank/logo slide)* | 3 |
| 5. Core: FIFO queue + self-correcting ETA | 4 |
| 6. Architecture: MVVM + Firestore | 4 |
| 7. Impact + future + thank you | 5 |

---

## Delivery tips

- **Time the demo first.** The click path alone should fit in 90 seconds; if it runs long, cut the "live moment" narration, not the tap.
- **Have a fallback screenshot** of each screen in case sign-in or the network misbehaves on stage.
- **Say the number out loud** as it changes ("position drops from 3 to 2") — it makes the real-time behaviour obvious.
- **Don't read the script.** Glance at headings only; the table above is your actual cue sheet.
- **End on the tagline.** "Join a queue. Skip the line." is in the app — mirror it.
