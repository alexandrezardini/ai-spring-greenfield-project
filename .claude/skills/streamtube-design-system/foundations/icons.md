# Foundation — Icons

**Live reference:** `docs/design-system/foundations/icons.html`

## System

Use **Lucide** icons (https://lucide.dev) throughout — nothing else. Icons render as **inline SVG on
`currentColor`**, so they inherit the text/accent color of their context automatically (a Lucide icon
inside a `.btn-primary` is `--color-bg`; inside a ghost button it's the accent).

## Sizing

Interface sizes — icons sit inline with 14px button/label text and inside the 36×36 icon button.
Keep icons at their natural interface size (typically 16px in controls); don't scale them up as
decoration. In buttons the icon is a **trailing** element and the text label still stays flush left
(see `components/buttons.md`).

## Tailwind/shadcn translation

- Install `lucide-react`; import named icons (`<Menu />`, `<ChevronDown />`, …).
- Do **not** set an explicit color on the icon — let it inherit `currentColor` from the control so
  themed hover/pressed/disabled states carry through for free.
- Size via the icon's `size`/`className` (e.g. `size-4` = 16px) consistently across a control set.
- For the icon-only button, center a single 16px icon in the 36×36 target (`components/buttons.md`).
