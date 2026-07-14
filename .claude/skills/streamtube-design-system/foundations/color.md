# Foundation — Color

**Live reference:** `docs/design-system/foundations/color.html` · **Values:** `styles.css` `:root`, `_ds_manifest.json`

## Roles (read exact hex from `styles.css`)

| Token | Role |
|---|---|
| `--color-bg` | Light ground — the page background |
| `--color-surface` | Slightly darker surface — cards, inputs, dialogs |
| `--color-text` | Ink — body and headings |
| `--color-accent` | The single accent (mono red) |
| `--color-accent-2` | Machine-derived stand-in — **treat as the same role as accent** |
| `--color-divider` | 40%-alpha ink — every rule and border |

## Ramps

Each role carries a **100–900 tonal ramp** (`--color-neutral-100 … -900`, `--color-accent-100 … -900`,
`--color-accent-2-100 … -900`), generated in OKLCH on **one shared perceptual lightness scale** — so
the same step of any ramp has the same visual weight.

- **100–300** — tinted fills, hovers, subtle borders.
- **500** — the role's base.
- **700–900** — text on tinted fills, pressed states, and **accent text at body size**.

**Prefer a ramp step over an ad-hoc `color-mix()`.** Only use `color-mix()` where the CSS layer
already does (ghost/secondary tints, divider alpha).

## Contrast rule (load-bearing)

The accent-to-ground pair is tuned to **≥ 3:1** — enough for icons, large text, and interface
chrome, **not** for body copy. For paragraph-size text in the accent, use `--color-accent-700`
on the light ground, never the raw accent.

## Mono-scheme rule

There is **no** second accent color. `--color-accent-2` and its ramp resolve to a near-identical
red so both token sets exist, but design-wise they are **one voice**. A `.tag-accent-2` reads the
same as `.tag-accent`. Don't introduce a second hue to "use" accent-2.

## Tailwind/shadcn translation

- Wire **every** ramp step into the Tailwind theme (`accent-100 … accent-900`, `neutral-100 …`),
  not only the base — pressed/hover/tinted states depend on the deep and light steps.
- Map roles to semantic Tailwind color keys that resolve to the CSS vars:
  `background → var(--color-bg)`, `surface/card → var(--color-surface)`,
  `foreground → var(--color-text)`, `accent/primary → var(--color-accent)`,
  `border → var(--color-divider)`.
- Keep the CSS variables as the runtime source so a future dark theme is a `:root`/`[data-theme]`
  swap, not a utility rewrite.
- `::selection` → an accent tint (`color-mix(in srgb, var(--color-accent) 30%, transparent)`).
