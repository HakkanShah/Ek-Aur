export function Phone({
  children,
  screenClassName = "bg-canvas",
  className = "",
}: {
  children: React.ReactNode;
  screenClassName?: string;
  className?: string;
}) {
  return (
    <div className={"relative mx-auto w-full max-w-[280px] " + className}>
      {/* side buttons */}
      <div className="absolute -left-[3px] top-[22%] h-14 w-[3px] rounded-l-sm bg-[#0c0c0c]" />
      <div className="absolute -right-[3px] top-[16%] h-8 w-[3px] rounded-r-sm bg-[#0c0c0c]" />
      <div className="absolute -right-[3px] top-[30%] h-16 w-[3px] rounded-r-sm bg-[#0c0c0c]" />

      {/* titanium frame */}
      <div className="relative aspect-[9/19] rounded-[2.9rem] border-[7px] border-[#0d0d10] bg-[#0d0d10] shadow-[0_50px_100px_-28px_rgba(80,40,110,0.55)]">
        {/* screen */}
        <div className={"absolute inset-0 overflow-hidden rounded-[2.35rem] " + screenClassName}>
          {children}
        </div>

        {/* dynamic island — floats above the screen, never in the way of content */}
        <div className="pointer-events-none absolute left-1/2 top-[9px] z-40 flex h-[24px] w-[82px] -translate-x-1/2 items-center justify-end rounded-full bg-black pr-2.5">
          <span className="h-[7px] w-[7px] rounded-full bg-[#171720] ring-1 ring-white/10" />
        </div>

        {/* subtle inner screen edge for realism */}
        <div className="pointer-events-none absolute inset-0 z-40 rounded-[2.35rem] ring-1 ring-inset ring-white/5" />
      </div>
    </div>
  );
}

/** A faux Android status bar for the top of a phone screen. Clears the island. */
export function StatusBar({ dark = false }: { dark?: boolean }) {
  const c = dark ? "text-white/85" : "text-ink/70";
  return (
    <div
      className={
        "flex items-center justify-between px-6 pt-[13px] text-[10px] font-semibold tracking-tight " +
        c
      }
    >
      <span>{"9:41"}</span>
      <div className="flex items-center gap-1.5">
        <span>{"5G"}</span>
        <span className="inline-block h-[9px] w-4 rounded-[3px] border border-current opacity-70" />
      </div>
    </div>
  );
}
