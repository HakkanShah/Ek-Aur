"use client";

import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { AppBadge } from "./AppBadge";
import { Reveal } from "./Reveal";

// The app's three looks, stop for stop (ui/theme/Palette.kt).
const LOOKS = [
  {
    name: "Reels",
    who: "Instagram only",
    canvas: "#FBF7FB",
    stops: ["#515BD4", "#8134AF", "#DD2A7B", "#F58529", "#FEDA77"],
  },
  {
    name: "Shorts",
    who: "YouTube only",
    canvas: "#FFF8F7",
    stops: ["#A80018", "#D0001A", "#E0001B", "#FF3B30", "#FF7A45"],
  },
  {
    name: "Both",
    who: "a bit of each",
    canvas: "#FCF7F9",
    stops: ["#5A4FD6", "#8E2FAA", "#DD2A7B", "#F0142E", "#FF7A3D"],
  },
];

const grad = (stops: string[]) => `linear-gradient(120deg, ${stops.join(", ")})`;

const FILTER_FILL = {
  all: grad(LOOKS[2].stops.slice(0, 4)),
  reels: grad(LOOKS[0].stops.slice(0, 4)),
  shorts: grad(LOOKS[1].stops.slice(0, 4)),
};

function Card({ title, desc, children }: { title: string; desc: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col rounded-card border border-hairline bg-white p-6 shadow-card">
      <div className="mb-5 flex min-h-[132px] items-center justify-center">{children}</div>
      <h3 className="text-lg font-bold text-ink">{title}</h3>
      <p className="mt-1.5 text-[14px] leading-relaxed text-smoke">{desc}</p>
    </div>
  );
}

/** Home's split card: each app's count and share over one two-colour bar. */
function SplitMock() {
  const reels = 120;
  const shorts = 45;
  const share = reels / (reels + shorts);
  return (
    <div className="w-full max-w-[260px] rounded-2xl border border-hairline bg-white p-4 shadow-sm">
      <div className="text-center text-[34px] font-extrabold leading-none tabular-nums">
        <span className="grad-text">{reels + shorts}</span>
      </div>
      <div className="mt-1 text-center text-[11px] text-smoke">Reels + Shorts today</div>
      <div className="mt-4 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <AppBadge app="reels" size={26} />
          <div className="leading-tight">
            <div className="text-[13px] font-bold tabular-nums">{reels}</div>
            <div className="text-[10px] text-smoke">Reels · {Math.round(share * 100)}%</div>
          </div>
        </div>
        <div className="flex items-center gap-2 text-right">
          <div className="leading-tight">
            <div className="text-[13px] font-bold tabular-nums">{shorts}</div>
            <div className="text-[10px] text-smoke">Shorts · {100 - Math.round(share * 100)}%</div>
          </div>
          <AppBadge app="shorts" size={26} />
        </div>
      </div>
      <div className="mt-3 flex h-2 gap-[3px]">
        <motion.div
          className="h-full rounded-full"
          style={{ background: "#C13584" }}
          initial={{ flexGrow: 0.5 }}
          whileInView={{ flexGrow: share }}
          viewport={{ once: true }}
          transition={{ duration: 1, ease: [0.22, 1, 0.36, 1] }}
        />
        <motion.div
          className="h-full rounded-full"
          style={{ background: "#D0001A" }}
          initial={{ flexGrow: 0.5 }}
          whileInView={{ flexGrow: 1 - share }}
          viewport={{ once: true }}
          transition={{ duration: 1, ease: [0.22, 1, 0.36, 1] }}
        />
      </div>
    </div>
  );
}

/** The three looks, cycling, the way the app cross-fades between them. */
function LooksMock() {
  const [i, setI] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setI((n) => (n + 1) % LOOKS.length), 2200);
    return () => clearInterval(id);
  }, []);
  const look = LOOKS[i];
  return (
    <div className="w-full max-w-[260px]">
      <motion.div
        className="rounded-2xl border border-hairline p-4 shadow-sm"
        animate={{ backgroundColor: look.canvas }}
        transition={{ duration: 0.45 }}
      >
        <div className="flex items-center justify-between">
          <span className="text-[13px] font-extrabold tracking-[0.18em]" style={{ backgroundImage: grad(look.stops), WebkitBackgroundClip: "text", color: "transparent" }}>
            EK AUR
          </span>
          <span className="text-[10px] text-smoke">{look.who}</span>
        </div>
        <motion.div
          key={look.name}
          initial={{ opacity: 0.4 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.45 }}
          className="mt-3 h-9 rounded-full"
          style={{ background: grad(look.stops.slice(0, 4)) }}
        />
      </motion.div>
      <div className="mt-3 flex justify-center gap-1.5 rounded-full bg-lav/70 p-1">
        {LOOKS.map((l, n) => (
          <button
            key={l.name}
            onClick={() => setI(n)}
            className={
              "rounded-full px-3 py-1 text-[11px] font-semibold transition-colors " +
              (n === i ? "text-white" : "text-smoke hover:text-ink")
            }
            style={n === i ? { background: grad(l.stops.slice(0, 4)) } : undefined}
          >
            {l.name}
          </button>
        ))}
      </div>
    </div>
  );
}

