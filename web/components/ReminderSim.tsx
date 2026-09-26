"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Logo } from "./Logo";
import { Pill } from "./Pill";
import { Bar, Finger, Switch, ease, type Scene } from "./PlayProtectSim";

/**
 * The scroll reminder, drawn after the app: set a number on Home, scroll, the
 * meme popup lands at the number, "10 More" pushes it back, "Take a break"
 * closes the app. The memes here are dancing emoji stickers with classic meme
 * text -- the app fills the same panel with a GIF.
 */

const SET_ON = 900;
const SLIDE_FROM = 1300;
const SLIDE_TO = 2500;
const PICK_TEN = 3100;

export const REMINDER_SCENES: Scene[] = [
  {
    id: "set",
    duration: 4200,
    still: 3600,
    finger: [
      { at: 0, x: 60, y: 70, hide: true },
      { at: 300, x: 80, y: 32 },
      { at: SET_ON - 100, x: 84, y: 25, tap: true },
      { at: SLIDE_FROM - 150, x: 10, y: 37.2 },
      { at: SLIDE_FROM, x: 10, y: 37.2, tap: true },
      { at: SLIDE_TO, x: 52.4, y: 37.2 },
      { at: PICK_TEN - 250, x: 20, y: 48.2 },
      { at: PICK_TEN, x: 20, y: 48.2, tap: true },
      { at: 3700, x: 40, y: 85, hide: true },
    ],
  },
  {
    id: "scroll",
    duration: 3600,
    still: 3300,
    finger: [
      { at: 0, x: 55, y: 80, hide: true },
      { at: 300, x: 55, y: 74 },
      { at: 600, x: 55, y: 40 },
      { at: 900, x: 55, y: 74 },
      { at: 1300, x: 55, y: 40 },
      { at: 1600, x: 55, y: 74 },
      { at: 2000, x: 55, y: 40 },
      { at: 2300, x: 55, y: 74 },
      { at: 2700, x: 55, y: 40 },
      { at: 3000, x: 60, y: 85, hide: true },
    ],
  },
  {
    id: "meme",
    duration: 3600,
    still: 2200,
    finger: [
      { at: 0, x: 60, y: 85, hide: true },
      { at: 1700, x: 70, y: 80 },
      { at: 2400, x: 77, y: 56.4 },
      { at: 2800, x: 77, y: 56.4, tap: true },
      { at: 3300, x: 76, y: 80, hide: true },
    ],
  },
  {
    id: "more",
    duration: 4300,
    still: 3700,
    finger: [
      { at: 0, x: 55, y: 80, hide: true },
      { at: 200, x: 55, y: 74 },
      { at: 450, x: 55, y: 42 },
      { at: 700, x: 55, y: 74 },
      { at: 950, x: 55, y: 42 },
      { at: 1200, x: 55, y: 74 },
      { at: 1450, x: 55, y: 42 },
      { at: 1700, x: 55, y: 74 },
      { at: 1950, x: 55, y: 42 },
      { at: 2300, x: 60, y: 85, hide: true },
    ],
  },
  {
    id: "break",
    duration: 4200,
    still: 3200,
    finger: [
      { at: 0, x: 60, y: 85, hide: true },
      { at: 300, x: 45, y: 75 },
      { at: 800, x: 36, y: 56.8 },
      { at: 1150, x: 36, y: 56.8, tap: true },
      { at: 1600, x: 45, y: 80, hide: true },
    ],
  },
];

