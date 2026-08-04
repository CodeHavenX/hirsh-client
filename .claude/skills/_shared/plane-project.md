# Plane project reference — HCEM

Static IDs for the `hirsh-client` build-out project (see the "HISS — UI & Navigation
Build Plan" tickets), used by `plan-ticket`, `implement-ticket`, `verify-ticket`,
`review`, and `create-pr` so they don't re-discover them via search every time.

IDs are permanent once created. **Always re-fetch a ticket's current
state/labels/description via `mcp__plane__retrieve_work_item` before trusting
anything about it except the id/external_id mapping below** — content and status
change after this file was written; the mapping does not.

- **Workspace:** `3997226d-ee16-41bc-926e-1d51607ff9af`
- **Project** ("HCE Psiquiatría Modular", identifier `HCEM`): `69616f0e-7541-46e7-94dd-3a261ab0bd05`

## Labels

| Label | id |
|---|---|
| Client | `3dcb4996-1a62-4892-a92c-a62b0fba0bab` |
| Backend | `e98aec78-a220-45f2-916a-bbec8ba2a30e` |

Every ticket in this repo's scope gets **Client**. **Backend** exists for when the
separate backend-service repo starts tracking its own tickets in the same project —
nothing here uses it yet.

## Modules (phases)

| Module | id |
|---|---|
| Fase 0 · Fundación | `710ab855-f2b3-41e1-a025-e459b05eeec7` |
| Fase 1 · Directorio de pacientes | `00d46caa-f89d-47d0-9cc7-9e630832a275` |
| Fase 2 · Hospitalización y encuentros clínicos | `e976afb6-d54d-4880-8d37-7fb26bb2dc10` |
| Fase 3 · Administración | `14660302-f6e5-40d4-9b15-40b54f11d2b5` |
| Fase 4 · Pulido y siguientes pasos | `83980820-5920-44b2-b23e-49ef5f992814` |

## States

| State | id | group |
|---|---|---|
| Backlog | `95e9398e-c114-41c8-942a-80a3624aa53d` | backlog |
| Todo | `52793115-adc8-4856-962b-c0478ffd9956` | unstarted |
| In Progress | `725d0ade-69d7-4ef0-861a-060b76a83298` | started |
| QA / Code Review | `452c5584-3af6-40e1-a430-9f303ebd3094` | started |
| Done | `1e32cdb1-1595-471b-8aab-244ae32308ec` | completed |
| Cancelled | `2345102f-8027-4d97-8b8a-991d7bf3b0d6` | cancelled |

**Pipeline convention these skills follow:**
`plan-ticket` moves a ticket **Backlog → Todo**. `implement-ticket` moves it **Todo
→ In Progress** when work actually starts. `verify-ticket` passing moves it **In
Progress → QA / Code Review** (verification done, ready for a review pass /
PR). **QA / Code Review → Done is a human call, made after the PR actually
merges** — this agent doesn't control merge and shouldn't guess that it happened,
so no skill sets Done automatically; `create-pr`'s handoff reminds the user to do
it once merged.

## Ticket id → work item UUID

Every ticket carries `external_id: "HISS-NNN"` / `external_source: "hiss-ui-plan"`
(set at creation), in addition to Plane's own `HCEM-<sequence_id>` identifier that
appears in the Plane UI. Look up by this table first — it's exact and avoids a
search round-trip. If a HISS-id isn't listed here (a ticket added after this table
was written), fall back to `mcp__plane__search_work_items` (query the HISS-id
string) or `mcp__plane__list_work_items` filtered/grepped for that `external_id` —
then **add the discovered mapping to this table** so the cache stays current.

| HISS id | work item id |
|---|---|
| HISS-101 | `a105c90a-f039-4978-a38b-9b768fc96f97` |
| HISS-102 | `481b9b58-6ca6-4bee-80b2-b843fbae413b` |
| HISS-103 | `89a659c9-2403-4bd4-be5e-d2326f44fb7c` |
| HISS-104 | `9bb77aa4-8f74-4c5b-9e52-672ef70ba6fb` |
| HISS-105 | `210c43dc-7abc-43d5-865f-da1abb317cfc` |
| HISS-106 | `8a5adcfb-cbcf-4ed9-b797-bf0d8f45465d` |
| HISS-107 | `65a6cf97-2419-4aa3-9458-f064cd14d9eb` |
| HISS-108 | `8b606b42-e44e-444e-93d5-d2e0f87e98e0` |
| HISS-109 | `7de1679e-cd85-4fdd-b235-2b3801fc9b3f` |
| HISS-110 | `a63a8e4f-4fd7-47be-88a1-9ce34865fa1e` |
| HISS-111 | `f238f011-6189-4c30-b6cb-f69e33315eef` |
| HISS-112 | `257679c0-8b5b-4242-9ee3-b9ff564c6198` |
| HISS-201 | `4eabf62d-f468-48f0-aa05-8f3ac1ff094c` |
| HISS-202 | `9c8a915d-c99d-4fc9-b5b4-26f77c476639` |
| HISS-203 | `4566fcc8-7693-4669-93e7-285f7243b741` |
| HISS-204 | `e402846f-a6a1-41da-b2a0-663b3ccbf24c` |
| HISS-205 | `aae6037a-99f7-4e9d-abd5-b56f463b22fe` |
| HISS-206 | `488a5f86-3613-42c0-bb94-faa7775c26b7` |
| HISS-301 | `06bcaf27-f019-4340-b283-ddbdb90dff2e` |
| HISS-302 | `2002b3a8-127f-4034-9680-c4437bd2c200` |
| HISS-303 | `180d2e8a-b9b2-4c45-8ac6-df26a679147e` |
| HISS-304 | `430d2e59-a263-4725-b17b-46f96013b817` |
| HISS-321 | `4aa6ccac-f1d3-45a7-b809-3c45d1680bac` |
| HISS-322 | `c4574fe9-d807-4413-bc48-661dcfeaee86` |
| HISS-401 | `1dfdbf94-ab07-42cf-af55-52c63544a784` |
| HISS-501 | `99124680-c9e4-4019-bef8-e53bafff2ad5` |
| HISS-502 | `6516a760-e318-491f-bcec-c586ec0d9e68` |
| HISS-503 | `7218897c-e6b4-485c-a228-effbdcfa8b4b` |

`sort_order` on each ticket encodes the recommended global build sequence
(topological order over `Depends on`, phase by phase — see `plan-ticket` Step 1 for
how to find "what's next"). HISS-101 and HISS-105 are already Done as of this
writing (the `Hospitalizacion`/`HistoriaClinica`/`Evolucion` domain models and the
full 6-patient seed set both landed on `main`).
