"use client";

import { PlayProtectSim, SCENES } from "./PlayProtectSim";
import { Walkthrough } from "./Walkthrough";

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

export function PlayProtectGuide() {
  return (
    <Walkthrough
      id="play-protect"
      scenes={SCENES}
      steps={STEPS}
      theme={{ accent: "#188038", soft: "#F1F8F4", tint: "#E6F4EA", glow: "rgba(52,168,83,0.10)" }}
      badge={
        <>
          <svg width="13" height="13" viewBox="0 0 24 24" aria-hidden>
            <path d="M12 2.5l8 3.2v5.6c0 5.2-3.4 9-8 10.7-4.6-1.7-8-5.5-8-10.7V5.7z" fill="currentColor" />
            <path d="M8 12.2l2.7 2.7L16 9.6" fill="none" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          Blocked by Play Protect? 30-second fix
        </>
      }
      title="Pause Play Protect, install, done."
      intro="Play Protect blocks apps from outside the Play Store that ask for accessibility, which is how Ek Aur sees a swipe. Pausing it lets the install through, and it switches itself back on the next day."
      note={
        <>
          <span className="font-bold text-ink">Is this safe?</span> While paused, Play Protect just
          skips checking apps from outside Google Play. Ek Aur can&apos;t read your screen, only the
          swipe. And your phone switches Play Protect back on by itself tomorrow.
        </>
      }
    >
      {(scene, t) => <PlayProtectSim scene={scene} t={t} />}
    </Walkthrough>
  );
}