export function ReminderSim({ scene, t }: { scene: number; t: number }) {
  const s = REMINDER_SCENES[scene];
  return (
    <div className="absolute inset-0 select-none font-sans">
      <AnimatePresence initial={false} mode="popLayout">
        <motion.div
          key={s.id}
          className="absolute inset-0"
          initial={{ opacity: 0, x: 24 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -24 }}
          transition={{ duration: 0.32, ease }}
        >
          {s.id === "set" && <SetScene t={t} />}
          {s.id === "scroll" && <ScrollScene t={t} />}
          {s.id === "meme" && <MemeScene t={t} />}
          {s.id === "more" && <MoreScene t={t} />}
          {s.id === "break" && <BreakScene t={t} />}
        </motion.div>
      </AnimatePresence>
      <Finger path={s.finger} t={t} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// 1 -- Home: switch the reminder on, pick 100, pick +10
// ---------------------------------------------------------------------------

const STOPS = [10, 15, 20, 25, 30, 40, 50, 60, 75, 100, 125, 150, 175, 200, 250, 300, 400, 500];

function SetScene({ t }: { t: number }) {
  const on = t >= SET_ON;
  const slide = Math.min(1, Math.max(0, (t - SLIDE_FROM) / (SLIDE_TO - SLIDE_FROM)));
  const eased = 1 - Math.pow(1 - slide, 3);
  const index = Math.round(eased * STOPS.indexOf(100));
  const at = STOPS[index];
  const fraction = index / (STOPS.length - 1);
  const snooze = t >= PICK_TEN ? 10 : 20;

  return (
    <div className="absolute inset-0 bg-[#FBF7FB]">
      <Bar />
      <div className="absolute inset-x-[6%] top-[8%] flex items-center justify-between">
        <span className="grad-text text-[15px] font-extrabold tracking-tight">EK AUR</span>
        <span className="grid h-6 w-6 place-items-center rounded-full bg-[#F3EEFB] text-[10px] font-bold text-[#8134AF]">H</span>
      </div>

      {/* the reminder card */}
      <div
        className="absolute inset-x-[5%] top-[20%] overflow-hidden rounded-[18px] bg-white px-[5%] pt-[5%] shadow-[0_10px_30px_-12px_rgba(28,28,30,0.25)] transition-[height] duration-500 ease-out"
        style={{ height: on ? "35.5%" : "12%" }}
      >
        <div className="flex items-center gap-2.5">
          <Ring progress={on ? 0.77 : 0} on={on} />
          <div className="min-w-0 flex-1 leading-tight">
            <div className="text-[11px] font-bold text-[#1C1C1E]">Scroll reminder</div>
            <div className="text-[8.5px] text-[#6F6F80]">
              {on ? `${Math.max(at - 77, 0)} to go · pops up at ${at}` : "Get a popup when you hit a number."}
            </div>
          </div>
          <Switch on={on} />
        </div>

        <div className="mt-[9%] flex items-end justify-between">
          <span className="text-[8.5px] font-semibold text-[#6F6F80]">Pop up at</span>
          <span className="flex items-baseline gap-1">
            <span className="grad-text text-[20px] font-extrabold leading-none tabular-nums">{at}</span>
            <span className="text-[8px] text-[#6F6F80]">reels</span>
          </span>
        </div>
        {/* slider */}
        <div className="relative mt-[4%] h-[14px]">
          <div className="absolute inset-x-0 top-1/2 h-[5px] -translate-y-1/2 rounded-full bg-[#F3EEFB]" />
          <div
            className="ig-gradient absolute left-0 top-1/2 h-[5px] -translate-y-1/2 rounded-full"
            style={{ width: `${fraction * 100}%` }}
          />
          <div
            className="absolute top-1/2 grid h-[14px] w-[14px] -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-white shadow-[0_1px_4px_rgba(0,0,0,0.25)]"
            style={{ left: `${fraction * 100}%` }}
          >
            <span className="ig-gradient block h-[6px] w-[6px] rounded-full" />
          </div>
        </div>
        <div className="mt-[1%] flex justify-between text-[7px] font-semibold text-[#B4B4C0]">
          <span>10</span>
          <span>500</span>
        </div>

        <div className="mt-[7%] text-[8.5px] font-semibold text-[#6F6F80]">Remind me later gives</div>
        <div className="mt-[3%] grid grid-cols-4 gap-1 rounded-full bg-[#F3EEFB] p-[3px] text-center text-[8.5px] font-bold">
          {[10, 20, 30, 50].map((n) => (
            <span
              key={n}
              className={
                "rounded-full py-[5px] transition-colors duration-300 " +
                (n === snooze ? "ig-gradient text-white" : "text-[#6F6F80]")
              }
            >
              +{n}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

function Ring({ progress, on }: { progress: number; on: boolean }) {
  const r = 14;
  const c = 2 * Math.PI * r;
  return (
    <div className="relative grid h-[34px] w-[34px] shrink-0 place-items-center">
      <svg viewBox="0 0 34 34" className="absolute inset-0 -rotate-90">
        <defs>
          <linearGradient id="rring" x1="0" x2="1" y1="0" y2="1">
            <stop offset="0" stopColor="#515BD4" />
            <stop offset="0.5" stopColor="#DD2A7B" />
            <stop offset="1" stopColor="#F58529" />
          </linearGradient>
        </defs>
        <circle cx="17" cy="17" r={r} fill="none" stroke="#F3EEFB" strokeWidth="3" />
        <motion.circle
          cx="17"
          cy="17"
          r={r}
          fill="none"
          stroke="url(#rring)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeDasharray={c}
          initial={false}
          animate={{ strokeDashoffset: c * (1 - progress) }}
          transition={{ duration: 0.8, ease }}
        />
      </svg>
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke={on ? "#DD2A7B" : "#6F6F80"} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
        <path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z" />
        <path d="M10 21h4" />
      </svg>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 2..5 -- the feed, the pill, the popup
// ---------------------------------------------------------------------------

const CLIPS = [
  { from: "#3a1c71", to: "#d76d77", user: "chai.memes", cap: "wait for it…" },
  { from: "#0f2027", to: "#2c5364", user: "cricket.clips", cap: "he really said that" },
  { from: "#833ab4", to: "#fd1d1d", user: "reels.daily", cap: "the algorithm knows" },
  { from: "#1a2980", to: "#26d0ce", user: "night.owl", cap: "POV: it's 2am" },
  { from: "#c31432", to: "#240b36", user: "food.wala", cap: "one more, promise" },
  { from: "#42275a", to: "#734b6d", user: "lofi.nights", cap: "3am thoughts" },
];

/** A reels feed that has scrolled [swipes] times, and the pill over it. */
function Feed({ swipes, count, dim = false }: { swipes: number; count: number; dim?: boolean }) {
  const clip = CLIPS[swipes % CLIPS.length];
  return (
    <div className="absolute inset-0 bg-black">
      <AnimatePresence initial={false}>
        <motion.div
          key={swipes}
          className="absolute inset-0"
          style={{ background: `linear-gradient(160deg, ${clip.from}, ${clip.to})` }}
          initial={{ y: "100%" }}
          animate={{ y: 0 }}
          exit={{ y: "-100%" }}
          transition={{ duration: 0.35, ease }}
        >
          <div className="absolute bottom-[7%] left-[6%] text-white">
            <div className="text-[10px] font-bold">@{clip.user}</div>
            <div className="text-[9px] opacity-80">{clip.cap}</div>
          </div>
          <div className="absolute bottom-[8%] right-[5%] flex flex-col gap-3">
            {[0, 1, 2].map((i) => (
              <span key={i} className="block h-7 w-7 rounded-full bg-white/15" />
            ))}
          </div>
        </motion.div>
      </AnimatePresence>
      <Bar dark />
      <div className="absolute inset-x-0 top-[5.5%] z-10 flex justify-center">
        <div className="origin-top scale-[0.72]">
          <Pill count={count} />
        </div>
      </div>
      <motion.div
        className="absolute inset-0 bg-black"
        initial={false}
        animate={{ opacity: dim ? 0.6 : 0 }}
        transition={{ duration: 0.25 }}
        style={{ pointerEvents: "none" }}
      />
    </div>
  );
}

/** Swipes so far, at the finger's rhythm: every [every] ms from [start]. */
function swipesAt(t: number, start: number, every: number, max: number) {
  if (t < start) return 0;
  return Math.min(max, Math.floor((t - start) / every) + 1);
}

function ScrollScene({ t }: { t: number }) {
  const n = swipesAt(t, 700, 700, 4);
  return <Feed swipes={n} count={96 + n} />;
}

const POP_AT = 250;

function MemeScene({ t }: { t: number }) {
  const up = t >= POP_AT && t < 2900;
  return (
    <div className="absolute inset-0">
      <Feed swipes={4} count={100} dim={up} />
      <AnimatePresence>
        {up && (
          <MemeCard
            key="m1"
            top="100 reels deep"
            bottom="Touch grass. It's free."
            sticker="🌱"
            minutes="1 h 12 min"
            pressed={t >= 2800 ? "more" : null}
          />
        )}
      </AnimatePresence>
    </div>
  );
}

const SECOND_POP = 2500;

function MoreScene({ t }: { t: number }) {
  // Ten fast swipes squeezed into four finger strokes: 100 -> 110.
  const strokes = swipesAt(t, 450, 500, 4);
  const count = t >= 2100 ? 110 : Math.min(110, 100 + Math.round(strokes * 2.5));
  const up = t >= SECOND_POP;
  return (
    <div className="absolute inset-0">
      <Feed swipes={4 + strokes} count={count} dim={up} />
      <AnimatePresence>
        {up && (
          <MemeCard
            key="m2"
            top="110 reels deep"
            bottom="Your thumb is filing a complaint"
            sticker="🥱"
            minutes="1 h 16 min"
            pressed={null}
          />
        )}
      </AnimatePresence>
    </div>
  );
}

function BreakScene({ t }: { t: number }) {
  const home = t >= 1350;
  return (
    <div className="absolute inset-0">
      <AnimatePresence initial={false}>
        {!home ? (
          <motion.div key="feed" className="absolute inset-0" exit={{ opacity: 0, scale: 0.9 }} transition={{ duration: 0.35, ease }}>
            <Feed swipes={8} count={110} dim />
            <MemeCard
              top="110 reels deep"
              bottom="Your thumb is filing a complaint"
              sticker="🥱"
              minutes="1 h 16 min"
              pressed={t >= 1150 ? "break" : null}
              still
            />
          </motion.div>
        ) : (
          <motion.div
            key="home"
            className="absolute inset-0"
            initial={{ opacity: 0, scale: 1.08 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.4, ease }}
          >
            <HomeScreen toast={t >= 1700} />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ---------------------------------------------------------------------------
// The popup and the home screen
// ---------------------------------------------------------------------------

function MemeCard({
  top,
  bottom,
  sticker,
  minutes,
  pressed,
  still = false,
}: {
  top: string;
  bottom: string;
  sticker: string;
  minutes: string;
  pressed: "more" | "break" | null;
  still?: boolean;
}) {
  return (
    <motion.div
      className="absolute inset-x-[5%] top-[27%] z-20 rounded-[18px] p-[1.5px]"
      style={{ background: "linear-gradient(135deg,#515BD4,#DD2A7B 55%,#F58529)", boxShadow: "0 18px 50px -10px rgba(221,42,123,0.55)" }}
      initial={still ? false : { opacity: 0, y: 40, rotate: -4, scale: 0.9 }}
      animate={{ opacity: 1, y: 0, rotate: 0, scale: 1 }}
      exit={{ opacity: 0, scale: 0.94 }}
      transition={{ type: "spring", stiffness: 260, damping: 20 }}
    >
      <div className="rounded-[16.5px] bg-[#111114] p-[4%]">
        {/* the meme */}
        <div className="relative h-[132px] overflow-hidden rounded-[12px]" style={{ background: "radial-gradient(circle at 50% 55%, #3A2150, #1E1E24 70%)" }}>
          <div className="absolute inset-0 grid place-items-center pb-3">
            <motion.span
              className="text-[46px] leading-none"
              animate={{ rotate: [-10, 10, -10], y: [0, -7, 0] }}
              transition={{ duration: 1.3, repeat: Infinity, ease: "easeInOut" }}
            >
              {sticker}
            </motion.span>
          </div>
          <MemeText className="top-[7%]">{top}</MemeText>
          <MemeText className="bottom-[7%]">{bottom}</MemeText>
        </div>
        <div className="mt-[3%] flex items-center justify-between px-[2%] text-[7.5px] font-semibold text-white/55">
          <span>⏱ {minutes} today</span>
        </div>
        <div className="mt-[3%] flex gap-[3%]">
          <motion.span
            className="ig-gradient grid h-[30px] flex-1 place-items-center rounded-full text-[10px] font-bold text-white"
            animate={{ scale: pressed === "break" ? 0.94 : 1 }}
          >
            Take a break
          </motion.span>
          <motion.span
            className="grid h-[30px] place-items-center rounded-full px-[5%] text-[10px] font-bold"
            style={{ background: "linear-gradient(#1b1b20,#1b1b20) padding-box, linear-gradient(135deg,#515BD4,#DD2A7B,#F58529) border-box", border: "1.5px solid transparent" }}
            animate={{ scale: pressed === "more" ? 0.94 : 1 }}
          >
            <span>
              <span className="grad-text">10</span> <span className="text-white">More</span>
            </span>
          </motion.span>
        </div>
        <div className="mt-[3%] flex items-center justify-center gap-2 pb-[1%] text-[8px] font-semibold text-white/45">
          <span>Not today</span>
          <span className="h-[3px] w-[3px] rounded-full bg-white/30" />
          <span>Turn off reminders</span>
        </div>
      </div>
    </motion.div>
  );
}

/** Classic meme text: heavy white capitals with a black outline. */
function MemeText({ children, className }: { children: string; className: string }) {
  return (
    <div
      className={"absolute inset-x-[4%] text-center text-[11.5px] font-black uppercase leading-[1.15] tracking-wide text-white " + className}
      style={{
        textShadow:
          "1.5px 0 0 #000, -1.5px 0 0 #000, 0 1.5px 0 #000, 0 -1.5px 0 #000, 1px 1px 0 #000, -1px -1px 0 #000, 1px -1px 0 #000, -1px 1px 0 #000",
      }}
    >
      {children}
    </div>
  );
}

function HomeScreen({ toast }: { toast: boolean }) {
  const apps = [
    ["#34A853", "Phone"], ["#1A73E8", "Messages"], ["#EA4335", "Camera"], ["#FBBC04", "Photos"],
    ["#5F6368", "Settings"], ["#0F9D58", "Maps"], ["#DB4437", "Music"], ["#4285F4", "Files"],
  ];
  return (
    <div className="absolute inset-0" style={{ background: "linear-gradient(170deg,#1e3c72,#2a5298 45%,#6dd5ed)" }}>
      <Bar dark />
      <div className="absolute inset-x-0 top-[14%] text-center text-white">
        <div className="text-[34px] font-light leading-none tracking-tight">8:33</div>
        <div className="mt-1 text-[9px] opacity-80">Saturday, 26 September</div>
      </div>
      <div className="absolute inset-x-[8%] top-[46%] grid grid-cols-4 gap-y-4 text-center">
        {apps.map(([c, n]) => (
          <div key={n} className="flex flex-col items-center gap-1">
            <span className="block h-[30px] w-[30px] rounded-[10px]" style={{ background: c }} />
            <span className="text-[7px] text-white/85">{n}</span>
          </div>
        ))}
      </div>
      <div className="absolute inset-x-[8%] bottom-[12%] flex justify-around">
        {["#34A853", "#1A73E8", "#EA4335", "#FBBC04"].map((c) => (
          <span key={c} className="block h-[30px] w-[30px] rounded-[10px] opacity-90" style={{ background: c }} />
        ))}
      </div>
      <AnimatePresence>
        {toast && (
          <motion.div
            key="toast"
            className="absolute inset-x-0 bottom-[25%] flex justify-center"
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
          >
            <span className="flex items-center gap-1.5 rounded-full bg-[#2B2D31] px-3 py-1.5 text-[8.5px] text-white shadow-lg">
              <Logo size={12} rounded={4} />
              Closed Instagram. Go touch grass. 🌱
            </span>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
