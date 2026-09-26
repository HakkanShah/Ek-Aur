import { Pill } from "./Pill";
import { Reveal } from "./Reveal";

function Card({
  title,
  desc,
  children,
  className = "",
}: {
  title: string;
  desc: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={
        "flex flex-col rounded-card border border-hairline bg-white p-6 shadow-card " + className
      }
    >
      <div className="mb-5 flex min-h-[92px] items-center justify-center">{children}</div>
      <h3 className="text-lg font-bold text-ink">{title}</h3>
      <p className="mt-1.5 text-[14px] leading-relaxed text-smoke">{desc}</p>
    </div>
  );
}

function Tile({ n, label }: { n: string; label: string }) {
  return (
    <div className="rounded-2xl bg-lav/60 px-3 py-2.5 text-center">
      <div className="text-xl font-extrabold text-ink tabular-nums">{n}</div>
      <div className="mt-0.5 flex items-center justify-center gap-1 text-[10px] text-smoke">
        <span className="ig-gradient h-1.5 w-1.5 rounded-full" />
        {label}
      </div>
    </div>
  );
}

const MEDAL: Record<number, string> = { 1: "#F5B301", 2: "#B6BECC", 3: "#CD7F45" };

/** A race row like the app leaderboard: ranked, ringed avatar, and a bar to the leader. */
function RaceRow({
  r,
  name,
  n,
  leader,
  you = false,
}: {
  r: number;
  name: string;
  n: number;
  leader: number;
  you?: boolean;
}) {
  const pct = Math.max(6, Math.round((n / leader) * 100));
  return (
    <div className={"flex items-center gap-2.5 rounded-xl px-1.5 py-1 " + (you ? "bg-lav/70" : "")}>
      <span
        className="grid h-5 w-5 shrink-0 place-items-center rounded-full text-[11px] font-extrabold text-white"
        style={{ backgroundColor: MEDAL[r] ?? "#B4B4C0" }}
      >
        {r}
      </span>
      {/* stories-style gradient ring around the avatar */}
      <span className="ig-gradient grid h-8 w-8 shrink-0 place-items-center rounded-full p-[1.5px]">
        <span className="grid h-full w-full place-items-center rounded-full bg-white text-[11px] font-bold text-ink">
          {name.charAt(0).toUpperCase()}
        </span>
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <span className={"truncate text-[13px] font-semibold " + (you ? "text-acid" : "text-ink")}>
            {name}
            {you ? " (you)" : ""}
          </span>
          <span className="shrink-0 text-[13px] font-bold text-ink tabular-nums">{n}</span>
        </div>
        <div className="mt-1 h-1.5 w-full overflow-hidden rounded-full bg-lav">
          <div
            className={(you ? "bg-acid " : "ig-gradient ") + "h-full rounded-full"}
            style={{ width: pct + "%" }}
          />
        </div>
      </div>
    </div>
  );
}

export function Features() {
  return (
    <section id="features" className="mx-auto max-w-6xl px-5 py-14 md:py-20">
      <Reveal>
        <h2 className="max-w-2xl text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
          It counts. It roasts. It never lectures.
        </h2>
        <p className="mt-3 max-w-xl text-[15px] text-smoke">
          Everything happens on your phone. The only thing that ever leaves is your name and a
          daily total, and only if you want on the leaderboard.
        </p>
      </Reveal>

      <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        <Reveal>
          <Card
            title="A counter that floats"
            desc="A little pill rides over Instagram and ticks up with every reel. The face gets more cooked as the number climbs."
          >
            <Pill count={137} />
          </Card>
        </Reveal>

        <Reveal delay={0.05}>
          <Card
            title="An honest dashboard"
            desc="Today, the last seven days, your worst day ever. No goals, no guilt — the bars say plenty."
          >
            <div className="grid w-full grid-cols-3 gap-2">
              <Tile n="137" label="Today" />
              <Tile n="892" label="7 days" />
              <Tile n="1.1k" label="Best" />
            </div>
          </Card>
        </Reveal>

        <Reveal delay={0.1}>
          <Card
            title="One global leaderboard"
            desc="Everyone who installs it is on one list, ranked on today's Reels and Shorts. Filter to just one app. No adding friends, no join step."
          >
            <div className="w-full space-y-1.5">
              <RaceRow r={1} name="rohan" n={402} leader={402} />
              <RaceRow r={2} name="hakkan" n={137} leader={402} you />
              <RaceRow r={3} name="priya" n={96} leader={402} />
            </div>
          </Card>
        </Reveal>

        <Reveal delay={0.05}>
          <Card
            title="Milestone roasts"
            desc="Round numbers, the small hours, and the numbers that are only funny on the dot: 69, 99, 420, and Kohli's 973. Once a day, never a nag."
          >
            <Pill count={69} message="69. nice. 😏" />
          </Card>
        </Reveal>

        <Reveal delay={0.1}>
          <Card
            title="Payments stay clean"
            desc="A bank app hates any accessibility service. So Ek Aur switches itself off the moment you leave Instagram — payments just work."
          >
            <div className="flex items-center gap-3 rounded-2xl bg-lav/60 px-4 py-3">
              <span className="text-sm font-semibold text-ink">Auto-off</span>
              <span className="relative inline-flex h-[22px] w-[38px] items-center rounded-full bg-good">
                <span className="absolute right-[3px] h-4 w-4 rounded-full bg-white shadow" />
              </span>
            </div>
          </Card>
        </Reveal>

        <Reveal delay={0.15}>
          <Card
            title="Yours, on your phone"
            desc="Raw scrolls and which videos you watched never leave the device. It can't read the screen — just the swipe."
          >
            <div className="grid h-12 w-12 place-items-center rounded-2xl bg-lav/60">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="url(#lg)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <defs>
                  <linearGradient id="lg" x1="0" y1="24" x2="24" y2="0">
                    <stop stopColor="#8134AF" />
                    <stop offset="1" stopColor="#F58529" />
                  </linearGradient>
                </defs>
                <rect x="4" y="10" width="16" height="10" rx="2.5" />
                <path d="M8 10V7a4 4 0 0 1 8 0v3" />
              </svg>
            </div>
          </Card>
        </Reveal>
      </div>
    </section>
  );
}
