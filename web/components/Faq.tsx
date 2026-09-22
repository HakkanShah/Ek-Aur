import { Reveal } from "./Reveal";

/**
 * The questions people actually ask, answered plainly. Doubles as SEO/GEO fuel:
 * the same Q&A is emitted as FAQPage JSON-LD below, so search and AI answer
 * engines can quote it directly.
 */
export const FAQS: { q: string; a: string }[] = [
  {
    q: "What is Ek Aur?",
    a: "Ek Aur is a free Android app that counts your Instagram Reels with a little floating counter. It cheers you on for one more — while the number quietly turns you in. You get an honest dashboard, a global leaderboard, and a dry roast at every milestone. No lectures.",
  },
  {
    q: "Is Ek Aur free?",
    a: "Yes, completely free. No ads, no subscription, no account required to just count — you only pick a username if you want to appear on the global leaderboard.",
  },
  {
    q: "Does it read my screen or my messages?",
    a: "No. It can only see the swipe gesture that moves to the next reel — never the screen, your DMs, or what you're watching. The only thing that ever leaves your phone is your name and a daily total, and only if you join the leaderboard.",
  },
  {
    q: "Why isn't it on the Play Store?",
    a: "It's a hobby app you sideload directly. Android throws a couple of scary-looking warnings for anything not from the Play Store — they're expected, and the install guide walks you past Play Protect and the 'install unknown apps' prompt in a few taps.",
  },
  {
    q: "Will it interfere with my banking or UPI apps?",
    a: "No. Ek Aur switches itself off automatically the moment you leave Instagram, so payment apps that dislike accessibility services just work. You tap it back on when you want to scroll.",
  },
  {
    q: "How does the leaderboard work?",
    a: "Everyone who installs Ek Aur is on one global leaderboard, ranked on today's reels. There's nothing to join and nobody to add — pick a username and you're racing. You can hide yourself any time.",
  },
  {
    q: "Which Android versions are supported?",
    a: "Android 8.0 and newer. The app is about 14 MB and updates itself from within the app once installed.",
  },
];

function FaqItem({ q, a }: { q: string; a: string }) {
  return (
    <details className="group rounded-card border border-hairline bg-white p-5 shadow-sm transition-shadow open:shadow-card sm:p-6">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-4 text-[16px] font-bold text-ink marker:hidden">
        {q}
        <span
          aria-hidden
          className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-lav text-lg text-smoke transition-transform duration-200 group-open:rotate-45"
        >
          +
        </span>
      </summary>
      <p className="mt-3 text-[14.5px] leading-relaxed text-smoke">{a}</p>
    </details>
  );
}

export function Faq() {
  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "FAQPage",
    mainEntity: FAQS.map((f) => ({
      "@type": "Question",
      name: f.q,
      acceptedAnswer: { "@type": "Answer", text: f.a },
    })),
  };

  return (
    <section id="faq" className="mx-auto max-w-3xl scroll-mt-24 px-5 py-20 md:py-24">
      <Reveal>
        <h2 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
          Questions, answered
        </h2>
        <p className="mt-3 text-[15px] text-smoke">
          The stuff people actually ask before installing.
        </p>
      </Reveal>

      <div className="mt-10 space-y-3">
        {FAQS.map((f, i) => (
          <Reveal key={f.q} delay={i * 0.04}>
            <FaqItem q={f.q} a={f.a} />
          </Reveal>
        ))}
      </div>

      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
    </section>
  );
}
