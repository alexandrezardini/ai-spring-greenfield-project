# Component — Table

**Live reference:** `docs/design-system/components/table.html` · **CSS:** `styles.css` `/* — tables — */`

## Modernist spec

`.table`: full width, `border-collapse: collapse`, 14px. Rules — not zebra fills — organize the data.

| Part | Style |
|---|---|
| `th` | flush-left, 11px, uppercase, `letter-spacing: 0.08em`, 60%-alpha ink, padding `--space-2`, **2px** divider bottom border |
| `td` | padding `--space-2`, **1px** divider bottom border |
| `tbody tr:hover` | `ink 4%` tint (subtle row hover) |

So the header is a **2px** rule and each row a **1px** rule — the same two-weight rule system as the
rest of Modernist. Status cells reuse the **tags** (`components/buttons.md`) rather than inventing
colored cells.

## shadcn/cva translation

Build `components/ui/table.tsx` (Table/Header/Row/Head/Cell slots) retuned to Modernist:

```
table:  w-full border-collapse text-sm
th:     text-left text-[11px] uppercase tracking-[0.08em] text-foreground/60 p-2 border-b-2 border-divider
td:     p-2 border-b border-divider
row:    hover:bg-foreground/4
```

- Two border weights are load-bearing: **`border-b-2`** under the header, **`border-b`** on rows.
- No zebra striping, no rounded container, no card wrapper — just ruled rows on the ground.
- For status/state cells, render the **Badge/tag** variants, not bespoke colored text.
