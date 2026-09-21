"use client";

import { useEffect, useState } from "react";
import { Phone, StatusBar } from "./Phone";
import { ShareCard } from "./ShareCard";

const easeOutCubic = (x: number) => 1 - Math.pow(1 - x, 3);

export function ShareSim() {
  const [t, setT] = useState(0);

  useEffect(() => {
    let raf = 0;
    let start = performance.now();
    const build = 2600;
    const hold = 1700;
    const loop = (now: number) => {
      const e = now - start;
      if (e < build) setT(e / build);
      else if (e < build + hold) setT(1);
      else start = now;
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(raf);
  }, []);

  const p = easeOutCubic(Math.min(1, t));
  const count = Math.round(969 * p);
  const grow = Math.min(1, t * 1.5);
  const showDare = t > 0.82;

  return (
    <Phone>
      <StatusBar />
      <div className="flex h-full flex-col px-4 pb-4 pt-3">
        <div className="flex items-center gap-2">
          <span className="ig-gradient h-4 w-[3px] rounded-full" />
          <span className="text-sm font-bold text-ink">Share card</span>
        </div>
        <div className="mt-4">
          <ShareCard count={count} grow={grow} showDare={showDare} square={false} />
        </div>
        <div className="mt-auto pt-4">
          <div className="ig-gradient grid place-items-center rounded-full py-2.5 text-[13px] font-semibold text-white shadow-soft">
            Share
          </div>
        </div>
      </div>
    </Phone>
  );
}
