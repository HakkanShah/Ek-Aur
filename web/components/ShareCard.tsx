const WEEK = [12, 40, 0, 120, 88, 260, 969];

export function ShareCard({
  count = 969,
  week = WEEK,
  watched = "1h 52m watched",
  username = "hakkan",
  grow = 1,
  showDare = true,
  dare = "You can't beat this. Don't try.",
  square = true,
  className = "",
}: {
  count?: number;
  week?: number[];
  watched?: string;
  username?: string;
  grow?: number;
  showDare?: boolean;
  dare?: string;
  square?: boolean;
  className?: string;
}) {
  const peak = Math.max(1, ...week);
  return (
    <div
      className={
        "flex w-full flex-col rounded-[26px] bg-white p-5 shadow-card ring-1 ring-hairline " +
        (square ? "aspect-square " : "") +
        className
      }
    >
      {/* header: wordmark left, person right */}
      <div className="flex items-start justify-between">
        <div>
          <div className="grad-text text-lg font-extrabold tracking-[0.14em]">EK AUR</div>
          <div className="text-[10px] tracking-[0.2em] text-smoke">one more</div>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm font-bold text-ink">{username}</span>
          <span className="ig-gradient grid h-7 w-7 place-items-center rounded-full text-[11px] font-bold text-white">
            {username.charAt(0).toUpperCase()}
          </span>
        </div>
      </div>

      {/* hero number */}
      <div className="mt-3 grad-text text-6xl font-extrabold leading-none tracking-tight tabular-nums">
        {count}
      </div>
      <div className="mt-2 text-lg text-smoke">Reels today</div>
      <div className="text-sm text-ash">{watched}</div>

      {/* week bars */}
      <div className="mt-5">
        <div className="mb-2 text-xs text-smoke">Last 7 days</div>
        <div className="flex h-24 items-end gap-2 border-b border-hairline pb-0">
          {week.map((v, i) => {
            const h = Math.max(v > 0 ? 4 : 0, (v / peak) * 96 * grow);
            const isPeak = v === peak && v > 0;
            return (
              <div
                key={i}
                className={
                  "flex-1 rounded-t-[3px] transition-[height,opacity] duration-500 " +
                  (isPeak ? "bg-acid" : "bg-acid/40")
                }
                style={{ height: `${h}px`, opacity: v > 0 ? 0.35 + 0.65 * grow : 0 }}
              />
            );
          })}
        </div>
      </div>

      {/* dare + link */}
      <div
        className="mt-4 transition-opacity duration-500"
        style={{ opacity: showDare ? 1 : 0 }}
      >
        <div className="text-xl font-extrabold text-acid">{dare}</div>
        <div className="mt-1 text-xs text-ash">ek-aur.vercel.app</div>
      </div>
    </div>
  );
}
