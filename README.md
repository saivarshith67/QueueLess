# QueueLess

**Join a queue. Skip the line.**

QueueLess turns a physical line into a virtual token. Students join a campus
queue (Exam Cell, Print Shop, …) from their phone, get a token number, and
watch their live position and wait estimate from anywhere — while staff call
the next person with a single tap.

> A physical line, turned into a virtual token — with a live, self-correcting
> wait estimate.

---

## Why

You need a hall ticket from the exam cell. There are twenty people in front of
you and no idea whether that's five minutes or fifty. So you stand there,
instead of studying, just to keep your place.

QueueLess removes the standing: the queue lives in the app, the position is
always live, and the estimate gets *more* honest as the day goes on.

---

## Features

### Students
- **Queues tab** — browse every open queue with live waiting counts and an
  estimate for a new joiner; tap a card for details and **Join queue**.
- **My Queues tab** — the hero screen: your position in line (`#3 in line`),
  your ETA, your status chip, and a cancel button. Updates in real time — no
  refresh, no pull-to-reload.
- **History tab** — every queue you've visited and what happened.
- **Google sign-in** only (Credential Manager + Firebase Auth).
- Guard rails: can't join a closed queue, a full queue, or the same queue
  twice (your existing token is returned instead).

### Staff (Admin)
- Sees **all** queues — open and closed — plus Create / Edit / Manage.
- **Control screen**: *Now serving*, a big **Call next** button, and the
  waiting list.
  - **Call next** promotes the lowest waiting token to *Now serving*.
  - **Served** finishes them (stamps the duration used by the estimator).
  - **Skip** handles a no-show.
- Roles live in Firestore (`users/{uid}.role`), so promoting someone to admin
  needs no new build — the app notices instantly.

### Under the hood
| Concept | Where it lives | In plain words |
|---------|----------------|----------------|
| Queue (FIFO) | token numbers, `Call next` | join = enqueue, call next = dequeue |
| Monotonic counter | `queue.lastTokenNumber` | numbers only go up; lowest waiting goes next |
| Derived position | `QueueMath.peopleAheadOf` | waiting people ahead + 1 — O(n), recomputed from live data |
| Sliding window | `QueueMath.minutesPerPerson` | after 3 served, ETA switches to the rolling average of the last 5 real service durations |
| Atomicity | Firestore transactions | no duplicate token numbers, no double-serve when two people act at once |
| Real-time sync | Firestore snapshot listeners | every open screen redraws itself when the data changes |

The wait estimate starts from the staff's declared average service time and
self-corrects: once a queue has served 3+ people it uses the average of the
last 5 actual `servedAt − calledAt` durations instead.

---

## Screens

```
Sign-in gate
 └── App shell (single activity, bottom nav)
      ├── Queues        → Queue detail → Join
      ├── My Queues     → position, ETA, status, leave
      └── History       → past visits
Admin mode adds:  Queue list (all) → Create / Edit / Manage
                   └── Control: Now serving · Call next · Served · Skip
```

Four sample queues are seeded on first launch: **Exam Cell**,
**HOD Consultation**, **Print Shop**, **Library Desk**.

---

## Architecture

Single-activity **MVVM** in Java:

```
Fragment (View, view-binding)  ──►  ViewModel (LiveData)  ──►  QueueRepository  ──►  Firebase
        ▲                                     ▲                      │
   live listeners                      holds the listeners      Auth + Firestore
   removed in onStop()                                            (the only class
                                                                  that touches Firebase)
```

- `MainActivity` owns the sign-in gate, the three root tabs, and the detail
  back stack (`showTab()` / `pushFragment()`).
- Screens never touch Firebase: ViewModels call `QueueRepository` and get
  either a one-shot `Callback` or a `ListenerRegistration` they remove in
  `onStop()`.
- `QueueMath` holds every position/ETA rule shared across screens.
- Offline failures are detected and surfaced as a clear message instead of a
  blank screen.
- Material 3 with dynamic colour on Android 12+; edge-to-edge throughout.

