# Component — Dialog (modal)

**Live reference:** `docs/design-system/components/dialog.html` · **CSS:** `styles.css` `/* — dialog — */`

## Modernist spec

The modal is the **top elevation** — the one place `--shadow-lg` is used.

- `.dialog-backdrop`: `position: fixed; inset: 0`, grid-centered, padding `--space-4`, background
  `neutral-900 @ 50%` (a scrim, not pure black).
- `.dialog`: `width: min(440px, 100%)`, column with `gap: --space-3`, padding `--space-4`,
  `background: --color-surface`, `--shadow-lg`, `border-radius: 0`.
- `.dialog-title`: heading font 800, 20px.
- `.dialog-body`: 14px, `opacity: 0.85`.
- `.dialog-actions`: flex row, `justify-content: flex-end`, `gap: --space-2`, `margin-top: --space-2`
  — actions are the one **right-aligned** cluster (primary + secondary buttons).

Even at top elevation the dialog is square-cornered and flat-surfaced — the shadow provides the lift,
the 0 radius keeps it Modernist.

## shadcn/cva translation

Wire Radix `Dialog` (`components/ui/dialog.tsx`) to the spec:

```
Overlay:  fixed inset-0 grid place-items-center p-4 bg-neutral-900/50
Content:  w-[min(440px,100%)] flex flex-col gap-3 p-4 bg-surface shadow-lg rounded-none
Title:    font-heading font-extrabold text-xl
Body:     text-sm opacity-85
Footer:   flex justify-end gap-2 mt-2      // right-aligned action cluster
```

- Strip the shadcn default `rounded-lg border` — dialog is `rounded-none`, surface-filled,
  `shadow-lg`.
- The **footer is the exception to flush-left**: dialog actions are right-aligned
  (`justify-end`). Everything else in the dialog (title, body) stays flush left.
- Scrim is `neutral-900/50`, not `black/80` — read the token, don't guess an opacity.
- Focus trap / ESC / `aria-modal` come from Radix; keep the global 2px accent focus ring on the
  buttons inside.
