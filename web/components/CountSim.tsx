"use client";

import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Phone } from "./Phone";
import { Pill } from "./Pill";
import { AppIcon, type AppKind } from "./AppBadge";

type Clip = { from: string; to: string; cap: string; user: string; app: AppKind };

// Six Reels, then six Shorts: one running number across both apps.
const CLIPS: Clip[] = [
  { from: "#3a1c71", to: "#d76d77", cap: "wait for it…", user: "chai.memes", app: "reels" },
  { from: "#0f2027", to: "#2c5364", cap: "he really said that", user: "cricket.clips", app: "reels" },
  { from: "#42275a", to: "#734b6d", cap: "3am thoughts", user: "lofi.nights", app: "reels" },
  { from: "#c31432", to: "#240b36", cap: "one more, promise", user: "food.wala", app: "reels" },
  { from: "#1a2980", to: "#26d0ce", cap: "POV: it's 2am", user: "night.owl", app: "reels" },
  { from: "#833ab4", to: "#fd1d1d", cap: "the algorithm knows", user: "reels.daily", app: "reels" },
  { from: "#ff512f", to: "#dd2476", cap: "4th hokage peak entry", user: "anime.edits", app: "shorts" },
  { from: "#1d2b64", to: "#f8cdda", cap: "this build is illegal", user: "bgmi.clips", app: "shorts" },
  { from: "#3c1053", to: "#ad5389", cap: "wait till the end", user: "desi.shorts", app: "shorts" },
  { from: "#f7971e", to: "#c21500", cap: "₹100 street food", user: "khana.shorts", app: "shorts" },
  { from: "#00b09b", to: "#1f4037", cap: "setup tour 2026", user: "tech.bhai", app: "shorts" },
  { from: "#cb2d3e", to: "#ef473a", cap: "last one. definitely.", user: "one.more", app: "shorts" },
];

const START = 22;

function ActionIcon({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid h-9 w-9 place-items-center rounded-full bg-white/10 text-white">
      {children}
    </div>
  );
}

export function CountSim() {
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const id = setInterval(() => setTick((t) => (t + 1) % CLIPS.length), 1100);
    return () => clearInterval(id);
  }, []);

  const count = START + tick;
  const index = tick % CLIPS.length;
  const app = CLIPS[index].app;
  const message =
    count === 25 ? "25 in. warmed up." : count === 30 ? "switched apps. didn't help." : undefined;

  return (
    <Phone screenClassName="bg-black">
      {/* the reels feed, scrolled one reel per swipe */}
      <motion.div
        className="absolute inset-0"
        animate={{ y: `-${index * 100}%` }}
        transition={{ type: "spring", stiffness: 90, damping: 18 }}
      >
        {CLIPS.map((r, i) => (
          <div key={i} className="relative h-full w-full" style={{ height: "100%" }}>
            <div
              className="absolute inset-0"
              style={{ background: `linear-gradient(150deg, ${r.from}, ${r.to})` }}
            />
            <div className="absolute inset-0 bg-black/15" />
            {/* right action rail */}
            <div className="absolute bottom-24 right-3 flex flex-col items-center gap-4">
              <ActionIcon>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 21s-7-4.5-9.5-8.5C.5 9 2.5 6 5.5 6 7.5 6 9 7.2 12 10c3-2.8 4.5-4 6.5-4 3 0 5 3 3 6.5C19 16.5 12 21 12 21z" />
                </svg>
              </ActionIcon>
              <ActionIcon>
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M21 12a8 8 0 0 1-11.6 7.1L3 21l1.9-6.4A8 8 0 1 1 21 12z" />
                </svg>
              </ActionIcon>
              <ActionIcon>
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M22 2 11 13M22 2l-7 20-4-9-9-4 20-7z" />
                </svg>
              </ActionIcon>
            </div>
            {/* bottom caption */}
            <div className="absolute bottom-6 left-4 right-16 text-white">
              <div className="flex items-center gap-2 text-[12px] font-semibold">
                @{r.user}
                {r.app === "shorts" && (
                  <span className="rounded-full bg-white px-2 py-0.5 text-[9px] font-bold text-black">Subscribe</span>
                )}
              </div>
              <div className="mt-1 text-[11px] text-white/85">{r.cap}</div>
            </div>
          </div>
        ))}
      </motion.div>

      {/* which app is on screen: the count carries straight on across both */}
      <div className="absolute left-3 top-[56px] z-30">
        <motion.div
          key={app}
          initial={{ opacity: 0, y: -4 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex items-center gap-1.5 rounded-full bg-black/45 px-2.5 py-1 text-[10px] font-semibold text-white backdrop-blur"
        >
          <AppIcon app={app} size={12} color="#fff" />
          {app === "reels" ? "Instagram Reels" : "YouTube Shorts"}
        </motion.div>
      </div>

      {/* the floating counter — sits up top where a phone's island would be */}
      <div className="pointer-events-none absolute inset-x-0 top-[14px] z-30 flex justify-center px-4">
        <Pill count={count} message={message} />
      </div>
    </Phone>
  );
}