/** The leaderboard filter: All · Reels · Shorts, with rows that re-rank. */
function RaceMock() {
  const rows = [
    { name: "tanmay.exe", reels: 180, shorts: 31 },
    { name: "you", reels: 62, shorts: 118 },
    { name: "ria.scrolls", reels: 4, shorts: 150 },
  ];
  const [only, setOnly] = useState<"all" | "reels" | "shorts">("all");
  const value = (r: (typeof rows)[number]) => (only === "all" ? r.reels + r.shorts : only === "reels" ? r.reels : r.shorts);
  const ranked = [...rows].sort((a, b) => value(b) - value(a));
  const leader = value(ranked[0]) || 1;
  return (
    <div className="w-full max-w-[260px]">
      <div className="mb-3 flex justify-center gap-1.5 rounded-full bg-lav/70 p-1">
        {(["all", "reels", "shorts"] as const).map((k) => (
          <button
            key={k}
            onClick={() => setOnly(k)}
            className={
              "rounded-full px-3 py-1 text-[11px] font-semibold capitalize transition-colors " +
              (only === k ? "text-white" : "text-smoke hover:text-ink")
            }
            style={only === k ? { background: FILTER_FILL[k] } : undefined}
          >
            {k}
          </button>
        ))}
      </div>
      <div className="space-y-1.5">
        {ranked.map((r, n) => (
          <motion.div
            layout
            key={r.name}
            transition={{ type: "spring", stiffness: 300, damping: 30 }}
            className={"flex items-center gap-2 rounded-xl px-2 py-1.5 " + (r.name === "you" ? "bg-lav/70" : "")}
          >
            <span className="w-3 text-[11px] font-bold text-smoke">{n + 1}</span>
            <span className="flex-1 truncate text-[12px] font-semibold">{r.name}</span>
            <span className="flex gap-1">
              {r.reels > 0 && <AppBadge app="reels" size={14} />}
              {r.shorts > 0 && <AppBadge app="shorts" size={14} />}
            </span>
            <span className="w-8 text-right text-[12px] font-bold tabular-nums">{value(r)}</span>
            <span className="h-1.5 w-12 overflow-hidden rounded-full bg-lav">
              <motion.span
                className="ig-gradient block h-full rounded-full"
                animate={{ width: `${Math.max(6, (value(r) / leader) * 100)}%` }}
              />
            </span>
          </motion.div>
        ))}
      </div>
    </div>
  );
}

export function Shorts() {
  return (
    <section id="shorts" className="relative mx-auto max-w-6xl scroll-mt-24 px-5 py-14 md:py-20">
      <Reveal>
        <div className="mx-auto max-w-2xl text-center">
          <div className="mb-5 flex justify-center gap-2">
            <AppBadge app="reels" size={48} />
            <AppBadge app="shorts" size={48} />
          </div>
          <span className="inline-flex items-center gap-2 rounded-full bg-[#FDECEC] px-3 py-1 text-xs font-bold text-[#C4001A]">
            Reels + Shorts
          </span>
          <h2 className="mt-4 text-4xl font-extrabold tracking-tight sm:text-5xl">
            Counts <span className="bg-gradient-to-r from-[#D0001A] to-[#FF7A45] bg-clip-text text-transparent">YouTube Shorts</span> too.
          </h2>
          <p className="mt-4 text-[17px] leading-relaxed text-smoke">
            Half of you said &ldquo;I don&apos;t use Reels.&rdquo; Cool. Switching apps doesn&apos;t get
            you off the leaderboard any more. Pick Instagram, YouTube or both in Setup.
          </p>
        </div>
      </Reveal>

      <div className="mt-12 grid gap-5 md:grid-cols-3">
        <Reveal delay={0.05}>
          <Card
            title="One number"
            desc="Reels and Shorts go into the same count, the same pill and the same roasts. Home shows the split, so you know which app took the afternoon."
          >
            <SplitMock />
          </Card>
        </Reveal>
        <Reveal delay={0.12}>
          <Card
            title="Dresses to match"
            desc="Only Reels? The Instagram gradient. Only Shorts? A Shorts red. Both? A mix. It follows what you count, or pick one yourself."
          >
            <LooksMock />
          </Card>
        </Reveal>
        <Reveal delay={0.19}>
          <Card
            title="Race on one app"
            desc="The leaderboard ranks everyone on the total, with a switch to race on just Reels or just Shorts. Tap it."
          >
            <RaceMock />
          </Card>
        </Reveal>
      </div>

      <Reveal delay={0.1}>
        <p className="mx-auto mt-10 max-w-2xl text-center text-[14px] leading-relaxed text-smoke">
          Already installed? The app updates itself: open it, tap <b className="text-ink">Count Shorts</b> on
          Home, or switch it on in Setup → Apps.
        </p>
      </Reveal>
    </section>
  );
}
