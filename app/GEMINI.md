# TukiWatch Project Instructions

## Code Quality Standards
- **Biome:** We use [Biome](https://biomejs.dev/) for formatting, linting, and organizing imports. 
  - Always run `bunx --bun @biomejs/biome check --write` before finalizing changes.
  - Use `--unsafe` to automatically fix issues like unused imports.
- **TypeScript:** Avoid `any` whenever possible. Use strict typing for props and hook returns.
- **React Hooks:** Ensure stable function identities using `useCallback` when functions are used as dependencies in `useEffect` or passed to memoized components.
- **CSS:** Use standard CSS. Avoid Sass-specific functions like `darken()` or `lighten()`. Use `filter: brightness()` or similar CSS-native alternatives.
