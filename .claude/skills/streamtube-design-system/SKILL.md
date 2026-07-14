---
name: streamtube-design-system
description: >
  StreamTube's "Modernist" design system, translated for the Next.js + Tailwind +
  shadcn/ui frontend. Reference this skill on demand whenever you plan, implement,
  or review a frontend screen or feature and need design information: how a button,
  card, form, nav, table, or dialog should look and behave; which color / spacing /
  type / radius / shadow token to use; how to port Modernist tokens into a Tailwind
  theme; how to build a shadcn primitive's cva variants to match the system; or how
  to verify design adherence. Triggers on: building/styling a StreamTube UI, "how
  should this button/card/form/dialog look", design tokens, color ramps, typography
  scale, spacing, Lucide icons, grayscale imagery, shadcn/cva variants for
  StreamTube, Modernist design system, design-system adherence.
---

# StreamTube Design System — "Modernist"

The frontend consumes a design system called **Modernist**. This skill translates it
into the project's stack (**Next.js + Tailwind + shadcn/ui**). It does **not** own
screens/flows (that's `screen-inventory`) or tests (that's `testing-guide-next-frontend`).

> **Stack note:** `nextjs-project/` is not initialized yet. When it is, the token-porting
> rule below is the first thing to wire up (`globals.css` + Tailwind theme), before any
> component is built.

## 1. What Modernist is (the non-negotiable feel)

Flat, architectural, set entirely in **Archivo**: a near-mono **red on light ground**,
a visible modular grid, **zero corner radius**, and **strong 2px rules**. Nothing floats,
nothing is decorated — alignment and the strength of the dividers do the organizing.
Labels sit **flush left** (even inside buttons). Photography prints in **pure black and white**.

**The five rules that define the look:**

1. **Mono red.** One accent — `--color-accent` `#ec3013`. There is no real second accent;
   `--color-accent-2-*` is a machine-derived stand-in kept only so both sets resolve. Treat
   accent and accent-2 as **one role**. Use the accent **sparingly**: the primary action and
   small emphasis. The system is mostly ink on ground.
2. **Zero radius.** `--radius-sm/md/lg` are all `0px` **on purpose**. Never round a corner.
3. **2px rules do the work.** Strong `--color-divider` rules between major sections and under
   the nav/table header. Never soften rules into hairlines or drop them for whitespace.
4. **Flush left.** Headings, copy, and the labels inside wide buttons all start at the left
   edge. Never center button labels or hero copy.
5. **Grayscale imagery.** Every content photograph goes through a grayscale wrapper — pure
   B&W. Never tint or colorize imagery.

## 2. Source of truth — read values, don't trust copies

This skill carries the **durable rules and semantics** inline. It deliberately does **not**
re-copy the concrete token values, because `styles.css` was machine-derived from `theme.json`
and declares itself the source of truth ("retune it here") — a copied hex would silently lie
after any retune.

**When you need an exact value, read it from:**

- `docs/design-system/styles.css` — the `:root` token sheet + the component CSS layer (canonical).
- `docs/design-system/_ds_manifest.json` — every token name/value/kind, machine-readable.
- `docs/design-system/readme.md` — the written design guide.
- `docs/design-system/foundations/*.html`, `components/*.html` — live rendered references
  (view source to see intended markup).
- `docs/design-system/templates/landing/` — the canonical "DS consumed correctly" example.

The token families you will reference: `--color-*` (roles + 100–900 ramps), `--font-*`,
`--space-*`, `--radius-*`, `--shadow-*`.

## 3. The bridge — how Modernist becomes Tailwind + shadcn

Modernist **ships as plain CSS** (`styles.css`: CSS variables + hand-authored classes like
`.btn`, `.card`, `.field`). This app is **Tailwind + shadcn/ui** (utility classes + `cva`
variants inside `components/ui/*`). **Do not** import `styles.css` and use raw `.btn`/`.card`
classes — that bypasses shadcn. Instead, treat this skill as a **translation layer**:

**Token-porting rule (do this once, at frontend bootstrap):**

1. Copy the `:root` token block from `styles.css` into the app's `globals.css` as CSS custom
   properties — **keep the variable names** (`--color-accent`, `--space-4`, `--radius-md`, …).
   These variables are the single runtime source; Tailwind reads them, it does not replace them.
