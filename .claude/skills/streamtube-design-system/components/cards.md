# Component — Cards

**Live reference:** `docs/design-system/components/cards.html` · **CSS:** `styles.css` `/* — cards — */`

## Modernist spec

`.card`: a **surface-filled** column (`--color-surface`), `gap: --space-2`, padding `--space-3`,
`border-radius: 0`. Flat by default — elevation is opt-in via a separate class.

| Part | Style |
|---|---|
| `.card-kicker` | 10px, uppercase, `letter-spacing: 0.1em`, **accent** color |
| `.card-title` | heading font 800, 17px, `line-height: 1.2` |
| `.card-body` | 13px, `opacity: 0.8`, `flex: 1` (pushes meta to the bottom) |
| `.card-meta` | 11px, 50%-alpha ink, inline row with `gap: 6px` (icon + text) |

Elevation utilities (opt-in, tuned to the ground — see `foundations/spacing-elevation.md`):
`.elev-sm` → `--shadow-sm`, `.elev-md` → `--shadow-md`, `.elev-lg` → `--shadow-lg`.

The kicker is the **one** small place the accent appears inside a card; the rest is ink on surface.
Everything stays flush left.

## shadcn/cva translation

Build `components/ui/card.tsx` with the standard shadcn slots, retuned to Modernist:

```
Card:        flex flex-col gap-2 p-3 bg-surface rounded-none   // + optional elevation
CardKicker:  text-[10px] uppercase tracking-[0.1em] text-accent   // Modernist-specific slot
CardTitle:   font-heading font-extrabold text-[17px] leading-tight
CardBody:    text-[13px] opacity-80 flex-1
CardMeta:    flex items-center gap-1.5 text-[11px] text-foreground/50
```

Elevation as a variant, **off by default** (`elevation: none | sm | md | lg` →
`shadow-none | shadow-sm | shadow-md | shadow-lg`).

- Strip the shadcn default `rounded-xl border shadow` — Modernist cards are `rounded-none`, filled
  (not bordered), and flat unless `elevation` is set.
- `CardKicker` has no shadcn equivalent — add it as a named slot; it's how the accent enters a card.
- The `flex-1` on the body is load-bearing when cards sit in an equal-height grid row.
