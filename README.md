# Haven

Roommates sharing a home often struggle to manage shared expenses and household chores fairly. Bills get split inconsistently, payments are forgotten, and chores fall unevenly on one or two people. Because these issues are usually tracked informally, through group chats or scattered payment apps, there’s no single, transparent record of who owes what or who's done what. This lack of visibility leads to resentment, awkward confrontations, and strained relationships between roommates.

We propose ‘Haven’, a shared household management app that centralizes logging and tracking of shared expenses, chores for all occupants in the same household, and a shared grocery list, along with widgets personalized for the user.

**Stack:** Kotlin · Jetpack Compose (Material 3) · MVVM (ViewModel + StateFlow) · Navigation Compose · Room · Firebase Authentication, Cloud Firestore, Cloud Storage, Cloud Messaging and Cloud Functions (Node.js) · CameraX + ML Kit · OkHttp · Coil

**Status:** Update 2 (feature-complete beta). Remaining work is listed under [To Do](#to-do-before-the-final-dec-8).

---

## Features

### Account & household
- Sign up/log in with email and password (Firebase Auth); returning users go straight to their household
- Create a household, or join one with a 6-character invite code or by scanning the household's QR code
- Household details: name, invite code (with regenerate), member count
- Members popup: current members and collapsible past members
- Leave household: only allowed once all balances are settled
- Profile screen: name, email, join date, household, notification settings, log out
- Profile picture upload (Android photo picker -> Firebase Storage -> shown with Coil)

### Dashboard
- Balance card ("You owe/You're owed") that opens the Ledger
- Expense summary card that flips between totals and a spending-by-category chart (stops auto-flipping when TalkBack is on)
- Chores card (what's due, or an "all caught up" message) and grocery card (items to buy, "+N more")
- Top bar with navigation drawer (Profile, Household)

### Expenses
- Ledger: balance card, category filter, "My expenses" filter
- Add expense: description, amount, category, who paid, even split or custom split
- Edit and delete expenses (with confirmation)
- Expenses involving a member who left the household are locked (can't be edited or deleted)
- Unsaved add expense forms are kept as drafts in Room and restored next time
- Settle up: balance with each roommate, full or partial payments; all balances update live
- Push notification to roommates when an expense is added or edited, or a settlement is made; tapping it opens the Ledger

### Chores
- Add, edit, complete and delete chores, with categories and notes
- One time or repeating (daily, weekly, every 2 weeks), with an optional end date
- Household or personal chores (personal chores are only visible to their owner)
- Manual assignment, or automatic assignment to the roommate furthest below their fair share of completed chores (fair share is based on days spent in the household, not counting vacations)
- "Take turns": a repeating chore passes to the next roommate after each completion
- Undo after marking a chore done
- Add chore forms are kept as drafts in Room

### Vacation mode
- Turned on from Profile; blocked until the user's unfinished household chores are done or handed over
- Handovers are offers the roommate can accept or decline; a declined chore is offered to the next roommate who hasn't been asked
- While away, the user is skipped by automatic assignment and their days away don't count against their fair share

### Groceries
- Shared household list with To Buy and Bought sections; add, edit (name and note) and delete items
- Checking an item off records who bought it and when; only the buyer can uncheck it
- Suggestions while typing: the household's past items plus generic food names from the USDA FoodData Central API (debounced, falls back to history only when offline)
- Barcode scanning with CameraX + ML Kit; the product is looked up with the Open Food Facts API (loading, found, not found and error states, with retry)
- "Add to expense": enter a price for each bought item, then the Add Expense form opens prefilled with the total; the items leave the list once the expense is saved
- Bought history grouped by day

### Data & offline
- Firestore is the shared source of truth between roommates, with live listeners so changes appear on every phone instantly
- Expenses and settlements are cached in Room, so the Ledger and balances work offline
- Writes made offline are queued and synced automatically when back online
- Local data (Room) is cleared on logout, and the device's push token is removed from the user's account

### Privacy & permissions
- The camera is only requested when scanning; if it's denied, the user can type the item instead or open Settings
- Barcode scanning runs on the device; only the barcode number is sent to Open Food Facts, never images
- Notifications follow the Android system setting; the Profile switch asks for permission or opens the system settings
- API keys live in git-ignored `local.properties` and reach the app through `BuildConfig`

---

## Setup Instructions

### 1. Firebase configuration
Haven requires a `google-services.json` file to connect to Firebase.

1. Open the [Firebase Console](https://console.firebase.google.com/).
2. Create or select the Firebase project associated with Haven.
3. Navigate to **Project Settings** → **General**.
4. Under **Your apps**, select the Android application.
5. Find the **SDK setup and configuration** section and download `google-services.json`.
6. Place the downloaded file directly inside the project's `app/` directory.

### 2. USDA API key (grocery suggestions)
1. Get a free key from [api.data.gov](https://api.data.gov/signup/).
2. Add it to `local.properties` in the project root (this file is git-ignored):
   ```properties
   USDA_API_KEY=your_key_here
   ```
*Without a key, the app still runs; suggestions just come from the household's past items only. Open Food Facts doesn't need a key.

### 3. Cloud Functions (push notifications)
The functions in `functions/` (Node.js 24) send a push notification when an expense or settlement is written to Firestore.

```bash
cd functions
npm install -g firebase-tools
firebase login
firebase projects:list
firebase init functions
firebase deploy --only functions
```

### Final layout

```text
Haven/
├── app/
│   ├── google-services.json
│   ├── build.gradle.kts
│   └── src/
├── functions/
│   └── index.js
├── build.gradle.kts
├── local.properties        (USDA_API_KEY, not committed)
└── settings.gradle.kts
```

### Required Firebase services
- Firebase Authentication (Email/Password)
- Cloud Firestore
- Cloud Storage (profile pictures)
- Cloud Messaging (push notifications)
- Cloud Functions

---

## Build and Run

1. Open the project in Android Studio.
2. Complete the setup steps above.
3. Allow Gradle to sync and download the required dependencies.
4. Connect an Android device or start an emulator.
5. Build and run the application.

### Tests
- **Unit tests** (`app/src/test`): expense splits and balances, locked expenses, chore auto-assignment, vacation rules, grocery history and USDA/Open Food Facts parsing. They run on the computer, no emulator needed:
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```
- **Compose UI tests** (`app/src/androidTest`): dashboard widgets, top bar, navigation drawer and the grocery list. They need a running emulator or device:
  ```bash
  ./gradlew :app:connectedDebugAndroidTest
  ```

---

## App flow

```text
Launch → signed in?
   ├─ no  → Welcome → Log in / Sign up
   └─ yes → in a household?
             ├─ no  → Join (invite code or QR scan) / Create household
             └─ yes → Home (dashboard)
                       ├─ Balance / expense summary cards → Ledger
                       ├─ Chores card → Chores · Grocery card → Grocery
                       └─ Menu → Profile (→ Vacation mode) · Household details
Bottom tabs: Home · Ledger · Chores · Grocery
Ledger  → + Add expense · tap expense to Edit · balance card → Settle up
Chores  → + Add chore · tap chore to Edit · hand-over offers
Grocery → Scan barcode · Show bought items · Add to expense → Enter prices → Add expense (prefilled)
Notification tap → Ledger
```

## Architecture
- **UI:** Jetpack Compose screens (`view/`) - only know their ViewModel
- **State:** ViewModels (`viewmodel/`) exposing `StateFlow`, created through factories
- **Data:** repositories (`actions/`) - Firestore with live listeners, Room cache and drafts; API clients for Open Food Facts and USDA (`data/api/`)
- **Domain:** pure Kotlin models and logic (`domain/`) - splits, balances, validation, chore assignment, vacation rules, API parsing (unit tested)
- **Models:** Firestore models (`data/model/`), Room entities/DAOs/database (`data/local/`)
- **Notifications:** Cloud Functions (`functions/`) send pushes through FCM; `HavenMessagingService` handles them on the device (`notifications/`)
- **Wiring:** `HavenApplication` + `AppContainer` (manual dependency injection) build the data layer once

---

## Known limitations
- Anyone with the invite code or QR code can join a household; there is no approval step yet
- No member removal or account deletion yet
- No password recovery
- Rules like "only the buyer can uncheck an item" and locked expenses are enforced in the app, not by Firestore security rules
- The grocery list shows its empty state while it's first loading
- Turning notifications off in Android doesn't remove the push token, so the server still sends to that phone (Android drops the notification)

## To Do (before the final, Dec 8)
- [ ] Push notifications for chore hand-overs (Nandana)
- [ ] Chores summary (Nandana)
- [ ] UI layout refinement (Nandana)
- [ ] Let new members choose which existing expenses they're part of when joining; recurring expenses (Chris)
- [ ] Account deletion and removing a member (Chris)
- [ ] Fake repositories and ViewModel tests (Chris + Nandana)

---

## Team & work split
| Member | Main contributions                                                                                                                                                                                                                                                                                               |
|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Chris** | Firebase/Firestore & Room data model; expenses (logic + screens); app container; profile screen and photo upload; groceries tab; connecting groceries to expenses; expense summary card; push notifications and Cloud Functions; CameraX + ML Kit setup, barcode scanning (Open Food Facts) and suggestions (USDA) |
| **Nandana** | Login/sign-up logic; initial screens and household logic; dashboard; chores tab; assignment, rotation and overdue logic; vacation mode and chore hand-overs; QR code invite scanning; household and profile redesign; dashboard UI tests; project folder structure                                      |

### Branches
| Branch          | What it contains                                                                                                        |
|-----------------|-------------------------------------------------------------------------------------------------------------------------|
| `main`          | Integrated, working app                                                                                                 |
| `chris_work`    | Expense feature: models, logic, tests, repository, Ledger, add/edit expense, settle up; early Ledger UI                 |
| `nanda-branch`  | Login/sign-up fixes and initial screens                                                                                 |
| `room`          | Room persistence (expenses, settlements), app container, expense drafts, offline write fix                              |
| `profile`       | Profile screen + photo upload, Ledger UI refresh, locked expenses                                                       |
| `dashboard`     | Dashboard screen, balance summary, recent activity overview                                                             |
| `chores`        | Chore creation, assignments, recurring schedules, automatic rotation, completion tracking, vacation mode and hand-overs |
| `grocery-list`  | Shared household grocery list, adding items, checking off purchased items, bought history, add to expense               |
| `notifications` | Push notifications: Cloud Functions, FCM tokens, notification channel, Profile settings                                 |
| `open-food-api` | Barcode scanning with Open Food Facts, USDA suggestions                                                                 |
| `qr-code`       | QR invite scanning, navigation bar, profile and household redesign, dashboard cards                                     |
| `testing`       | Compose UI tests for the dashboard                                                                                      |

---

## Use of generative AI

Generative AI tools (Claude) were used as assistants during development of this project. All AI-generated output was reviewed, tested, and edited by the team before being included, and we take full responsibility for the final code and documentation.

**Where AI was used**
- Development assistance on new topics (eg., Room, MVVM flow, CameraX, Cloud Functions, etc)
- Brainstorming solutions (eg., introduction of Manual DI) and logic
- Unit test development for testing logic in the domain files
- Removal of any redundant code
- Debugging and explaining errors

**Verification**
- Every AI suggestion was read, understood, and modified before being committed. Code was compiled, run, and tested, and changes went through our team's normal review process. We did not merge code we couldn't explain.
