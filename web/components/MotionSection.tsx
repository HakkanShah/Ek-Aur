import { SetupSim } from "./SetupSim";
import { CountSim } from "./CountSim";
import { ShareSim } from "./ShareSim";
import { Reveal } from "./Reveal";

function SimRow({
  id,
  sim,
  kicker,
  title,
  body,
  points,
  flip = false,
}: {
  id?: string;
  sim: React.ReactNode;
  kicker: string;
  title: string;
  body: string;
  points: string[];
  flip?: boolean;
}) {
  return (
    <div
      id={id}
      className="grid scroll-mt-24 items-center gap-10 md:grid-cols-2 md:gap-14"
    >
      <Reveal className={flip ? "md:order-2" : ""}>
        <div className="animate-floaty [animation-duration:7s]">{sim}</div>
      </Reveal>
      <Reveal delay={0.08} className={flip ? "md:order-1" : ""}>
        <div className="text-xs font-bold uppercase tracking-widest text-acid">{kicker}</div>
        <h3 className="mt-2 text-3xl font-extrabold tracking-tight text-ink">{title}</h3>
        <p className="mt-3 max-w-md text-[15px] leading-relaxed text-smoke">{body}</p>
        <ul className="mt-5 space-y-2.5">
          {points.map((p) => (
            <li key={p} className="flex items-start gap-3 text-[14px] text-ink">
              <span className="mt-[7px] h-1.5 w-1.5 shrink-0 rounded-full ig-gradient" />
              {p}
            </li>
          ))}
        </ul>
      </Reveal>
    </div>
  );
}

export function MotionSection() {
  return (
    <section id="see" className="relative overflow-hidden bg-white/60">
      <div className="pointer-events-none absolute inset-0 -z-10">
        <div className="absolute right-0 top-1/4 h-80 w-80 rounded-full bg-g3/10 blur-[100px]" />
        <div className="absolute left-0 bottom-1/4 h-80 w-80 rounded-full bg-g1/10 blur-[100px]" />
      </div>

      <div className="mx-auto max-w-6xl px-5 py-20 md:py-28">
        <Reveal>
          <h2 className="max-w-2xl text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
            Three taps to set up. Then it just runs.
          </h2>
          <p className="mt-3 max-w-xl text-[15px] text-smoke">
            Live simulations, not screenshots — this is exactly what the app does.
          </p>
        </Reveal>

        <div className="mt-16 space-y-24 md:space-y-28">
          <SimRow
            id="how"
            sim={<SetupSim />}
            kicker="01 · Set up"
            title="Flip three switches"
            body="Grant accessibility (to see the swipe), overlay (to float the counter), and usage access (so it can switch off for payments). A progress bar walks you through it."
            points={[
              "Guided, one-time — the app checks each grant for you",
              "It reads the swipe, never the screen or your messages",
              "The moment it's done, the counter goes live",
            ]}
          />
          <SimRow
            sim={<CountSim />}
            kicker="02 · Use it"
            title="Then just scroll"
            body="Open Instagram and forget it's there. Every reel bumps the pill; the face gets more cooked as the number climbs; a dry line lands at the milestones."
            points={[
              "One swipe, one count — feed scrolling never counts",
              "Double-tap the pill to jump into the app",
              "Drag it anywhere it's in your way",
            ]}
            flip
          />
          <SimRow
            sim={<ShareSim />}
            kicker="03 · Flex it"
            title="Build a card to dunk with"
            body="One tap turns today into a clean, gradient share card — your number, your week, and a dare. It links straight to the download, so your friends have no excuse."
            points={[
              "Ready instantly, share anywhere",
              "The dare scales with how cooked you are",
              "The link points to the latest build",
            ]}
          />
        </div>
      </div>
    </section>
  );
}
