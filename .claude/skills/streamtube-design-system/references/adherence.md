# Reference — Design-System Adherence

The machine-checkable half of Modernist. Design intent (the `foundations/` + `components/` pages)
tells you *what* to build; this page tells you *how to prove* you actually consumed the system
instead of hardcoding values.

## Enforceable rules

These are the readme's hard rules, restated as things a linter (and a reviewer) can catch:

1. **No raw hex colors.** Every color comes from a `--color-*` token via `var()` (or a Tailwind
   utility that resolves to one). A literal like `#ec3013` is a bug even when it's "the right red" —
   it drifts the moment `theme.json` is retuned.
2. **No raw `px` values.** Spacing, sizing, and radius come from `--space-*` / `--radius-*`. A literal
   `16px` should be `var(--space-4)` / the matching Tailwind step. (Genuinely intrinsic 1px/2px rule
   borders are the documented exception — they *are* the rule system.)
3. **No font besides Archivo.** `--font-heading` / `--font-body` only. Any other `font-family` is
   off-system.
4. **Ramp steps over ad-hoc `color-mix()`.** Reach for a `--color-*-{100..900}` step before mixing
   your own tint (`foundations/color.md`).
5. **Zero radius.** `rounded-none` everywhere — `--radius-*` are all 0 (`foundations/spacing-elevation.md`).
6. **Themed focus, never default blue.** `:focus-visible { outline: 2px solid var(--color-accent);
   outline-offset: 2px }` on interactive elements (inputs use a border-color focus — see
   `components/forms.md`). Never ship the browser's blue ring.
7. **Flush-left labels.** Button labels in wide/`block` buttons start at the left padding edge
   (`components/buttons.md`); only dialog action rows are right-aligned (`components/dialog.md`).
8. **Accent body text uses `--color-accent-700`.** The base accent-to-ground pair is only ~3:1 —
   fine for icons/large text/chrome, not body copy (`foundations/color.md`).

## The oxlint config

`docs/design-system/_adherence.oxlintrc.json` is the shipped enforcement source. It already encodes
rules 1–3 as `no-restricted-syntax` selectors:

- raw hex literal `#[0-9a-fA-F]{3,8}` → *"use a design-system color token via var()."*
- raw `\d+px` literal → *"use a design-system spacing token via var()."*
- any `font-family` not `Archivo` → *"Font not provided by the design system."*

Its `x-omelette` block carries the **authoritative token list** (every `--color-*`, `--space-*`,
`--radius-*`, `--shadow-*`, `--font-*` name) and their kinds — a machine-readable allowlist you can
diff against, and a fallback source for token names alongside `_ds_manifest.json`.

## Wiring it into `nextjs-project/` (when initialized)

- The frontend uses **oxlint** (per the staged `.claude/rules/next-frontend-code-quality.md`) — fold
  these `no-restricted-syntax` rules into the project `.oxlintrc.json` so hardcoded hex/px/fonts fail
  lint, not review.
- The rules currently `warn`; decide whether to promote to `error` in CI once the token layer
  (`globals.css` + Tailwind theme) exists, so there's a sanctioned token for every value the rules
  would otherwise flag.
- These lint rules are a **backstop**, not the design spec — passing lint proves you didn't hardcode,
  not that you matched the component's intended look. Both matter.
