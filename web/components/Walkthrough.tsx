"use client";

import { useEffect, useRef, useState } from "react";
import { AnimatePresence, motion, useReducedMotion } from "framer-motion";
import { Phone } from "./Phone";

/**
 * A step-by-step phone walkthrough: an animated phone on one side, the steps
 * on the other, kept in sync. Steps are clickable, the scrubber shows one
 * segment per step, it only runs while on screen, and with reduced motion
 * each step shows its telling moment, still.
 */

export type WalkScene = { id: string; duration: number; still: number };
export type WalkStep = { title: string; body: string };

export type WalkTheme = {
  /** Filled step number, progress, badge text. */
  accent: string;
  /** Active row and caption background. */
  soft: string;
  /** Badge background and "done" step background. */
  tint: string;
  /** A soft glow in the card's corner. */
  glow: string;
};

const TICK = 50;

export function Walkthrough({
  id,
  scenes,
  steps,
  theme,
  badge,
  title,
  intro,
  note,
  flip = false,
  children,
}: {
  id: string;
  scenes: WalkScene[];
  steps: WalkStep[];
  theme: WalkTheme;
  badge: React.ReactNode;
  title: string;
  intro: React.ReactNode;
  note?: React.ReactNode;
  /** Phone on the right on desktop. */
  flip?: boolean;
  /** Renders the phone screen for a scene at a time. */
  children: (scene: number, t: number) => React.ReactNode;
}) {
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
    const timer = setInterval(() => {
      setT((prev) => {
        const next = prev + TICK;
        if (next < scenes[scene].duration) return next;
        setScene((s) => (s + 1) % scenes.length);
        return 0;
      });
    }, TICK);
    return () => clearInterval(timer);
  }, [running, scene, scenes]);

  function jump(i: number) {
    setScene(i);
    setT(reduced ? scenes[i].still : 0);
  }

  const shownT = reduced ? scenes[scene].still : t;
  const phoneCol = flip ? "md:col-start-2" : "md:col-start-1";
  const textCol = flip ? "md:col-start-1" : "md:col-start-2";
  const cols = flip ? "md:grid-cols-[1fr_minmax(0,300px)]" : "md:grid-cols-[minmax(0,300px)_1fr]";

  return (
    <div
      ref={box}
      id={id}
      className="relative scroll-mt-24 overflow-hidden rounded-[32px] border border-hairline bg-white shadow-card"
    >
      <div
        className={"pointer-events-none absolute -top-24 h-72 w-72 rounded-full blur-[80px] " + (flip ? "-right-20" : "-left-20")}
        style={{ background: theme.glow }}
      />
      <div
        className={"pointer-events-none absolute bottom-0 h-72 w-72 rounded-full bg-g3/10 blur-[90px] " + (flip ? "-left-16" : "-right-16")}
      />

      <div className={"relative grid gap-8 p-6 sm:p-10 md:grid-rows-[auto_1fr] md:gap-x-14 md:gap-y-2 " + cols}>
        {/* the phone: beside the steps on desktop, between heading and steps on a phone */}
        <div className={"order-2 mx-auto w-full max-w-[300px] md:order-none md:row-span-2 md:row-start-1 md:self-center " + phoneCol}>
          <Phone screenClassName="bg-white" className="max-w-[300px]">
            {children(scene, shownT)}
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
              {scenes.map((s, i) => (
                <button
                  key={s.id}
                  type="button"
                  onClick={() => jump(i)}
                  aria-label={`Step ${i + 1}`}
                  className="h-8 flex-1 py-3"
                >
                  <span className="block h-[5px] overflow-hidden rounded-full bg-hairline">
                    <span
                      className="block h-full rounded-full"
                      style={{
                        background: theme.accent,
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
          <div className="mt-3 min-h-[92px] rounded-2xl px-4 py-3 md:hidden" style={{ background: theme.soft }} aria-live="polite">
            <AnimatePresence mode="wait" initial={false}>
              <motion.div
                key={scene}
                initial={{ opacity: 0, y: 6 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -6 }}
                transition={{ duration: 0.22 }}
              >
                <div className="text-[11px] font-bold uppercase tracking-wider" style={{ color: theme.accent }}>
                  Step {scene + 1} of {steps.length}
                </div>
                <div className="mt-0.5 text-[15px] font-bold text-ink">{steps[scene].title}</div>
                <div className="mt-0.5 text-[13px] leading-relaxed text-smoke">{steps[scene].body}</div>
              </motion.div>
            </AnimatePresence>
          </div>
        </div>

        {/* the heading */}
        <div className={"order-1 md:order-none md:row-start-1 md:self-end " + textCol}>
          <div
            className="inline-flex items-center gap-2 rounded-full px-3 py-1 text-[12px] font-bold"
            style={{ background: theme.tint, color: theme.accent }}
          >
            {badge}
          </div>
          <h3 className="mt-4 text-3xl font-extrabold leading-tight tracking-tight text-ink sm:text-[34px]">{title}</h3>
          <p className="mt-3 max-w-lg text-[15px] leading-relaxed text-smoke">{intro}</p>
        </div>

        {/* the steps */}
        <div className={"order-3 md:order-none md:row-start-2 " + textCol}>
          <ol className="space-y-1 md:mt-5">
            {steps.map((step, i) => {
              const active = i === scene;
              const done = i < scene;
              return (
                <li key={step.title}>
                  <button
                    type="button"
                    onClick={() => jump(i)}
                    className={
                      "flex w-full items-start gap-3.5 rounded-2xl px-3 py-2.5 text-left transition-colors " +
                      (active ? "" : "hover:bg-canvas")
                    }
                    style={active ? { background: theme.soft } : undefined}
                  >
                    <span
                      className={
                        "mt-[1px] grid h-7 w-7 shrink-0 place-items-center rounded-full text-[12px] font-bold transition-colors duration-300 " +
                        (active || done ? "" : "bg-canvas text-smoke ring-1 ring-hairline")
                      }
                      style={
                        active
                          ? { background: theme.accent, color: "#fff", boxShadow: `0 6px 16px -6px ${theme.accent}` }
                          : done
                            ? { background: theme.tint, color: theme.accent }
                            : undefined
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

          {note && (
            <div className="mt-6 rounded-2xl bg-canvas px-4 py-3 text-[13px] leading-relaxed text-smoke">{note}</div>
          )}
        </div>
      </div>
    </div>
  );
}