### Project structure

```
app/src/main/java/com/varshith/queueless/
├── App.java                     # Material 3 dynamic colour
├── MainActivity.java            # sign-in gate + tabs + navigation
├── data/
│   ├── Queue.java               # queues/{queueId}
│   ├── Token.java               # queues/{id}/tokens/{id}
│   ├── UserProfile.java         # users/{uid}  (role)
│   └── QueueRepository.java     # every Auth + Firestore call
├── ui/
│   ├── HomeFragment, MyQueuesFragment, HistoryFragment
│   ├── QueueDetailFragment, QueueFormFragment, AdminQueueControlFragment
│   ├── adapters/                # RecyclerView adapters
│   └── viewmodel/               # one ViewModel per screen
└── util/                        # QueueMath, StatusChip, Anim, ErrorText
```

### Firestore data model

```
users/{uid}                    role: STUDENT | ADMIN, email, displayName, createdAt
queues/{queueId}               name, description, avgServiceMinutes, isOpen,
                               capacity (0 = unlimited), lastTokenNumber, createdAt
queues/{queueId}/tokens/{id}   tokenNumber, userId, studentName,
                               status: WAITING | CALLED | SERVED | CANCELLED | NO_SHOW,
                               joinedAt, calledAt, servedAt
```

---

## Tech stack

| Layer | Choice |
|-------|--------|
| Language | Java 11 |
| UI | AndroidX, Material Components, ViewBinding, RecyclerView, Navigation via fragments |
| Architecture | MVVM (ViewModel + LiveData), single repository |
| Backend | Firebase Auth (Google Sign-In via Credential Manager), Cloud Firestore |
| Build | Gradle 9.6 (Kotlin DSL), AGP 9.4.1, version catalog (`gradle/libs.versions.toml`), `google-services` 4.5.0 |
| SDKs | `minSdk` 26 · `targetSdk`/`compileSdk` 37 |

---

## Getting started

### Prerequisites
- Android Studio (Ladybug or newer). Gradle auto-provisions the JDK 25 daemon
  toolchain (foojay resolver in `settings.gradle.kts`); app code compiles at
  Java 11.
- A Firebase project with **Authentication → Google** enabled and
  **Cloud Firestore** created

### 1. Clone and open

```bash
git clone https://github.com/saivarshith67/QueueLess.git
cd QueueLess
```

Open the folder in Android Studio and let Gradle sync.

### 2. Point it at your Firebase project

`app/google-services.json` is deliberately **not** in version control — it is
listed in `.gitignore` because it carries per-project Firebase credentials.
You must drop your own copy in before the project will build:

1. Create a Firebase project, add an Android app with package name
   `com.varshith.queueless`.
2. Download `google-services.json` into `app/google-services.json`.
3. In **Authentication → Sign-in method**, enable **Google**, and make sure a
   **Web OAuth client** exists (the plugin turns it into
   `R.string.default_web_client_id`, which the sign-in flow looks up by name).
4. Create a **Cloud Firestore** database.

Without that file the build stops at the `google-services` plugin with a
*"google-services.json is missing"* error. Once the file exists but Google
auth isn't enabled yet, the app builds and the sign-in button shows
*"Google Sign-In isn't configured"* instead of crashing.

### 3. Run

```bash
./gradlew :app:assembleDebug     # or press Run in Android Studio
```

Release builds use R8 with the rules in `app/src/main/keepRules/rules.keep`:

```bash
./gradlew :app:assembleRelease
```

### 4. Make someone an admin

On first sign-in the app seeds `users/{uid}` with `role: "STUDENT"`. Open that
document in the Firestore console and set `role` to `ADMIN` — the app picks the
change up live, no rebuild.

---

## Testing

```bash
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (device/emulator)
```

---

## Roadmap ideas

- Notifications when you're ~2 tokens away from being called
- Estimated-accuracy indicator (how far the rolling average has drifted)
- Server-side Firestore security rules + composite indexes as a versioned file
- Multi-campus / multi-desk support and queue analytics for staff
