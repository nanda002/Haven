# Haven

Roommates sharing a home often struggle to manage shared expenses and household chores fairly. Bills get split inconsistently, payments are forgotten, and chores fall unevenly on one or two people. Because these issues are usually tracked informally, through group chats or scattered payment apps, there’s no single, transparent record of who owes what or who's done what. This lack of visibility leads to resentment, awkward confrontations, and strained relationships between roommates.

We propose ‘Haven’, a shared household management app that centralizes logging and tracking of shared expenses, chores for all occupants in the same household, and a shared grocery list, along with widgets personalized for the user.

**Stack:** Kotlin · Jetpack Compose · MVVM (ViewModel + StateFlow) · Room · Firebase Authentication, Cloud Firestore and Cloud Storage · Coil

---

## Features

### Implemented
**Account & household**
- Sign up/log in with email and password (Firebase Auth); returning users go straight to their household
- Create a household or join one with a 6-character invite code
- Household details: name, invite code (with regenerate), member count
- Members popup: current members and collapsible past members
- Leave household: only allowed once all balances are settled
- Profile screen: name, email, join date, household, settings/privacy sections, log out
- Profile picture upload (Android photo picker -> Firebase Storage -> shown with Coil)

**Expenses**
- Dashboard balance card ("You owe/You're owed") that opens the Ledger
- Ledger: balance card, category filter (Rent, Utilities, Groceries, Other)
- Add expense: description, amount, category, who paid, even split or custom split
- Edit and delete expenses (with confirmation)
- Expenses involving a member who left the household are locked (can't be edited or deleted)
- Unsaved add expense forms are kept as drafts and restored next time using Room
- Settle up: balance with each roommate, full or partial payments; all balances update live

**Data & offline**
- Firestore is the shared source of truth between roommates; screens read from a local Room database that
  Firestore keeps up to date, so the Ledger and balances work offline
- Writes made offline are queued and synced automatically when back online
- Local data (Room) is cleared on logout

### To do (before Update 2)
- [ ] Chores tab (rotation logic, assign, mark done, due dates)
- [ ] Groceries tab (shared list, check off)
- [ ] Third-party API - planned: barcode scanning with the Open Food Facts API for groceries
- [ ] Device capability - planned: camera (barcode / QR invite scanning)
- [ ] Extras if time allows: notifications, settlement history/undo, receipt scanning, chore calendar

---

## App flow
Launch → signed in?
   ├─ no  → Log in / Sign up
   └─ yes → in a household?
             ├─ no  → Join (invite code) / Create household
             └─ yes → Home (dashboard)
                       ├─ Balance card → Ledger
                       └─ Menu → Profile · Household details
Bottom tabs: Home · Ledger · Chores (coming soon) · Pantry (coming soon)
Ledger → + Add expense · tap expense to Edit · balance card → Balances (Settle up)

## Architecture
- **UI:** Jetpack Compose screens (`view/`) - only know their ViewModel
- **State:** ViewModels (`viewmodel/`) exposing `StateFlow`, created through factories
- **Data:** repositories (`actions/`) - write to Firestore, read from Room, keep Room in sync
- **Domain:** pure Kotlin models and logic (`domain/`) - splits, balances, validation (unit tested)
- **Models:** Firestore models (`data/model/`), Room entities/DAOs/database (`data/local/`)
- **Wiring:** `HavenApplication` + `AppContainer` (manual dependency injection) build the data layer once

---

## Team & work split
| Member | Main contributions |
|---|---|
| **Chris** | Firebase/Firestore & Room data model; expenses (logic + screens); app container; profile screen and photo upload |
| **Nandana** | Login and sign-up screens; initial screens; dashboard; household details; leave household logic; project folder structure |

### Branches
| Branch | What it contains |
|---|---|
| `main` | Integrated, working app |
| `chris_work` | Expense feature: models, logic, tests, repository, Ledger, add/edit expense, settle up; early Ledger UI |
| `nanda-branch` | Login/sign-up fixes and initial screens |
| `room` | Room persistence (expenses, settlements), app container, expense drafts, offline write fix |
| `profile` | Profile screen + photo upload, Ledger UI refresh, locked expenses |
| Module | Description |
| `dashboard` | Dashboard screen, balance summary, recent activity overview |
| `chores` | Chore list, assignments, recurring chores, completion tracking |
---

## Use of generative AI

Generative AI tools were used as assistants during development of this project. All AI-generated output was reviewed, tested, and edited by the team before being included, and we take full responsibility for the final code and documentation.

**Where AI was used**
- Development of new topics (Room, MVVM flow, etc)
- Brainstorming solutions (Introduction of Manual DI) and logic
- Unit test development for testing logic in the domain files
- Removal of any redundant code.
- Debugging and explaining errors

**Verification**
Every AI suggestion was read, understood, and modified before being committed. Code was compiled, run, and tested, and changes went through the team's normal review process. We did not merge code we couldn't explain
---
