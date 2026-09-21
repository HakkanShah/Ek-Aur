"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { Phone, StatusBar } from "./Phone";
import { Pill } from "./Pill";

const ROWS = [
  { name: "Accessibility", why: "counts your reels" },
  { name: "Overlay", why: "floats the counter" },
  { name: "Usage access", why: "auto-off for payments" },
];

function Switch({ on }: { on: boolean }) {
  return (
    <span
      className={
        "relative inline-flex h-[22px] w-[38px] items-center rounded-full transition-colors duration-500 " +
        (on ? "bg-good" : "bg-hairline")
      }
    >
      <motion.span
        className="absolute h-[16px] w-[16px] rounded-full bg-white shadow"
        animate={{ x: on ? 19 : 3 }}
        transition={{ type: "spring", stiffness: 500, damping: 30 }}
      />
    </span>
  );
}

export function SetupSim() {
  const [step, setStep] = useState(0); // 0 none, 1..3 rows on, 3 = done

  useEffect(() => {
    const id = setInterval(() => setStep((s) => (s + 1) % 4), 1250);
    return () => clearInterval(id);
  }, []);

  const done = step >= 3;

  return (
    <Phone>
      <StatusBar />
      <div className="flex h-full flex-col px-4 pb-4 pt-3">
        <div className="flex items-center gap-2">
          <span className="ig-gradient h-4 w-[3px] rounded-full" />
          <span className="text-sm font-bold text-ink">Setup</span>
        </div>

        {/* progress */}
        <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-lav">
          <motion.div
            className="ig-gradient h-full rounded-full"
            animate={{ width: `${(step / 3) * 100}%` }}
            transition={{ type: "spring", stiffness: 120, damping: 20 }}
          />
        </div>
        <div className="mt-2 text-[11px] text-smoke">
          {done ? "All set." : `${step} of 3 done`}
        </div>

        {/* permission rows */}
        <div className="mt-3 space-y-2.5">
          {ROWS.map((r, i) => {
            const on = step > i;
            return (
              <div
                key={r.name}
                className="flex items-center gap-3 rounded-2xl bg-white p-3 shadow-card ring-1 ring-hairline"
              >
                <span
                  className={
                    "h-2.5 w-2.5 rounded-full transition-colors duration-500 " +
                    (on ? "bg-good" : "bg-ash")
                  }
                />
                <div className="flex-1">
                  <div className="text-[13px] font-semibold text-ink">{r.name}</div>
                  <div className="text-[10px] text-smoke">{r.why}</div>
                </div>
                <Switch on={on} />
              </div>
            );
          })}
        </div>

        {/* payoff: the pill drops in */}
        <div className="relative mt-auto h-16">
          <AnimatePresence>
            {done && (
              <motion.div
                className="absolute inset-x-0 flex flex-col items-center gap-2"
                initial={{ opacity: 0, y: -14 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -14 }}
                transition={{ type: "spring", stiffness: 260, damping: 22 }}
              >
                <Pill count={1} className="scale-90" />
                <span className="text-[11px] font-medium text-acid">Counter’s live. Go scroll.</span>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </Phone>
  );
}
