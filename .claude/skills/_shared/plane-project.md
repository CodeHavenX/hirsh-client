# Plane project reference — HCEM

Static IDs for the `hirsh-client` build-out project (see the "HISS — UI & Navigation
Build Plan" tickets), used by `plan-ticket`, `implement-ticket`, `verify-ticket`,
`review-changes`, and `create-pr` so they don't re-discover them via search every time.

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

Most tickets get **Client**. **Backend** now also sees use: HISS-671 (client-side
dev-server change that requires backend-side CORS coordination) carries both
labels, and HISS-672 (production deployment discovery, not part of any Fase
module) carries **Backend** alone.

## Modules (phases)

| Module | id |
|---|---|
| Fase 0 · Fundación | `710ab855-f2b3-41e1-a025-e459b05eeec7` |
| Fase 1 · Directorio de pacientes | `00d46caa-f89d-47d0-9cc7-9e630832a275` |
| Fase 2 · Hospitalización y encuentros clínicos | `e976afb6-d54d-4880-8d37-7fb26bb2dc10` |
| Fase 3 · Administración | `14660302-f6e5-40d4-9b15-40b54f11d2b5` |
| Fase 4 · Pulido y siguientes pasos | `83980820-5920-44b2-b23e-49ef5f992814` |
| Fase 5 · Integración con el backend real | `f5f82d4b-e907-499f-b608-e57bce172b88` |

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
| HISS-601 | `eea71e2d-c4b9-4279-81a2-72e3a5279042` |
| HISS-602 | `5dd80320-1ea4-411a-bb59-e3eabc1580c5` |
| HISS-603 | `e9ef0e9e-1b3b-44e4-ac6e-9c224591922c` |
| HISS-604 | `ef0def6c-4efe-4dd2-9bb9-47e43bdee1f6` |
| HISS-611 | `7890fbeb-eb9e-4263-b8b7-74c375a118f3` |
| HISS-612 | `4c18b0a7-fb58-432c-b81e-cec0b493a874` |
| HISS-613 | `afc5a67c-9801-40cd-8ce2-a731a4dcb3d2` |
| HISS-621 | `cae69183-d832-4b4c-b6f3-49fdb8ee7628` |
| HISS-622 | `3517e728-f85e-4f8f-b5c3-36797bec49f0` |
| HISS-623 | `847401fa-9cad-4431-9c0d-2023d9705c71` |
| HISS-624 | `8c61924d-faac-482c-8bdb-f44e3a5bc1a7` |
| HISS-631 | `d0e6c330-a4c8-4bab-a23d-a96266dae532` |
| HISS-632 | `4a8321ca-c044-4bf1-ab46-39e9cb4abd50` |
| HISS-633 | `49b15932-72ec-482a-bbd4-9c8c43e95909` |
| HISS-634 | `435607ae-a268-4144-8c7f-71b33482d598` |
| HISS-641 | `52bd46dd-2c37-4e77-8523-fd0cb9c1c120` |
| HISS-651 | `237e8624-19a6-4077-b0a3-568ddb3a26b6` |
| HISS-652 | `42239401-dc0c-465c-87cf-bb8269735b50` |
| HISS-653 | `11d4096c-b865-4f32-9a21-0a398692b00a` |
| HISS-661 | `c3e6db87-70ce-431b-93a4-76a1bdc524b8` |
| HISS-662 | `022cdba8-96be-43d8-8c76-ad76dbc2b2bf` |
| HISS-663 | `d0e3979d-8746-49d3-a6ff-b52c6ac9abee` |
| HISS-664 | `1c493634-d987-4e84-8428-a20fd5dab329` |
| HISS-671 | `723b6b44-8a2b-416b-914a-402d6a3d3877` |
| HISS-672 | `42d175dc-956b-4d84-8f35-3fc316a8b566` |

`sort_order` on each ticket encodes the recommended global build sequence
(topological order over `Depends on`, phase by phase — see `plan-ticket` Step 1 for
how to find "what's next"). HISS-101 and HISS-105 are already Done as of this
writing (the `Hospitalizacion`/`HistoriaClinica`/`Evolucion` domain models and the
full 6-patient seed set both landed on `main`).

Fase 5 (HISS-601–671) wires the client to the real HIRSH backend, replacing the
in-memory fakes; HISS-672 is a standalone discovery ticket (Backlog, no module)
for unscoped production-deployment work and isn't part of Fase 5's build sequence.
Its `Depends on` sections reference each other by HISS id, same convention as
Fase 0–4.
