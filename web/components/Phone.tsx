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
    <div className={"relative mx-auto w-full max-w-[290px] " + className}>
      {/* side buttons */}
      <div className="absolute -left-[3px] top-[22%] h-14 w-[3px] rounded-l bg-[#0c0c0c]" />
      <div className="absolute -right-[3px] top-[16%] h-8 w-[3px] rounded-r bg-[#0c0c0c]" />
      <div className="absolute -right-[3px] top-[30%] h-16 w-[3px] rounded-r bg-[#0c0c0c]" />
      <div className="relative aspect-[9/19] rounded-[2.7rem] border-[9px] border-[#101013] bg-[#101013] shadow-[0_40px_90px_-24px_rgba(80,40,110,0.5)]">
        {/* notch */}
        <div className="absolute left-1/2 top-[7px] z-30 h-[22px] w-[92px] -translate-x-1/2 rounded-b-2xl rounded-t-[10px] bg-[#101013]" />
        <div className={"absolute inset-0 overflow-hidden rounded-[2.05rem] " + screenClassName}>
          {children}
        </div>
      </div>
    </div>
  );
}

/** A faux Android status bar for the top of a phone screen. */
export function StatusBar({ dark = false }: { dark?: boolean }) {
  const c = dark ? "text-white/80" : "text-ink/70";
  return (
    <div className={"flex items-center justify-between px-5 pt-3 text-[10px] font-medium " + c}>
      <span>{"9:41"}</span>
      <div className="flex items-center gap-1">
        <span>{"5G"}</span>
        <span className="inline-block h-2 w-4 rounded-[2px] border border-current opacity-70" />
      </div>
    </div>
  );
}
