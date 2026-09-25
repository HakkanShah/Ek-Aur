"use client";

import { AnimatePresence, motion } from "framer-motion";
import { Logo } from "./Logo";
import { Pill } from "./Pill";
import { Bar, Finger, G, I, Icon, Scrim, Switch, ease, type Scene } from "./PlayProtectSim";

/**
 * The "Restricted setting" walkthrough, drawn after stock Android 13
 * Settings: the Accessibility list, Ek Aur's own switch page, the
 * "Restricted setting" dialog, App info and its ⋮ menu, the screen-lock
 * check, and Android's "full control" warning.
 */

export const RESTRICTED_SCENES: Scene[] = [
  {
    id: "list",
    duration: 2800,
    still: 2400,
    finger: [
      { at: 0, x: 60, y: 70, hide: true },
      { at: 400, x: 55, y: 55 },
      { at: 1100, x: 42, y: 29 },
      { at: 1800, x: 42, y: 26, tap: true },
    ],
  },
  {
    id: "restricted",
    duration: 4700,
    still: 2600,
    finger: [
      { at: 0, x: 60, y: 40, hide: true },
      { at: 300, x: 72, y: 34 },
      { at: 800, x: 86, y: 26.5 },
      { at: 1100, x: 86, y: 23.6, tap: true },
      { at: 1700, x: 72, y: 50 },
      { at: 2500, x: 79, y: 59 },
      { at: 3100, x: 79, y: 56.4, tap: true },
      { at: 3600, x: 86, y: 76, hide: true },
    ],
  },
  {
    id: "appinfo",
    duration: 2700,
    still: 2400,
    finger: [
      { at: 0, x: 60, y: 50, hide: true },
      { at: 400, x: 72, y: 30 },
      { at: 1100, x: 92, y: 10 },
      { at: 1700, x: 92, y: 7.2, tap: true },
    ],
  },
  {
    id: "allow",
    duration: 4600,
    still: 1250,
    finger: [
      { at: 0, x: 88, y: 9 },
      { at: 500, x: 60, y: 10 },
      { at: 1100, x: 60, y: 8.2, tap: true },
      { at: 1500, x: 56, y: 62, hide: true },
      { at: 1900, x: 50, y: 82 },
      { at: 2600, x: 50, y: 74.6, tap: true },
      { at: 3100, x: 62, y: 92, hide: true },
    ],
  },
  {
    id: "back",
    duration: 2600,
    still: 1800,
    finger: [
      { at: 0, x: 60, y: 60, hide: true },
      { at: 400, x: 74, y: 34 },
      { at: 1100, x: 86, y: 26.5 },
      { at: 1700, x: 86, y: 23.6, tap: true },
    ],
  },
  {
    id: "control",
    duration: 3500,
    still: 1800,
    finger: [
      { at: 0, x: 80, y: 30, hide: true },
      { at: 500, x: 62, y: 62 },
      { at: 1300, x: 50, y: 50 },
      { at: 2000, x: 50, y: 46.3, tap: true },
      { at: 2500, x: 70, y: 88, hide: true },
    ],
  },
  {
    id: "done",
    duration: 3400,
    still: 3000,
    finger: [{ at: 0, x: 70, y: 80, hide: true }],
  },
];

