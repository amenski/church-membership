# MoreView

`frontend/src/views/MoreView.vue`

The screen behind the "More" tab of the phone tab bar: the pages and account actions the bar has no room for. Route `/more`, minimum role VOLUNTEER (`requiresAuth`, `requiresRole: 'VOLUNTEER'`, title "More", `frontend/src/router/index.js`). No endpoints, no store of its own; it reads `useAuthStore()` only.

## What the user sees
- Page head "More", then one card of rows, each at least 60px with an icon, a 16px name, a 13px one-line subtitle and a chevron: **Households** ("Families and shared addresses", `/households`), **Activity** ("Administrators only", `/activity`, ADMIN only: other roles do not get the row) and **Profile** ("Your details and password", `/profile`).
- Below it a card with the signed-in name (first and last name, else the email), the email, and a full-width "Sign out" button (48px, clay outline). Sign out calls `authStore.logout()`, shows the toast "You have been successfully signed out" and goes to `/login`; a failure shows the toast "Logout Failed".

## Phone only
The bottom tab bar (`components/BottomTabs.vue`, see [../design.md](../design.md#layout)) exists below `lg` only, so this screen does too. On a desktop width `MoreView` redirects to `/dashboard` as soon as it is created, and again if the window grows past `lg` while it is open (`matchMedia('(min-width: 62rem)')`). The tab bar lights More on `/more` and on the pages it opens.

## Roles
| Role | Tab bar | Rows on More |
|------|---------|--------------|
| VOLUNTEER, STAFF | the five tabs | Households, Profile |
| ADMIN | the five tabs | Households, Activity, Profile |
| MEMBER | none (the top bar has the name and Sign out) | the route is refused and goes home |

## Gotchas
- The mockup's dark "More" header is not built: the shell's top bar (brand) stays and the page head carries the title.
- The sign-out logic is a copy of `App.vue`'s `handleLogout` (the rail has its own button).
