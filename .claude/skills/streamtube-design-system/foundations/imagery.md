# Foundation — Imagery

**Live reference:** `docs/design-system/foundations/image.html` · **Class:** `.grayscale`

## Rule

Every content photograph prints in **pure black and white**. Modernist wraps images in `.grayscale`:

```css
.grayscale { filter: grayscale(1) contrast(1.08); }
```

The slight contrast bump keeps B&W photos from going flat. Wrap hero and inline images alike.

## Do / Don't

- **Do** run every content photograph and figure through the grayscale treatment.
- **Don't** tint, colorize, or duotone imagery — the accent never touches a photo.
- `img` defaults: `display: block; max-width: 100%`. `figure` has no margin; `figcaption` is 11px,
  55%-alpha ink.

## Scope

This applies to **content photography**. It does not mean the UI is grayscale — the accent red still
carries actions and emphasis. Only photographs/figures get desaturated.

## Tailwind/shadcn translation

- Provide a small image wrapper (or utility) that applies `grayscale contrast-[1.08]` and pair it
  with `next/image`.
- Set `next/image` to `block` with `max-w-full`; carry captions at 11px muted.
- Because it's a `filter`, it composes with `next/image` optimization without touching the source
  asset — keep originals in color; desaturate at render.
