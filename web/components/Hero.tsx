import { CountSim } from "./CountSim";
import { DownloadButton } from "./DownloadButton";
import { Reveal } from "./Reveal";

export function Hero() {
  return (
    <section id="top" className="relative overflow-hidden">
      {/* soft gradient atmosphere */}
      <div className="pointer-events-none absolute inset-0 -z-10">
        <div className="animate-drift absolute -left-24 -top-24 h-[26rem] w-[26rem] rounded-full bg-g2/25 blur-[90px]" />
        <div className="animate-drift absolute -right-16 top-10 h-[24rem] w-[24rem] rounded-full bg-g3/20 blur-[90px]" />
        <div className="absolute left-1/3 top-1/2 h-[20rem] w-[20rem] rounded-full bg-g4/15 blur-[100px]" />
      </div>

      <div className="mx-auto grid max-w-6xl items-center gap-12 px-5 pb-16 pt-14 md:grid-cols-2 md:gap-8 md:pb-24 md:pt-20">
        {/* copy */}
        <div>
          <Reveal>
            <span className="inline-flex items-center gap-2 rounded-full border border-hairline bg-white/70 px-3 py-1 text-xs font-semibold text-smoke shadow-sm backdrop-blur">
              <span className="grad-text font-bold">BETA</span>
              <span className="h-1 w-1 rounded-full bg-ash" />
              Instagram Reels, counted
            </span>
          </Reveal>

          <Reveal delay={0.05}>
            <h1 className="mt-5 text-6xl font-extrabold leading-[0.95] tracking-tight sm:text-7xl">
              <span className="grad-text">Ek Aur.</span>
            </h1>
            <p className="mt-1 text-lg tracking-[0.2em] text-smoke">ONE MORE</p>
          </Reveal>

          <Reveal delay={0.12}>
            <p className="mt-6 max-w-md text-xl font-medium leading-snug text-ink sm:text-2xl">
              The reel counter that cheers you on for one more —{" "}
              <span className="text-smoke">while the number quietly turns you in.</span>
            </p>
          </Reveal>

          <Reveal delay={0.18}>
            <p className="mt-4 max-w-md text-[15px] leading-relaxed text-smoke">
              No lectures, no streaks to protect, no wellness sermon. A floating counter over
              Instagram, an honest dashboard, a global leaderboard, and a dry roast every
              milestone. The graph does the judging.
            </p>
          </Reveal>

          <Reveal delay={0.24}>
            <div className="mt-8 flex flex-col items-start gap-3 sm:flex-row sm:items-start sm:gap-4">
              <DownloadButton className="items-start" />
              <a
                href="#see"
                className="group inline-flex items-center justify-center gap-2 rounded-full border border-hairline bg-white px-6 py-3.5 text-[15px] font-semibold text-ink shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:border-ash/60 hover:shadow-md active:scale-[0.97]"
              >
                See how it works
                <span aria-hidden className="transition-transform duration-200 group-hover:translate-y-0.5">
                  ↓
                </span>
              </a>
            </div>
          </Reveal>
        </div>

        {/* phone */}
        <Reveal delay={0.15} className="relative">
          <div className="animate-floaty">
            <CountSim />
          </div>
        </Reveal>
      </div>
    </section>
  );
}
