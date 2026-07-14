# Component — Navigation (header bar)

**Live reference:** `docs/design-system/components/navigation.html` · **CSS:** `styles.css` `/* — navigation — */`

## Modernist spec

`.nav`: a flex row, `gap: --space-4`, padding `--space-3 --space-4`, closed by a **2px
`--color-divider` bottom rule** — the header is separated from the page by a rule, not a shadow.

- `.nav-brand`: heading font 800, 18px, `margin-right: auto` — the brand pushes the links to the
  right while itself staying flush left.
- `.nav a`: inherits ink color, 14px, no underline. Hover **and** the current page
  (`a[aria-current="page"]`) → **accent** color. That's the whole active-state treatment: color, no
  underline, no pill.

The nav is flat and ruled — no background fill beyond the page ground, no shadow.

## shadcn/cva translation

There's no single shadcn "nav" primitive — compose one:

```
<header> :  flex items-center gap-4 px-4 py-3 border-b-2 border-divider
brand    :  font-heading font-extrabold text-lg mr-auto
link     :  text-sm text-foreground no-underline
            hover:text-accent aria-[current=page]:text-accent
```

- Use Next.js `<Link>`; drive the active state off `usePathname()` → set `aria-current="page"` and
  let the `aria-[current=page]:text-accent` selector do the styling (don't add a separate active class).
- The `border-b-2 border-divider` is the load-bearing detail — it's the 2px rule, not a `shadow-sm`.
- Brand keeps `mr-auto`; everything stays flush left / right-grouped, never centered.
