"use client";

import { REMINDER_SCENES, ReminderSim } from "./ReminderSim";
import { Reveal } from "./Reveal";
import { Walkthrough } from "./Walkthrough";

const STEPS = [
  {
    title: "Pick your number",
    body: "Home → Scroll reminder. Slide to where you want the tap on the shoulder, and pick what “later” means: 10, 20, 30 or 50 more.",
  },
  {
    title: "Scroll like nothing happened",
    body: "The counter keeps ticking, Reels and Shorts together. The ring on Home fills as you get close.",
  },
  {
    title: "Hit it, get memed",
    body: "A “Doomscroll reminder” lands in the middle of the screen with your count and a fresh meme GIF every time.",
  },
  {
    title: "“10 More.” Fine.",
    body: "It steps aside and comes back ten later, with a new meme. It never nags on every reel.",
  },
  {
    title: "Or take the break",
    body: "Take a break closes Instagram or YouTube and drops you on the home screen. Not today quiets it till midnight.",
  },
];

export function Reminders() {
  return (
    <section id="reminders" className="mx-auto max-w-6xl scroll-mt-24 px-5 py-14 md:py-20">
      <Reveal>
        <Walkthrough
          id="reminder-demo"
          scenes={REMINDER_SCENES}
          steps={STEPS}
          theme={{ accent: "#DD2A7B", soft: "#FDEEF4", tint: "#FCE4EF", glow: "rgba(221,42,123,0.12)" }}
          badge={
            <>
              <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                <path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2h-15z" />
                <path d="M10 21h4" />
              </svg>
              New · Scroll reminders
            </>
          }
          title="A meme at your limit. Not a lecture."
          intro="Pick a number. When today's count hits it, a meme drops in over the feed. Take the break, or tap “10 More” and it'll be back. You set the limit, the app just keeps the joke going."
          note={
            <>
              <span className="font-bold text-ink">Where do the memes come from?</span> GIPHY. A few
              are fetched in the background while reminders are on, so the popup opens instantly,
              even offline. Nothing about you goes with the request.
            </>
          }
        >
          {(scene, t) => <ReminderSim scene={scene} t={t} />}
        </Walkthrough>
      </Reveal>
    </section>
  );
}
