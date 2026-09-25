"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Logo } from "./Logo";

/**
 * The Play Protect walkthrough, drawn after real screenshots of the Play
 * Store (account menu, Play Protect, its settings, the "Pause Play Protect
 * instead?" dialog). Every screen is plain markup, so it stays sharp at any
 * size, and a finger shows each tap.
 *
 * The parent owns the clock: it passes the scene and the time into it, so the
 * step list beside the phone can jump to any step and stay in sync.
 */

export type Tap = { at: number; x: number; y: number; tap?: boolean; hide?: boolean };

export type Scene = {
  id: string;
  duration: number;
  /** Where the finger goes, in % of the screen, and when it taps. */
  finger: Tap[];
  /** The moment shown when motion is reduced. */
  still: number;
};

export const SCENES: Scene[] = [
  {
    id: "blocked",
    duration: 5200,
    still: 5000,
    finger: [
      { at: 0, x: 62, y: 40, hide: true },
      { at: 200, x: 55, y: 30 },
      { at: 750, x: 55, y: 21.5, tap: true },
      { at: 1300, x: 72, y: 70 },
      { at: 1850, x: 83, y: 60.5, tap: true },
      { at: 2300, x: 90, y: 92, hide: true },
    ],
  },
  {
    id: "store",
    duration: 2600,
    still: 2500,
    finger: [
      { at: 0, x: 55, y: 45 },
      { at: 700, x: 86, y: 14 },
      { at: 1500, x: 86, y: 10.3, tap: true },
    ],
  },
  {
    id: "menu",
    duration: 3000,
    still: 2200,
    finger: [
      { at: 0, x: 80, y: 20, hide: true },
      { at: 500, x: 55, y: 60 },
      { at: 1200, x: 42, y: 71 },
      { at: 1750, x: 42, y: 68.1, tap: true },
    ],
  },
  {
    id: "protect",
    duration: 2800,
    still: 2600,
    finger: [
      { at: 0, x: 50, y: 40, hide: true },
      { at: 500, x: 62, y: 30 },
      { at: 1100, x: 91, y: 13 },
      { at: 1700, x: 91, y: 8.8, tap: true },
    ],
  },
  {
    id: "settings",
    duration: 2300,
    still: 1100,
    finger: [
      { at: 0, x: 60, y: 45, hide: true },
      { at: 400, x: 70, y: 34 },
      { at: 1000, x: 87, y: 26 },
      { at: 1400, x: 87.5, y: 22.8, tap: true },
    ],
  },
  {
    id: "pause",
    duration: 4200,
    still: 1900,
    finger: [
      { at: 0, x: 87, y: 26, hide: true },
      { at: 500, x: 70, y: 44 },
      { at: 1400, x: 56, y: 54 },
      { at: 2200, x: 55, y: 51.2, tap: true },
      { at: 2700, x: 82, y: 70, hide: true },
    ],
  },
  {
    id: "install",
    duration: 6400,
    still: 5600,
    finger: [
      { at: 0, x: 62, y: 40, hide: true },
      { at: 200, x: 55, y: 30 },
      { at: 750, x: 55, y: 21.5, tap: true },
      { at: 1300, x: 72, y: 70 },
      { at: 1850, x: 83, y: 60.5, tap: true },
      { at: 2300, x: 60, y: 75, hide: true },
      { at: 3500, x: 70, y: 68 },
      { at: 4100, x: 83, y: 60.5, tap: true },
      { at: 4500, x: 88, y: 88, hide: true },
    ],
  },
];

// Google's own palette for these screens.
export const G = {
  blue: "#0B57D0",
  tonal: "#D3E3FD",
  green: "#188038",
  greenSoft: "#E6F4EA",
  menu: "#EDF0F9",
  text: "#1F1F1F",
  sub: "#444746",
  line: "#E1E3E1",
};

export const ease = [0.22, 1, 0.36, 1] as const;

