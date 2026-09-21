import { Logo } from "./Logo";

export function Nav() {
  return (
    <header className="sticky top-0 z-50 border-b border-hairline/70 bg-canvas/70 backdrop-blur-xl">
      <nav className="mx-auto flex h-16 max-w-6xl items-center justify-between px-5">
        <a href="#top" className="flex items-center gap-2.5">
          <Logo size={30} rounded={9} />
          <span className="grad-text text-lg font-extrabold tracking-tight">Ek Aur</span>
        </a>

        <div className="hidden items-center gap-8 text-sm font-medium text-ink/70 md:flex">
          <a href="#how" className="transition-colors hover:text-ink">How it works</a>
          <a href="#see" className="transition-colors hover:text-ink">See it</a>
          <a href="#install" className="transition-colors hover:text-ink">Install</a>
        </div>

        <a
          href="/api/download"
          className="ig-gradient rounded-full px-4 py-2 text-sm font-semibold text-white shadow-soft transition-transform duration-200 hover:-translate-y-0.5 active:scale-95"
        >
          Download
        </a>
      </nav>
    </header>
  );
}
