"use client";

import { RESTRICTED_SCENES, RestrictedSim } from "./RestrictedSim";
import { Walkthrough } from "./Walkthrough";

const STEPS = [
  {
    title: "Open Accessibility, tap Ek Aur",
    body: "Settings → Accessibility. It's under Downloaded apps (Installed services on realme and OPPO, Installed apps on Samsung).",
  },
  {
    title: "Tap the switch, press OK",
    body: "It says “Restricted setting”. That's expected, and this first tap is what unlocks the next step.",
  },
  {
    title: "Open Ek Aur's App info, tap ⋮",
    body: "Settings → Apps → Ek Aur (One More). The three dots are at the top right.",
  },
  {
    title: "Tap “Allow restricted settings”",
    body: "Confirm with your fingerprint or PIN. Not in the menu? Close Settings from Recents, then open App info again.",
  },
  {
    title: "Back to Accessibility → Ek Aur, switch on",
    body: "This time it sticks.",
  },
  {
    title: "Tap Allow",
    body: "Android's standard warning for every accessibility app. Ek Aur only uses it to see the swipe.",
  },
  {
    title: "Done. It's counting",
    body: "The counter shows up over Reels and Shorts. You never do this again, updates keep the permission.",
  },
];

export function RestrictedGuide() {
  return (
    <Walkthrough
      id="restricted-setting"
      flip
      scenes={RESTRICTED_SCENES}
      steps={STEPS}
      theme={{ accent: "#0B57D0", soft: "#EEF3FD", tint: "#D3E3FD", glow: "rgba(11,87,208,0.10)" }}
      badge={
        <>
          <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden>
            <path d="M6 11h12v10H6z" fill="currentColor" />
            <path d="M8.5 11V7.5a3.5 3.5 0 0 1 7 0V11" fill="none" stroke="currentColor" strokeWidth="2.2" />
          </svg>
          Most common after installing
        </>
      }
      title="“Restricted setting”? Tap once, allow, switch on."
      intro="Android 13 and newer lock the accessibility switch for apps that didn't come from the Play Store. The unlock is hidden in App info, and it only shows up after you've tried the switch once."
      note={
        <>
          <span className="font-bold text-ink">Can&apos;t find the ⋮ item?</span> It only appears{" "}
          <span className="font-bold text-ink">after</span> you&apos;ve tapped the switch once. Close
          Settings from Recents and open App info fresh. On Xiaomi it&apos;s Settings → Apps → Manage
          apps; on realme, OPPO, OnePlus and vivo, Settings → Apps → App management; on Samsung and
          Pixel, Settings → Apps.
        </>
      }
    >
      {(scene, t) => <RestrictedSim scene={scene} t={t} />}
    </Walkthrough>
  );
}