export function PlayProtectSim({ scene, t }: { scene: number; t: number }) {
  const s = SCENES[scene];
  return (
    <div className="absolute inset-0 select-none" style={{ color: G.text, fontFamily: "Roboto, var(--font-poppins), system-ui, sans-serif" }}>
      <AnimatePresence initial={false} mode="popLayout">
        <motion.div
          key={s.id}
          className="absolute inset-0"
          initial={{ opacity: 0, x: 24 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: -24 }}
          transition={{ duration: 0.32, ease }}
        >
          {s.id === "blocked" && <InstallFlow t={t} blocked />}
          {s.id === "store" && <StoreHome />}
          {s.id === "menu" && <AccountMenu t={t} />}
          {s.id === "protect" && <ProtectHome />}
          {s.id === "settings" && <ProtectSettings scanOn />}
          {s.id === "pause" && <PauseScene t={t} />}
          {s.id === "install" && <InstallFlow t={t} />}
        </motion.div>
      </AnimatePresence>
      <Finger path={s.finger} t={t} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// The finger
// ---------------------------------------------------------------------------

export function Finger({ path, t }: { path: Tap[]; t: number }) {
  let current = path[0];
  for (const k of path) if (k.at <= t) current = k;
  const tapping = current.tap && t - current.at < 380;
  return (
    <motion.div
      className="pointer-events-none absolute z-50"
      initial={false}
      animate={{
        left: `${current.x}%`,
        top: `${current.y}%`,
        opacity: current.hide ? 0 : 1,
      }}
      transition={{ type: "spring", stiffness: 120, damping: 20, opacity: { duration: 0.25 } }}
      style={{ translateX: "-50%", translateY: "-50%" }}
    >
      <div className="relative grid place-items-center">
        {tapping && (
          <motion.span
            key={current.at}
            className="absolute rounded-full border-2 border-white/90"
            initial={{ width: 18, height: 18, opacity: 0.9 }}
            animate={{ width: 54, height: 54, opacity: 0 }}
            transition={{ duration: 0.5, ease: "easeOut" }}
          />
        )}
        <motion.span
          className="block h-[26px] w-[26px] rounded-full border-2 border-white bg-black/25 shadow-[0_6px_18px_rgba(0,0,0,0.35)] backdrop-blur-[1px]"
          animate={{ scale: tapping ? 0.78 : 1 }}
          transition={{ type: "spring", stiffness: 600, damping: 22 }}
        />
      </div>
    </motion.div>
  );
}

// ---------------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------------

export function Bar({ dark = false }: { dark?: boolean }) {
  return (
    <div
      className="absolute inset-x-0 top-0 flex items-center justify-between px-[7%] pt-[12px] text-[9.5px] font-medium"
      style={{ color: dark ? "#fff" : G.text }}
    >
      <span>12:12</span>
      <span className="flex items-center gap-1">
        <span className="tracking-tight">4G</span>
        <span className="inline-block h-[8px] w-[15px] rounded-[2.5px] border border-current opacity-70" />
      </span>
    </div>
  );
}

export function Icon({ d, size = 14, color = G.sub, fill = false, sw = 2 }: { d: string | string[]; size?: number; color?: string; fill?: boolean; sw?: number }) {
  const paths = Array.isArray(d) ? d : [d];
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden>
      {paths.map((p, i) => (
        <path
          key={i}
          d={p}
          fill={fill ? color : "none"}
          stroke={fill ? "none" : color}
          strokeWidth={sw}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      ))}
    </svg>
  );
}

export const I = {
  back: "M19 12H5M11 18l-6-6 6-6",
  close: ["M18 6L6 18", "M6 6l12 12"],
  gear: [
    "M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z",
    "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
  ],
  refresh: ["M20 12a8 8 0 1 1-2.35-5.65", "M20 4.5v5h-5"],
  grid: ["M4 4h16v16H4z", "M9 4v16M15 4v16M4 9h16M4 15h16"],
  bell: ["M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5h4"],
  card: ["M3 6h18v12H3z", "M3 10h18"],
  shieldPlay: ["M12 3l7 3v5c0 5-3.5 8.5-7 10-3.5-1.5-7-5-7-10V6z", "M10.5 9.5v5l4-2.5z"],
  flask: ["M9 3h6", "M10 3v6L4.5 19a1.5 1.5 0 0 0 1.3 2h12.4a1.5 1.5 0 0 0 1.3-2L14 9V3", "M7 15h10"],
  folder: "M3 6.5h6l2 2.5h10v10H3z",
  bookmark: "M7 3.5h10v17l-5-3.5-5 3.5z",
  mic: ["M12 3a3 3 0 0 0-3 3v5a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z", "M5.5 11a6.5 6.5 0 0 0 13 0", "M12 17.5V21"],
  search: ["M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14z", "M20.5 20.5L16 16"],
  file: ["M6 3h8l4 4v14H6z", "M14 3v4h4"],
  more: "M12 5.5h.01M12 12h.01M12 18.5h.01",
};

function Avatar({ size }: { size: number }) {
  return (
    <span
      className="grid shrink-0 place-items-center rounded-full font-semibold text-white"
      style={{ width: size, height: size, fontSize: size * 0.42, background: "linear-gradient(135deg,#F5B301,#F58529)" }}
    >
      J
    </span>
  );
}

export function Scrim({ on }: { on: boolean }) {
  return (
    <motion.div
      className="absolute inset-0 z-20 bg-black"
      initial={false}
      animate={{ opacity: on ? 0.42 : 0 }}
      transition={{ duration: 0.3 }}
      style={{ pointerEvents: "none" }}
    />
  );
}

/** The dialog Play Protect uses: shield header, title, text, stacked buttons. */
function ProtectDialog({
  title,
  children,
  buttons,
  pressed,
  top,
}: {
  title: string;
  children: React.ReactNode;
  buttons: string[];
  pressed?: string;
  top: string;
}) {
  return (
    <motion.div
      className="absolute inset-x-[7%] z-30 rounded-[18px] bg-white px-[6%] pb-[3%] pt-[5%] shadow-[0_18px_50px_rgba(0,0,0,0.35)]"
      style={{ top }}
      initial={{ opacity: 0, scale: 0.92 }}
      animate={{ opacity: 1, scale: 1 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ duration: 0.28, ease }}
    >
      <div className="flex items-center justify-center gap-1 text-[8.5px]" style={{ color: G.sub }}>
        <Icon d={I.shieldPlay} size={10} color={G.sub} sw={2.2} />
        Google Play Protect
      </div>
      <div className="mt-[6px] text-center text-[13px] leading-tight">{title}</div>
      <div className="mt-[7px] space-y-[6px] text-[8.5px] leading-[1.35]" style={{ color: G.sub }}>
        {children}
      </div>
      <div className="mt-[9px] space-y-[4px]">
        {buttons.map((b) => (
          <div
            key={b}
            className="grid h-[26px] place-items-center rounded-[9px] text-[9.5px] font-medium transition-colors"
            style={{
              background: b === "Close" ? "transparent" : pressed === b ? "#A8C7FA" : G.tonal,
              color: G.text,
            }}
          >
            {b}
          </div>
        ))}
      </div>
    </motion.div>
  );
}

export function Switch({ on }: { on: boolean }) {
  return (
    <span
      className="relative inline-flex h-[18px] w-[32px] shrink-0 items-center rounded-full transition-colors duration-300"
      style={{ background: on ? G.blue : "#E3E3E3", boxShadow: on ? "none" : "inset 0 0 0 1.5px #747775" }}
    >
      <motion.span
        className="absolute rounded-full"
        initial={false}
        animate={{ x: on ? 15.5 : 4, width: on ? 13 : 9, height: on ? 13 : 9, backgroundColor: on ? "#FFFFFF" : "#747775" }}
        transition={{ type: "spring", stiffness: 500, damping: 32 }}
      />
    </span>
  );
}

// ---------------------------------------------------------------------------
// 1 & 7 -- installing: blocked the first time, fine after the pause
// ---------------------------------------------------------------------------

function InstallFlow({ t, blocked = false }: { t: number; blocked?: boolean }) {
  const installer = t >= 1000 && t < (blocked ? 2300 : 2100);
  const blockedShown = blocked && t >= 2300;
  const installing = !blocked && t >= 2100 && t < 3300;
  const installed = !blocked && t >= 3300 && t < 5600;
  const opened = !blocked && t >= 5600;

  return (
    <div className="absolute inset-0 bg-white">
      <Bar />
      <div className="absolute inset-x-0 top-[7%] flex items-center gap-2 px-[6%]">
        <Icon d={I.back} size={14} color={G.text} />
        <span className="text-[13px]">Downloads</span>
        <span className="ml-auto"><Icon d={I.search} size={13} color={G.text} /></span>
      </div>
      <div className="absolute inset-x-0 top-[13%] px-[6%] text-[8.5px] font-medium" style={{ color: G.sub }}>
        Today
      </div>
      <motion.div
        className="absolute inset-x-[4%] top-[18.5%] flex items-center gap-2.5 rounded-[12px] px-[3%] py-[3%]"
        animate={{ backgroundColor: t >= 750 && t < 1100 ? "#E8EEF9" : "rgba(0,0,0,0)" }}
      >
        <span className="grid h-[28px] w-[28px] shrink-0 place-items-center rounded-[8px]" style={{ background: "#E7F2EC" }}>
          <Icon d={I.file} size={15} color={G.green} />
        </span>
        <div className="min-w-0 leading-tight">
          <div className="truncate text-[10px] font-medium">ekaur-v0.22.3-build53.apk</div>
          <div className="text-[8px]" style={{ color: G.sub }}>9.7 MB · APK · just now</div>
        </div>
      </motion.div>
      {[0, 1].map((i) => (
        <div key={i} className="absolute inset-x-[4%] flex items-center gap-2.5 px-[3%] opacity-50" style={{ top: `${29 + i * 8}%` }}>
          <span className="h-[28px] w-[28px] rounded-[8px] bg-[#F1F3F4]" />
          <div className="space-y-1">
            <div className="h-[6px] w-[90px] rounded bg-[#E8EAED]" />
            <div className="h-[5px] w-[54px] rounded bg-[#F1F3F4]" />
          </div>
        </div>
      ))}

      <Scrim on={installer || blockedShown || installing || installed} />

      <AnimatePresence>
        {(installer || installing || installed) && (
          <motion.div
            key="installer"
            className="absolute inset-x-[6%] top-[45%] z-30 rounded-[20px] bg-[#F3F6FC] px-[7%] pb-[4%] pt-[6%] shadow-[0_18px_50px_rgba(0,0,0,0.35)]"
            initial={{ opacity: 0, scale: 0.92 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            transition={{ duration: 0.26, ease }}
          >
            <div className="flex items-center gap-2">
              <Logo size={22} rounded={30} />
              <span className="text-[11px] font-medium">Ek Aur (One More)</span>
            </div>
            <div className="mt-[10px] min-h-[30px] text-[9.5px]" style={{ color: G.sub }}>
              {installer && "Do you want to install this app?"}
              {installing && (
                <div>
                  Installing…
                  <div className="mt-2 h-[3px] overflow-hidden rounded-full bg-[#D3E3FD]">
                    <motion.div
                      className="h-full rounded-full"
                      style={{ background: G.blue }}
                      initial={{ width: "8%" }}
                      animate={{ width: "100%" }}
                      transition={{ duration: 1.1, ease: "easeInOut" }}
                    />
                  </div>
                </div>
              )}
              {installed && "App installed."}
            </div>
            <div className="mt-[8px] flex justify-end gap-[14%] text-[10px] font-medium" style={{ color: G.blue }}>
              {installer && (<><span>Cancel</span><span>Install</span></>)}
              {installed && (<><span>Done</span><span>Open</span></>)}
              {installing && <span className="opacity-0">.</span>}
            </div>
          </motion.div>
        )}
        {blockedShown && (
          <ProtectDialog key="blocked" title="App blocked to protect your device" buttons={["OK"]} top="30%">
            <p>
              This app can request access to sensitive data. This can increase the risk of identity
              theft or financial fraud.
            </p>
          </ProtectDialog>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {opened && (
          <motion.div
            key="opened"
            className="absolute inset-0 z-40 grid place-items-center bg-[#FBF7FB]"
            initial={{ opacity: 0, scale: 1.04 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.4, ease }}
          >
            <div className="flex flex-col items-center gap-3">
              <motion.div initial={{ scale: 0.6 }} animate={{ scale: 1 }} transition={{ type: "spring", stiffness: 260, damping: 16 }}>
                <Logo size={64} rounded={30} />
              </motion.div>
              <div className="grad-text text-[20px] font-extrabold tracking-[0.12em]">EK AUR</div>
              <div className="text-[9px] font-semibold tracking-[0.3em] text-smoke">INSTALLED</div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 2 -- the Play Store, profile picture top right
// ---------------------------------------------------------------------------

function StoreHome() {
  const tiles = ["#FDE7E9", "#E6F4EA", "#E8F0FE", "#FEF7E0"];
  return (
    <div className="absolute inset-0 bg-white">
      <Bar />
      <div className="absolute inset-x-[4%] top-[6.6%] flex h-[7.4%] items-center gap-2 rounded-full bg-[#EEF1F7] pl-[4%] pr-[1.5%]">
        <Icon d={I.search} size={13} color={G.sub} />
        <span className="flex-1 text-[10px]" style={{ color: G.sub }}>Search apps & games</span>
        <Icon d={I.mic} size={12} color={G.sub} />
        <Avatar size={24} />
      </div>
      <div className="absolute inset-x-0 top-[16.5%] flex gap-[6%] border-b px-[5%] pb-[2%] text-[9px] font-medium" style={{ color: G.sub, borderColor: G.line }}>
        <span className="relative" style={{ color: G.green }}>
          For you
          <span className="absolute -bottom-[7px] left-0 right-0 h-[2.5px] rounded-full" style={{ background: G.green }} />
        </span>
        <span>Top charts</span>
        <span>Children</span>
        <span>Categories</span>
      </div>
      <div className="absolute inset-x-[5%] top-[24%] text-[11px] font-medium">Recommended for you</div>
      <div className="absolute inset-x-[5%] top-[28.5%] flex gap-[4%]">
        {tiles.map((c, i) => (
          <div key={i} className="w-[22%]">
            <div className="aspect-square rounded-[14px]" style={{ background: c }} />
            <div className="mt-1 h-[5px] w-[80%] rounded bg-[#E8EAED]" />
            <div className="mt-1 h-[4px] w-[50%] rounded bg-[#F1F3F4]" />
          </div>
        ))}
      </div>
      <div className="absolute inset-x-[5%] top-[46%] h-[22%] rounded-[16px]" style={{ background: "linear-gradient(135deg,#E8F0FE,#FCE8F3)" }} />
      <div className="absolute inset-x-[5%] top-[71%] space-y-[10px]">
        {[0, 1].map((i) => (
          <div key={i} className="flex items-center gap-2.5">
            <div className="h-[30px] w-[30px] rounded-[9px] bg-[#F1F3F4]" />
            <div className="space-y-1">
              <div className="h-[6px] w-[100px] rounded bg-[#E8EAED]" />
              <div className="h-[5px] w-[60px] rounded bg-[#F1F3F4]" />
            </div>
          </div>
        ))}
      </div>
      <div className="absolute inset-x-0 bottom-0 flex h-[9%] items-center justify-around border-t bg-[#F8FAFD] text-[8px]" style={{ color: G.sub, borderColor: G.line }}>
        {["Games", "Apps", "Search", "Books"].map((l) => (
          <span key={l} className={l === "Apps" ? "font-semibold" : ""} style={l === "Apps" ? { color: G.green } : undefined}>
            {l}
          </span>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 3 -- the account menu
// ---------------------------------------------------------------------------

// The account menu's list rows, in % of the screen: the finger aims at them.
const ROW_TOP = 45;
const ROW_STEP = 6.6;

function AccountMenu({ t }: { t: number }) {
  const rows: [string, string | string[], string?][] = [
    ["Manage apps and device", I.grid],
    ["Notifications and offers", I.bell, "2"],
    ["Payments and subscriptions", I.card],
    ["Play Protect", I.shieldPlay],
    ["Play Labs", I.flask],
    ["Library", I.folder],
    ["Play Pass", I.bookmark],
  ];
  const pressed = t >= 1750;
  return (
    <motion.div
      className="absolute inset-0"
      style={{ background: G.menu }}
      initial={{ y: "-6%", opacity: 0.6 }}
      animate={{ y: 0, opacity: 1 }}
      transition={{ duration: 0.35, ease }}
    >
      <Bar />
      <span className="absolute right-[6%] top-[6.5%]"><Icon d={I.close} size={14} color={G.text} /></span>
      <div className="absolute inset-x-[4%] top-[11.5%] flex items-center gap-3 rounded-[20px] bg-white px-[5%] py-[3.2%]">
        <Avatar size={34} />
        <div className="min-w-0 flex-1 leading-tight">
          <div className="text-[12px] font-medium">Johnny</div>
          <div className="truncate text-[8.5px]" style={{ color: G.sub }}>johnny@gmail.com</div>
        </div>
        <span className="grid h-[20px] w-[20px] place-items-center rounded-full bg-[#E2E7F4]">
          <Icon d="M6 9l6 6 6-6" size={10} color={G.text} />
        </span>
      </div>
      <div className="absolute inset-x-[4%] top-[25%] flex items-center gap-2.5 rounded-full bg-white px-[5%] py-[2.6%] text-[10px]">
        <span className="text-[12px] font-bold" style={{ background: "conic-gradient(#EA4335 0 25%,#FBBC05 0 45%,#34A853 0 70%,#4285F4 0)", WebkitBackgroundClip: "text", color: "transparent" }}>G</span>
        Manage your Google Account
      </div>
      <div className="absolute inset-x-[4%] top-[33.4%] rounded-[20px] bg-white px-[5%] py-[2.6%]">
        <div className="flex items-center justify-between text-[10px]">
          <span>Play Points Bronze</span>
          <span className="font-medium">67</span>
        </div>
        <div className="mt-[6px] h-[3px] rounded-full bg-[#E2E7F4]">
          <div className="h-full w-[8%] rounded-full bg-[#8D5524]" />
        </div>
      </div>
      {rows.map(([label, icon, badge], i) => {
        const isTarget = label === "Play Protect";
        const first = i === 0;
        const last = i === rows.length - 1;
        return (
          <motion.div
            key={label}
            className="absolute inset-x-[4%] flex items-center gap-3 px-[5%]"
            style={{
              top: `${ROW_TOP + i * ROW_STEP}%`,
              height: `${ROW_STEP - 0.4}%`,
              borderRadius: first ? "20px 20px 0 0" : last ? "0 0 20px 20px" : 0,
            }}
            animate={{ backgroundColor: isTarget && pressed ? "#DCE4F4" : "#FFFFFF" }}
            transition={{ duration: 0.2 }}
          >
            <Icon d={icon} size={13} color={G.text} />
            <span className="flex-1 text-[10px]">{label}</span>
            {badge && <span className="text-[9px]" style={{ color: G.sub }}>{badge}</span>}
          </motion.div>
        );
      })}
    </motion.div>
  );
}

// ---------------------------------------------------------------------------
// 4 -- Play Protect, settings gear top right
// ---------------------------------------------------------------------------

function ProtectHome() {
  const apps = ["#E07A5F", "linear-gradient(45deg,#F58529,#DD2A7B,#8134AF)", "#0B3F91", "#2AABEE", "#FF5A1F"];
  return (
    <div className="absolute inset-0 bg-white">
      <Bar />
      <div className="absolute inset-x-0 top-[7%] flex items-center px-[5%]">
        <Icon d={I.back} size={14} color={G.text} />
        <span className="ml-[10%] flex-1 text-[14px]">Play Protect</span>
        <span className="mr-[6%]"><Icon d={I.refresh} size={13} color={G.text} /></span>
        <Icon d={I.gear} size={14} color={G.text} sw={1.8} />
      </div>
      <div className="absolute inset-x-0 top-[16%] flex h-[30%] justify-center">
      <motion.div
        className="grid h-full place-items-center rounded-full"
        style={{ aspectRatio: "1", background: "#F1F8F4" }}
        initial={{ scale: 0.85, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", stiffness: 200, damping: 18 }}
      >
        <div className="grid h-[74%] w-[74%] place-items-center rounded-full" style={{ background: "#E3F2EA" }}>
          <svg width="44" height="44" viewBox="0 0 24 24" aria-hidden>
            <path d="M12 2.5l8 3.2v5.6c0 5.2-3.4 9-8 10.7-4.6-1.7-8-5.5-8-10.7V5.7z" fill="#138A5E" />
            <path d="M8 12.2l2.7 2.7L16 9.6" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
      </motion.div>
      </div>
      <div className="absolute inset-x-0 top-[48.5%] text-center text-[14px] font-medium">No harmful apps found</div>
      <div className="absolute inset-x-0 top-[53.6%] text-center text-[9.5px]" style={{ color: G.sub }}>Play Protect scanned yesterday</div>
      <div className="absolute left-1/2 top-[58.6%] grid h-[5%] w-[34%] -translate-x-1/2 place-items-center rounded-full border text-[10px] font-medium" style={{ borderColor: "#C4C7C5", color: G.blue }}>
        Scan
      </div>
      <div className="absolute inset-x-[6%] top-[69%] text-[11.5px] font-medium">Recently scanned apps</div>
      <div className="absolute inset-x-[6%] top-[74.5%] flex items-center gap-[4%]">
        {apps.map((c, i) => (
          <span key={i} className="h-[24px] w-[24px] rounded-[7px]" style={{ background: c }} />
        ))}
        <span className="text-[8px] leading-tight" style={{ color: G.sub }}>+78<br />more</span>
      </div>
      <div className="absolute inset-x-[6%] top-[82%] text-[9px]" style={{ color: G.sub }}>Apps scanned yesterday</div>
      <div className="absolute inset-x-[6%] top-[87%] h-px" style={{ background: G.line }} />
      <div className="absolute inset-x-[6%] top-[89.5%] text-[8.5px] leading-snug" style={{ color: G.sub }}>
        Play Protect regularly checks your apps and device for harmful behaviour.
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 5 & 6 -- settings, the switch, and the pause
// ---------------------------------------------------------------------------

function ProtectSettings({ scanOn }: { scanOn: boolean }) {
  return (
    <div className="absolute inset-0 bg-white">
      <Bar />
      <div className="absolute inset-x-0 top-[7%] flex items-center px-[5%]">
        <Icon d={I.back} size={14} color={G.text} />
        <span className="ml-[10%] text-[14px]">Play Protect settings</span>
      </div>
      <div className="absolute inset-x-[5%] top-[15.5%] text-[9px] font-medium" style={{ color: G.sub }}>General</div>
      <div className="absolute inset-x-[5%] top-[20%] flex items-center gap-3">
        <div className="flex-1 leading-snug">
          <div className="text-[10.5px]">Scan apps with Play Protect</div>
          <div className="text-[9px]" style={{ color: G.sub }}>Play Protect can scan this device and warn you about harmful apps</div>
        </div>
        <Switch on={scanOn} />
      </div>
      <div className="absolute inset-x-[5%] top-[32%] flex items-center gap-3">
        <div className="flex-1 leading-snug">
          <div className="text-[10.5px]">Improve harmful app detection</div>
          <div className="text-[9px]" style={{ color: G.sub }}>Send unknown apps to Google for better detection</div>
        </div>
        <Switch on />
      </div>
      <div className="absolute inset-x-[5%] top-[43.5%] h-px" style={{ background: G.line }} />
      <div className="absolute inset-x-[5%] top-[47%] text-[9px] font-medium" style={{ color: G.sub }}>App privacy</div>
      <div className="absolute inset-x-[5%] top-[51.5%] leading-snug">
        <div className="text-[10.5px]">Permissions for unused apps</div>
        <div className="text-[9px]" style={{ color: G.sub }}>Review permissions for apps that you haven&apos;t used in a few months</div>
      </div>
    </div>
  );
}

function PauseScene({ t }: { t: number }) {
  const dialog = t >= 150 && t < 2600;
  const pressed = t >= 2200 ? "Pause" : undefined;
  const paused = t >= 2600;
  return (
    <div className="absolute inset-0">
      <ProtectSettings scanOn={!paused} />
      <Scrim on={dialog} />
      <AnimatePresence>
        {dialog && (
          <ProtectDialog
            key="pause"
            title="Pause Play Protect instead?"
            buttons={["Pause", "Turn off", "Close"]}
            pressed={pressed}
            top="27%"
          >
            <p>
              When paused, Play Protect will no longer scan apps available outside of Google Play for
              malware. Play Protect will automatically turn on again the next day.
            </p>
            <p>Requests to pause or turn off Play Protect may be a scam.</p>
          </ProtectDialog>
        )}
      </AnimatePresence>
    </div>
  );
}