2. Map them into the Tailwind theme so utilities resolve to the tokens
   (e.g. a `bg-accent` utility → `var(--color-accent)`, spacing scale → `var(--space-*)`,
   `borderRadius` → `var(--radius-*)`, `boxShadow` → `var(--shadow-*)`). Wire the **whole**
   100–900 ramps, not just the base steps.
3. Load **Archivo** via `next/font` and bind it to `--font-heading` / `--font-body`.
4. Never hard-code a hex, a font name, or a px value a token already carries (see
   `references/adherence.md`).

**Per-component rule:** for each shadcn primitive, author its `cva` variants to match the
Modernist component spec in `components/*.md` — same fills, same states, `rounded-none`,
flush-left labels, themed hover/pressed/focus. The component pages give you the exact
CSS-derived behavior to reproduce.

## 4. Interaction states (themed, never browser defaults)

Every interactive element gets a themed `:hover` tint and a pressed state from the accent
ramp — **one step past the base**: `--color-accent-600` pressed-ish/hover on a light ground,
`--color-accent-700` for the deepest press, or a `color-mix()` tint for outlined/ghost
variants. Keyboard focus is **always**:

```css
:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px; }
```

Never leave the default blue focus ring. `::selection` is an accent tint. Disabled controls
drop to **45% opacity**. Prefer **ramp steps** over ad-hoc `color-mix()`; prefer
`--shadow-sm/md/lg` over ad-hoc box-shadows.

## 5. Theming status

The app is **light-ground only today** (`themes: []` in `_ds_manifest.json` — no dark theme
authored). But `readme.md` carries dark-ground conventions (e.g. accent hover flips to
`--color-accent-400` on a dark ground). **Keep specs dark-aware**: drive everything from
tokens and ramp steps so a future dark theme is a token swap, not a component rewrite.

## 6. Component & foundation index

Read the relevant page on demand — each carries the Modernist spec, its shadcn/`cva`
translation, and the exact tokens.

### Foundations
| Topic | File |
|---|---|
| Color roles + 100–900 tonal ramps, ramp-step usage, accent-on-body | `foundations/color.md` |
| Typography — Archivo scale, weights, flush-left | `foundations/type.md` |
| Spacing scale, 0 radius, elevation shadows | `foundations/spacing-elevation.md` |
| Lucide icons at interface sizes | `foundations/icons.md` |
| Grayscale imagery treatment | `foundations/imagery.md` |

### Components
| Component | Modernist classes | File |
|---|---|---|
| Buttons + tags | `.btn` (`-primary/-secondary/-ghost/-icon/-block`), `.tag` | `components/buttons.md` |
| Cards | `.card` (`-kicker/-title/-body/-meta`), `.elev-*` | `components/cards.md` |
| Forms | `.field`, `.input`, `.radio`+`.dot`, `.seg`+`.seg-opt` | `components/forms.md` |
| Navigation | `.nav`, `.nav-brand` | `components/navigation.md` |
| Table | `.table` | `components/table.md` |
| Dialog | `.dialog-backdrop`, `.dialog` (`-title/-body/-actions`) | `components/dialog.md` |

### References
| Topic | File |
|---|---|
| Enforceable adherence rules + oxlint config | `references/adherence.md` |

## 7. Do / Don't (system-wide)

**Do**
- Let the grid show: equal-width cells, strong 2px rules between sections, visible structure.
- Keep everything flush left — headings, copy, labels inside wide buttons.
- Use the accent sparingly — primary action + small emphasis; the poster-statement red field
  is reserved for hero/close banners where display type carries the page.
- Print photographs in B&W through the grayscale wrapper.
- For paragraph-size text in the accent, use a **deep ramp step** (`--color-accent-700` on the
  light ground) — the base accent-to-ground pair is only ~3:1 (fine for icons/large text/chrome,
  not for body copy).

**Don't**
- Round a corner anywhere.
- Center button labels or hero copy.
- Soften rules into hairlines or replace them with whitespace.
- Tint or colorize imagery.
- Hard-code a hex/font/px a token already carries.
