# Component — Forms

**Live reference:** `docs/design-system/components/forms.html` · **CSS:** `styles.css` `/* — forms — */`
**Applied in StreamTube:** every auth screen (login, sign-up, recover, set-new-password) is a form.

Native elements, themed states, **no JavaScript** in the DS reference.

## Field + label

`.field > label`: 12px, `margin-bottom: 5px`, 70%-alpha ink (label sits above the input, flush left).

## Text input

`.input`: full width, `min-height: 36px`, padding `6px 10px`, **14px** (matches `.btn`),
`background: --color-surface`, `1px --color-divider` border, `border-radius: 0`,
`caret-color: --color-accent`.

| State | Style |
|---|---|
| hover | border → `ink 45%` |
| focus | `:focus-visible` border → `--color-accent`, `outline-offset: 0` |
| textarea | `textarea.input` → `min-height: 90px`, `resize: vertical` |

Note: the input's focus is a **border** change (offset 0), not the standard 2px outer ring — the ring
is for buttons/choices. Match this.

## Radio (`.radio` + `.dot`)

The native `input` is visually hidden; a `.dot` (16px circle, 1.5px divider border) renders the state:

- hover → dot border accent
- checked → accent border + accent fill + `inset 0 0 0 4px --color-bg` (the ground "hole" in the center)
- focus → 2px accent ring, offset 2px

## Segmented control (`.seg` + `.seg-opt`)

Inline row, 1px divider frame, `border-radius: 0`, options split by a 1px divider (`.seg-opt + .seg-opt`).
Selected option (`:has(input:checked)`) → **solid accent fill, `--color-bg` text**. Unselected hover →
`ink 7%` tint. Focus → 2px accent ring, offset **-2px** (inset, so it stays inside the frame).

## shadcn/cva translation

- **Input** (`components/ui/input.tsx`): `bg-surface border border-divider rounded-none min-h-9 px-2.5
  text-sm caret-accent`, hover `border-foreground/45`, `focus-visible:border-accent
  focus-visible:outline-none`. **Not** the ring treatment shadcn ships — Modernist inputs use a
  border-color focus.
- **Label** (`components/ui/label.tsx`): `text-xs mb-[5px] text-foreground/70 block`.
- **Textarea**: same as input + `min-h-[90px] resize-y`.
- **Radio group** (Radix `RadioGroupItem`): `size-4 rounded-full border-[1.5px] border-divider`,
  checked → `border-accent bg-accent shadow-[inset_0_0_0_4px_var(--color-bg)]`, focus-visible ring.
- **Segmented / Toggle group**: inline flex, `border border-divider rounded-none divide-x
  divide-divider`; selected item `bg-accent text-background`; focus ring **inset** (`outline-offset:-2px`).
- Keep every control `rounded-none`. Labels and helper text stay flush left; caret is accent.
