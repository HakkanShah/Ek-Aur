import { Logo } from "./Logo";

export function Nav() {
  return (
    <header className="sticky top-0 z-50 px-3 pt-3 sm:px-5 sm:pt-4">
      <nav className="mx-auto flex h-14 max-w-5xl items-center justify-between gap-3 rounded-full border border-hairline/80 bg-white/70 pl-4 pr-2 shadow-[0_10px_40px_-16px_rgba(28,28,30,0.28)] backdrop-blur-xl sm:h-[60px] sm:pl-5 sm:pr-2.5">
        <a href="#top" className="flex items-center gap-2.5">
          <Logo size={28} rounded={9} />
          <span className="grad-text text-[17px] font-extrabold tracking-tight">Ek Aur</span>
        </a>

        <div className="hidden items-center gap-1 text-sm font-medium text-ink/70 md:flex">
          <a href="#how" className="rounded-full px-3.5 py-2 transition-colors hover:bg-lav/70 hover:text-ink">
            How it works
          </a>
          <a href="#see" className="rounded-full px-3.5 py-2 transition-colors hover:bg-lav/70 hover:text-ink">
            See it
          </a>
          <a href="#install" className="rounded-full px-3.5 py-2 transition-colors hover:bg-lav/70 hover:text-ink">
            Install
          </a>
        </div>

        <a
          href="/api/download"
          className="ig-gradient rounded-full px-4 py-2 text-sm font-semibold text-white shadow-soft transition-transform duration-200 hover:-translate-y-0.5 active:scale-95 sm:px-5 sm:py-2.5"
        >
          Download
        </a>
      </nav>
    </header>
  );
}
