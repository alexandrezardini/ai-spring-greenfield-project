# Foundation — Spacing, Radius & Elevation

**Live reference:** `docs/design-system/foundations/layout.html` · **Values:** `styles.css` `:root`

## Spacing scale

A **1.00× density** scale. Read exact values from `styles.css`; the current steps:

| Token | Value |
|---|---|
| `--space-1` | 4px |
| `--space-2` | 8px |
| `--space-3` | 12px |
| `--space-4` | 16px |
| `--space-6` | 24px |
| `--space-8` | 32px |

Note the scale **skips** `-5` and `-7` — there is no `--space-5`/`--space-7`. Use the defined steps;
don't invent intermediate values or raw px.

## Radius — zero, on purpose

`--radius-sm`, `--radius-md`, `--radius-lg` are **all `0px`**. This is the identity of the system,
not an oversight. In Tailwind terms: **`rounded-none` everywhere**. Never round a corner.

## Rules / dividers

The organizing device is the rule, not the shadow. `--color-divider` (40%-alpha ink):

- **Section separators & nav/table headers:** `2px` solid (`.hr` is a `2px` rule).
- **In-table row separators:** `1px`.

Never soften a rule to a hairline or drop it for whitespace.

## Elevation

Three shadows, **tuned to the light ground** — use these, never ad-hoc box-shadows:

| Token | Use |
|---|---|
| `--shadow-sm` | Cards, subtle lift (`.elev-sm`) |
| `--shadow-md` | Raised surfaces (`.elev-md`) |
| `--shadow-lg` | Dialogs / top elevation (`.elev-lg`, `.dialog`) |

Elevation is used **sparingly** — flat is the default; most separation comes from rules, not shadow.

## Tailwind/shadcn translation

- Map the Tailwind `spacing` scale to `var(--space-*)` and **drop** the keys the system doesn't
  define, or at least prefer the named steps.
- Set `borderRadius` for `sm/md/lg` all to `var(--radius-*)` (0) — and make `rounded-none` the habit
  in every `cva` base.
- Map `boxShadow` `sm/md/lg` to `var(--shadow-sm/md/lg)`.
- Default border width for rules is `2px` between sections, `1px` inside data rows — encode as
  explicit `border-b-2` / `border-b` with the `divider` color.
