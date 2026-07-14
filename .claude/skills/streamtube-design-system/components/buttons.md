# Component — Buttons & Tags

**Live reference:** `docs/design-system/components/buttons.html` · **CSS:** `styles.css` `/* — buttons — */`, `/* — tags — */`
**Applied in StreamTube:** the auth screens (Entrar / Criar conta / Recuperar senha) — primary submit + ghost links.

## Buttons — Modernist spec

Base `.btn`: inline-flex, **heading font weight 800**, 14px (matches `.input` so button + field sit
side by side in sign-up rows), `border-radius: 0`, padding `--space-2` × `calc(--space-3 * 1.2)`,
icon is a **trailing** element (`gap: 6px`). Disabled → **45% opacity**, `not-allowed`.

| Variant | Fill / border | Hover | Pressed (`:active`) |
|---|---|---|---|
| `.btn-primary` | solid `--color-accent`, text `--color-bg` | `--color-accent-600` | `--color-accent-700` |
| `.btn-secondary` | 1px `--color-divider` border, transparent | `ink 7%` tint | `ink 14%` tint |
| `.btn-ghost` | accent text, no fill, tight inline padding | `accent 10%` tint | `accent 18%` tint |
| `.btn-icon` | 36×36 square, no padding — centers one icon | (per variant) | (per variant) |
| `.btn-block` | full width, `justify-content: flex-start` — **label flush left** | | |

**Flush-left rule (critical):** a button wider than its label (`.btn-block`, full-width submits)
starts the text at the left padding edge — trailing icon and all — **never centered**. Default
`.btn` centers because it hugs its label; the moment it's full-width, it's left-aligned.

Focus: the global `:focus-visible` 2px accent ring (offset 2px). Never a browser default.

## shadcn/cva translation

Build `components/ui/button.tsx` so its `cva` reproduces the table above:

```
base:     inline-flex items-center gap-1.5 font-heading font-extrabold text-sm
          rounded-none disabled:opacity-45 disabled:pointer-events-none
          focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent
variants.variant:
  primary:   bg-accent text-background      hover:bg-accent-600  active:bg-accent-700
  secondary: border border-divider          hover:bg-foreground/7 active:bg-foreground/14
  ghost:     text-accent px-1               hover:bg-accent/10    active:bg-accent/18
variants.size:
  default:   py-2 px-[calc(theme(space.3)*1.2)]
  icon:      size-9 p-0            (36×36, centers one Lucide icon)
  block:     w-full justify-start mt-2        // FLUSH-LEFT label
```

- Keep icons as **trailing** children; they inherit `currentColor` (see `foundations/icons.md`).
- `block` must set `justify-start` — this is the one place a shadcn default (`justify-center`)
  actively contradicts Modernist.
- Don't add a `rounded-md` — the shadcn default template ships rounded; strip it to `rounded-none`.

## Tags — Modernist spec

`.tag`: 11px, inline, padding `3px 10px`, `border-radius: 0` (`calc(--radius-md * 0.75)` = still 0).

| Variant | Style |
|---|---|
| `.tag-accent` | `accent-100` fill, `accent-800` text |
| `.tag-accent-2` | reads the **same** as accent (mono scheme) |
| `.tag-neutral` | `neutral-100` fill, `neutral-800` text |
| `.tag-outline` | 1px accent border, accent text |

## shadcn/cva translation (tags → a Badge primitive)

```
base:  inline-flex items-center text-[11px] px-2.5 py-0.5 rounded-none tracking-[0.02em]
variants:
  accent:  bg-accent-100 text-accent-800
  neutral: bg-neutral-100 text-neutral-800
  outline: border border-accent text-accent
```

Skip a distinct `accent-2` variant — it's the same voice as `accent`; add it only as an alias if a
consumer insists on the class name.
