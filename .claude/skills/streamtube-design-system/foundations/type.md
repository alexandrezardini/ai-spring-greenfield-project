# Foundation — Typography

**Live reference:** `docs/design-system/foundations/type.html` · **Values:** `styles.css` `:root` + base type

## Family

**Archivo over Archivo** — one family for both heading and body, bound to `--font-heading` /
`--font-body`. Heading weight is `--font-heading-weight` (`800`); body is `400`. Load Archivo via
`next/font` and bind it to the CSS variables — never name the font in a class.

## Scale (from `styles.css`, read there for the source of truth)

| Element | Size | Notes |
|---|---|---|
| `h1` | 42px | `line-height: 1.12`, `letter-spacing: -0.015em` |
| `h2` | 32px | " |
| `h3` | 25px | " |
| `h4` | 20px | " |
| `h5` | 16px | |
| `h6` | 13px | **uppercase**, `letter-spacing: 0.08em` |
| body | 15px | `line-height: 1.55`, weight 400 |

The scale is **fixed**. Density (1.00×) and radius (0px) are baked into `--space-*` / `--radius-*`
— density moves **spacing**, not type sizes. Use the variables, not raw numbers.

## Flush left

All type is flush left — headings, copy, and labels. Never center hero copy or button labels.

## Helpers

- `.text-muted` → `color-mix(in srgb, var(--color-text) 55%, transparent)` for secondary text.
- Links: `color: var(--color-accent)`, `text-underline-offset: 3px`. For body-size accent text use
  `--color-accent-700` (contrast — see `color.md`).

## Tailwind/shadcn translation

- Register Archivo once via `next/font`, expose it as `--font-heading`/`--font-body`, and set the
  Tailwind `fontFamily` to read those vars.
- Encode the scale as heading component styles or a typography config keyed to the sizes above —
  don't sprinkle arbitrary `text-[42px]` values.
- Headings default to weight 800; keep `tracking` tight (`-0.015em`) on `h1–h4` and the uppercase
  `0.08em` treatment on `h6`.
