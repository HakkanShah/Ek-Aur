"use client";

import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { Phone } from "./Phone";
import { PlayProtectSim, SCENES } from "./PlayProtectSim";

const STEPS = [
  {
    title: "Install says “App blocked”",
    body: "Everyone sees this the first time. Play Protect is cautious about apps from outside the Play Store. Tap OK.",
  },
  {
    title: "Open the Play Store, tap your profile picture",
    body: "It's the round picture at the top right.",
  },
  {
    title: "Tap Play Protect",
    body: "In the menu, under Payments and subscriptions.",
  },
  {
    title: "Tap the settings gear",
    body: "Top right, next to the refresh arrow.",
  },
  {
    title: "Switch off “Scan apps with Play Protect”",
    body: "The first switch on the page.",
  },
  {
    title: "Tap Pause",
    body: "It switches itself back on tomorrow. On older phones there's no Pause: the switch just turns off (tap Turn off if asked). Switch it back on after installing.",
  },
  {
    title: "Install Ek Aur again",
    body: "Open the file from Downloads, tap Install. That's it.",
  },
];

const TICK = 50;

export function PlayProtectGuide() {
  const reduced = useReducedMotion();
  const [scene, setScene] = useState(0);
  const [t, setT] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [inView, setInView] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  // Only run while on screen: no work, and no missed steps, when scrolled past.
  useEffect(() => {
    const el = box.current;
    if (!el) return;
    const io = new IntersectionObserver(([e]) => setInView(e.isIntersecting), { threshold: 0.35 });
    io.observe(el);
    return () => io.disconnect();
  }, []);

  const running = playing && inView && !reduced;

  useEffect(() => {
    if (!running) return;
    const id = setInterval(() => {
      setT((prev) => {
        const next = prev + TICK;
        if (next < SCENES[scene].duration) return next;
        setScene((s) => (s + 1) % SCENES.length);
        return 0;
      });
    }, TICK);
    return () => clearInterval(id);
  }, [running, scene]);

  function jump(i: number) {
    setScene(i);
    setT(reduced ? SCENES[i].still : 0);
  }

  // With reduced motion, each step shows its telling moment, still.
  const shownT = reduced ? SCENES[scene].still : t;

  return (
    <div
      ref={box}
      id="play-protect"
      className="relative scroll-mt-24 overflow-hidden rounded-[32px] border border-hairline bg-white shadow-card"
    >
      <div className="pointer-events-none absolute -left-20 -top-24 h-72 w-72 rounded-full bg-[#34A853]/10 blur-[80px]" />
      <div className="pointer-events-none absolute -right-16 bottom-0 h-72 w-72 rounded-full bg-g3/10 blur-[90px]" />

      <div className="relative grid gap-8 p-6 sm:p-10 md:grid-cols-[minmax(0,300px)_1fr] md:grid-rows-[auto_1fr] md:gap-x-14 md:gap-y-2">
        {/* the phone: left on desktop, between heading and steps on a phone */}
        <div className="order-2 mx-auto w-full max-w-[300px] md:order-none md:col-start-1 md:row-span-2 md:row-start-1 md:self-center">
          <Phone screenClassName="bg-white" className="max-w-[300px]">
            <PlayProtectSim scene={scene} t={shownT} />
          </Phone>

          {/* scrubber: one segment per step */}
          <div className="mt-5 flex items-center gap-2">
            <button
              type="button"
              onClick={() => setPlaying((p) => !p)}
              aria-label={playing ? "Pause the walkthrough" : "Play the walkthrough"}
              className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-ink text-white transition-transform active:scale-90"
            >
              {playing && !reduced ? (
                <svg width="12" height="12" viewBox="0 0 24 24" aria-hidden>
                  <path d="M8 5v14M16 5v14" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
                </svg>
              ) : (
                <svg width="12" height="12" viewBox="0 0 24 24" aria-hidden>
                  <path d="M7 4.5v15l12-7.5z" fill="currentColor" />
                </svg>
              )}
            </button>
            <div className="flex flex-1 gap-1">
              {SCENES.map((s, i) => (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => jump(i)}
                  aria-label={`Step ${i + 1}`}
                  className="group h-8 flex-1 py-3"
                >
                  <span className="block h-[5px] overflow-hidden rounded-full bg-hairline">
                    <span
                      className="block h-full rounded-full bg-[#188038]"
                      style={{
                        width: i < scene ? "100%" : i === scene ? `${Math.min(100, (shownT / s.duration) * 100)}%` : "0%",
                        transition: i === scene && running ? `width ${TICK}ms linear` : "none",
                      }}
                    />
                  </span>
                </button>
              ))}
            </div>
          </div>

          {/* On a phone the list sits below, out of sight: caption the step here. */}
          <div className="mt-3 min-h-[92px] rounded-2xl bg-[#F1F8F4] px-4 py-3 md:hidden" aria-live="polite">
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={scene}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.22 }}
              >
                <div className="text-[11px] font-bold uppercase tracking-wider text-[#137333]">
                  Step {scene + 1} of {STEPS.length}
                </div>
                <div className="mt-0.5 text-[15px] font-bold text-ink">{STEPS[scene].title}</div>
                <div className="mt-0.5 text-[13px] leading-relaxed text-smoke">{STEPS[scene].body}</div>
              </motion.div>
            </AnimatePresence>
          </div>
        </div>

        {/* the heading */}
        <div className="order-1 md:order-none md:col-start-2 md:row-start-1 md:self-end">
          <div className="inline-flex items-center gap-2 rounded-full bg-[#E6F4EA] px-3 py-1 text-[12px] font-bold text-[#137333]">
            <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden>
              <path d="M12 2.5l8 3.2v5.6c0 5.2-3.4 9-8 10.7-4.6-1.7-8-5.5-8-10.7V5.7z" fill="currentColor" />
              <path d="M8 12.2l2.7 2.7L16 9.6" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            Blocked by Play Protect? 30-second fix
          </div>
          <h3 className="mt-4 text-3xl font-extrabold leading-tight tracking-tight text-ink sm:text-[34px]">
            Pause Play Protect, install, done.
          </h3>
          <p className="mt-3 max-w-lg text-[15px] leading-relaxed text-smoke">
            Play Protect blocks apps from outside the Play Store that ask for accessibility, which is
            how Ek Aur sees a swipe. Pausing it lets the install through, and it switches itself back
            on the next day.
          </p>
        </div>

        {/* the steps */}
        <div className="order-3 md:order-none md:col-start-2 md:row-start-2">
          <ol className="space-y-1 md:mt-5">
            {STEPS.map((step, i) => {
              const active = i === scene;
              const done = i < scene;
              return (
                <li key={step.title}>
                  <button
                    type="button"
                    onClick={() => jump(i)}
                    className={
                      "flex w-full items-start gap-3.5 rounded-2xl px-3 py-2.5 text-left transition-colors " +
                      (active ? "bg-[#F1F8F4]" : "hover:bg-canvas")
                    }
                  >
                    <span
                      className={
                        "mt-[1px] grid h-7 w-7 shrink-0 place-items-center rounded-full text-[12px] font-bold transition-colors duration-300 " +
                        (active
                          ? "bg-[#188038] text-white shadow-[0_6px_16px_-6px_rgba(24,128,56,0.8)]"
                          : done
                            ? "bg-[#E6F4EA] text-[#137333]"
                            : "bg-canvas text-smoke ring-1 ring-hairline")
                      }
                    >
                      {done ? (
                        <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden>
                          <path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                      ) : (
                        i + 1
                      )}
                    </span>
                    <span className="min-w-0">
                      <span className={"block text-[15px] font-bold " + (active ? "text-ink" : "text-ink/80")}>
                        {step.title}
                      </span>
                      <motion.span
                        initial={false}
                        animate={{ height: active ? "auto" : 0, opacity: active ? 1 : 0 }}
                        transition={{ duration: 0.28, ease: [0.22, 1, 0.36, 1] }}
                        className="block overflow-hidden text-[13.5px] leading-relaxed text-smoke"
                      >
                        <span className="block pt-1">{step.body}</span>
                      </motion.span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ol>

          <p className="mt-6 rounded-2xl bg-canvas px-4 py-3 text-[13px] leading-relaxed text-smoke">
            <span className="font-bold text-ink">Is this safe?</span> While paused, Play Protect just
            skips checking apps from outside Google Play. Ek Aur can&apos;t read your screen, only the
            swipe. And your phone switches Play Protect back on by itself tomorrow.
          </p>
        </div>
      </div>
    </div>
  );
}