export function RestrictedSim({ scene, t }: { scene: number; t: number }) {
  const s = RESTRICTED_SCENES[scene];
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
          {s.id === "list" && <A11yList pressed={t >= 1800} />}
          {s.id === "restricted" && <RestrictedScene t={t} />}
          {s.id === "appinfo" && <AppInfo />}
          {s.id === "allow" && <AllowScene t={t} />}
          {s.id === "back" && <ServicePage on={false} />}
          {s.id === "control" && <ControlScene t={t} />}
          {s.id === "done" && <DoneScene t={t} />}
        </motion.div>
      </AnimatePresence>
      <Finger path={s.finger} t={t} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

const BG = "#F8F9FC";

function TopBar({ title, big = false, menu = false }: { title?: string; big?: boolean; menu?: boolean }) {
  return (
    <>
      <div className="absolute inset-x-0 top-[5.8%] flex items-center px-[5%]">
        <Icon d={I.back} size={14} color={G.text} />
        <span className="flex-1" />
        {menu && <Icon d={I.more} size={16} color={G.text} sw={3} />}
      </div>
      {title && (
        <div className={"absolute inset-x-[6%] top-[11%] leading-tight " + (big ? "text-[19px]" : "text-[15px]")}>{title}</div>
      )}
    </>
  );
}

function Header({ top, children }: { top: string; children: React.ReactNode }) {
  return (
    <div className="absolute inset-x-[6%] text-[8.5px] font-semibold" style={{ top, color: G.blue }}>
      {children}
    </div>
  );
}

function Row({ top, title, sub, icon, highlight = false }: { top: string; title: string; sub?: string; icon?: React.ReactNode; highlight?: boolean }) {
  return (
    <motion.div
      className="absolute inset-x-[3%] flex items-center gap-2.5 rounded-[12px] px-[3%] py-[2.2%]"
      style={{ top }}
      animate={{ backgroundColor: highlight ? "#DDE6F7" : "rgba(0,0,0,0)" }}
      transition={{ duration: 0.2 }}
    >
      {icon}
      <div className="leading-tight">
        <div className="text-[10.5px]">{title}</div>
        {sub && <div className="text-[8.5px]" style={{ color: G.sub }}>{sub}</div>}
      </div>
    </motion.div>
  );
}

function Dot({ color }: { color: string }) {
  return <span className="h-[20px] w-[20px] shrink-0 rounded-full" style={{ background: color }} />;
}

// ---------------------------------------------------------------------------
// 1 -- Settings > Accessibility
// ---------------------------------------------------------------------------

function A11yList({ pressed }: { pressed: boolean }) {
  return (
    <div className="absolute inset-0" style={{ background: BG }}>
      <Bar />
      <TopBar title="Accessibility" big />
      <Header top="20%">Downloaded apps</Header>
      <Row
        top="23%"
        title="Ek Aur (One More)"
        sub="Off"
        highlight={pressed}
        icon={<Logo size={20} rounded={30} />}
      />
      <Header top="35%">Screen readers</Header>
      <Row top="38%" title="TalkBack" sub="Off" icon={<Dot color="#E8EAED" />} />
      <Header top="47%">Display</Header>
      <Row top="50%" title="Display size and text" icon={<Dot color="#E8EAED" />} />
      <Row top="57%" title="Colour and motion" icon={<Dot color="#E8EAED" />} />
      <Header top="66%">Interaction controls</Header>
      <Row top="69%" title="Accessibility Menu" sub="Off" icon={<Dot color="#E8EAED" />} />
      <Row top="76.5%" title="Switch Access" sub="Off" icon={<Dot color="#E8EAED" />} />
    </div>
  );
}

// ---------------------------------------------------------------------------
// 2, 5, 7 -- Ek Aur's own page, with the main switch
// ---------------------------------------------------------------------------

function ServicePage({ on }: { on: boolean }) {
  return (
    <div className="absolute inset-0" style={{ background: BG }}>
      <Bar />
      <TopBar title="Ek Aur (One More)" big />
      <motion.div
        className="absolute inset-x-[4%] top-[19.5%] flex h-[8.2%] items-center rounded-[22px] pl-[5%] pr-[4%]"
        animate={{ backgroundColor: on ? "#D3E3FD" : "#E9EDF4" }}
        transition={{ duration: 0.3 }}
      >
        <span className="flex-1 text-[11px] font-medium">Use Ek Aur (One More)</span>
        <Switch on={on} />
      </motion.div>
      <div className="absolute inset-x-[6%] top-[31.5%] flex items-center gap-3">
        <div className="flex-1 leading-snug">
          <div className="text-[10.5px]">Ek Aur (One More) shortcut</div>
          <div className="text-[8.5px]" style={{ color: G.sub }}>Off</div>
        </div>
        <Switch on={false} />
      </div>
      <div className="absolute inset-x-[6%] top-[41%] h-px" style={{ background: G.line }} />
      <Header top="44.5%">About Ek Aur (One More)</Header>
      <div className="absolute inset-x-[6%] top-[48.5%] text-[9px] leading-[1.45]" style={{ color: G.sub }}>
        Only for counting Instagram Reels and YouTube Shorts. It sees the swipe, never your screen or
        anything else.
      </div>
    </div>
  );
}

function RestrictedScene({ t }: { t: number }) {
  const dialog = t >= 1400 && t < 3500;
  return (
    <div className="absolute inset-0">
      <ServicePage on={false} />
      <Scrim on={dialog} />
      <AnimatePresence>
        {dialog && (
          <motion.div
            key="restricted"
            className="absolute inset-x-[7%] top-[35%] z-30 rounded-[22px] bg-[#F3F6FC] px-[7%] pb-[4%] pt-[6%] shadow-[0_18px_50px_rgba(0,0,0,0.35)]"
            initial={{ opacity: 0, scale: 0.92 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            transition={{ duration: 0.26, ease }}
          >
            <div className="flex justify-center">
              <Icon d={["M6 11h12v10H6z", "M8.5 11V7.5a3.5 3.5 0 0 1 7 0V11"]} size={16} color={G.blue} />
            </div>
            <div className="mt-2 text-center text-[13px]">Restricted setting</div>
            <div className="mt-2 text-[9px] leading-[1.45]" style={{ color: G.sub }}>
              For your security, this setting is currently unavailable.
            </div>
            <div className="mt-1 text-[9px] font-medium" style={{ color: G.blue }}>Learn more</div>
            <div className="mt-[10px] flex justify-end text-[10px] font-medium" style={{ color: G.blue }}>
              <motion.span
                className="rounded-full px-3 py-1"
                animate={{ backgroundColor: t >= 3100 ? "#D3E3FD" : "rgba(0,0,0,0)" }}
              >
                OK
              </motion.span>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 3 & 4 -- App info, the ⋮ menu, the screen lock
// ---------------------------------------------------------------------------

function AppInfo() {
  const rows = ["Notifications", "Permissions", "Storage & cache", "Mobile data & Wi-Fi", "Screen time", "App battery usage"];
  return (
    <div className="absolute inset-0" style={{ background: BG }}>
      <Bar />
      <TopBar menu />
      <div className="absolute inset-x-0 top-[12.5%] flex flex-col items-center gap-2">
        <Logo size={44} rounded={30} />
        <div className="text-[14px]">Ek Aur (One More)</div>
      </div>
      <div className="absolute inset-x-[7%] top-[29%] flex justify-around text-[8.5px]" style={{ color: G.text }}>
        {[
          ["Open", "M7 17L17 7M9 7h8v8"],
          ["Uninstall", "M5 7h14M9 7V5h6v2M7 7l1 12h8l1-12"],
          ["Force stop", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 8v5M12 16h.01"],
        ].map(([label, d]) => (
          <div key={label} className="flex flex-col items-center gap-1">
            <span className="grid h-[30px] w-[30px] place-items-center rounded-full border" style={{ borderColor: "#C4C7C5" }}>
              <Icon d={d} size={12} color={G.blue} />
            </span>
            {label}
          </div>
        ))}
      </div>
      {rows.map((r, i) => (
        <div key={r} className="absolute inset-x-[7%] text-[10.5px]" style={{ top: `${44 + i * 7.5}%` }}>
          {r}
          <div className="text-[8.5px]" style={{ color: G.sub }}>
            {["No notifications", "No permissions requested", "1.2 MB used", "No data used", "0 minutes today", "0% since last full charge"][i]}
          </div>
        </div>
      ))}
    </div>
  );
}

function AllowScene({ t }: { t: number }) {
  const menu = t < 1500;
  const pressed = t >= 1100;
  const sheet = t >= 1500 && t < 3500;
  const verified = t >= 2700;
  return (
    <div className="absolute inset-0">
      <AppInfo />
      <AnimatePresence>
        {menu && (
          <motion.div
            key="menu"
            className="absolute right-[3%] top-[4.5%] z-30 w-[66%] origin-top-right rounded-[10px] bg-white py-[2%] shadow-[0_10px_30px_rgba(0,0,0,0.22)]"
            initial={{ opacity: 0, scale: 0.85 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.9 }}
            transition={{ duration: 0.2, ease }}
          >
            <motion.div
              className="px-[8%] py-[5%] text-[10.5px]"
              animate={{ backgroundColor: pressed ? "#E8EDF6" : "#FFFFFF" }}
            >
              Allow restricted settings
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <Scrim on={sheet} />
      <AnimatePresence>
        {sheet && (
          <motion.div
            key="lock"
            className="absolute inset-x-0 bottom-0 z-30 flex h-[42%] flex-col items-center rounded-t-[24px] bg-white px-[8%] pt-[6%]"
            initial={{ y: "100%" }}
            animate={{ y: 0 }}
            exit={{ y: "100%" }}
            transition={{ duration: 0.35, ease }}
          >
            <div className="text-[13px]">Verify it&apos;s you</div>
            <div className="mt-1 text-[9px]" style={{ color: G.sub }}>Use your fingerprint or screen lock</div>
            <motion.div
              className="mt-[10%] grid h-[52px] w-[52px] place-items-center rounded-full"
              animate={{ backgroundColor: verified ? "#E6F4EA" : "#E8F0FE" }}
            >
              {verified ? (
                <Icon d="M5 12.5l4.5 4.5L19 7.5" size={24} color="#188038" sw={2.6} />
              ) : (
                <Icon
                  d={[
                    "M8 11a4 4 0 0 1 8 0v2",
                    "M12 11v4.5",
                    "M6 14.5V11a6 6 0 0 1 12 0v3",
                    "M9.5 19c1-1.5 1-3 1-4",
                    "M14.5 18.5c.5-1.3.5-2.5.5-3.5",
                  ]}
                  size={26}
                  color={G.blue}
                />
              )}
            </motion.div>
            <div className="mt-3 text-[9px]" style={{ color: verified ? "#188038" : G.sub }}>
              {verified ? "Verified" : "Touch the fingerprint sensor"}
            </div>
            <div className="mt-auto pb-[8%] text-[9.5px] font-medium" style={{ color: G.blue }}>Use PIN</div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 6 -- "Allow full control?"
// ---------------------------------------------------------------------------

function ControlScene({ t }: { t: number }) {
  const dialog = t >= 200 && t < 2500;
  const on = t >= 2500;
  return (
    <div className="absolute inset-0">
      <ServicePage on={on} />
      <Scrim on={dialog} />
      <AnimatePresence>
        {dialog && (
          <motion.div
            key="control"
            className="absolute inset-x-[6%] top-[15%] z-30 rounded-[22px] bg-[#F3F6FC] px-[7%] pb-[3%] pt-[5%] shadow-[0_18px_50px_rgba(0,0,0,0.35)]"
            initial={{ opacity: 0, scale: 0.92 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0, scale: 0.95 }}
            transition={{ duration: 0.26, ease }}
          >
            <div className="flex justify-center">
              <Logo size={26} rounded={30} />
            </div>
            <div className="mt-2 text-center text-[11.5px] leading-snug">
              Allow Ek Aur (One More) to have full control of your device?
            </div>
            {[
              ["View and control screen", "It can read all content on the screen and display content over other apps."],
              ["View and perform actions", "It can track your interactions with an app or a hardware sensor, and interact with apps on your behalf."],
            ].map(([h, b]) => (
              <div key={h} className="mt-[7px] flex gap-2">
                <span className="mt-[2px] h-[10px] w-[10px] shrink-0 rounded-full" style={{ background: "#A8C7FA" }} />
                <div className="leading-[1.35]">
                  <div className="text-[9px] font-medium">{h}</div>
                  <div className="text-[8px]" style={{ color: G.sub }}>{b}</div>
                </div>
              </div>
            ))}
            <div className="mt-[9px] space-y-[3px] text-center text-[10px] font-medium" style={{ color: G.blue }}>
              <motion.div
                className="rounded-[10px] py-[6px]"
                animate={{ backgroundColor: t >= 2000 ? "#D3E3FD" : "rgba(0,0,0,0)" }}
              >
                Allow
              </motion.div>
              <div className="py-[6px]">Deny</div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// ---------------------------------------------------------------------------
// 7 -- on, and the counter shows up
// ---------------------------------------------------------------------------

function DoneScene({ t }: { t: number }) {
  return (
    <div className="absolute inset-0">
      <ServicePage on />
      <AnimatePresence>
        {t >= 600 && (
          <motion.div
            key="pill"
            className="absolute inset-x-0 top-[4.2%] z-30 flex justify-center"
            initial={{ opacity: 0, y: -16, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            transition={{ type: "spring", stiffness: 260, damping: 20 }}
          >
            <Pill count={0} className="scale-[0.8]" />
          </motion.div>
        )}
        {t >= 1100 && (
          <motion.div
            key="toast"
            className="absolute inset-x-0 top-[80%] z-30 flex justify-center"
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3 }}
          >
            <span className="flex items-center gap-2 rounded-full bg-[#2B2D31] px-4 py-2 text-[9.5px] text-white shadow-lg">
              <Logo size={14} rounded={30} />
              Ek Aur is on
            </span>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
