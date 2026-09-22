import { Reveal } from "./Reveal";

const EMAIL = "hakkanparbej@gmail.com";

// A pre-filled composer: opens Gmail (or whatever mail app is default) addressed
// to the developer, so feedback is one tap away.
const SUBJECT = "Ek Aur — feedback / idea / bug";
const BODY = `Hey Hakkan,

(a bug, a feature you want, or just to say the leaderboard ruined your sleep schedule)

— via ek-aur.vercel.app`;
const MAILTO = `mailto:${EMAIL}?subject=${encodeURIComponent(SUBJECT)}&body=${encodeURIComponent(BODY)}`;

function MailIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <path d="m3.5 7 8.5 6 8.5-6" />
    </svg>
  );
}

export function Contact() {
  return (
    <section id="contact" className="mx-auto max-w-3xl px-5 pb-20 md:pb-24">
      <Reveal>
        <div className="grad-border rounded-card bg-white p-8 text-center shadow-card sm:p-10">
          <div className="ig-gradient mx-auto grid h-12 w-12 place-items-center rounded-2xl text-white shadow-soft">
            <MailIcon />
          </div>

          <h2 className="mt-5 text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">
            Got feedback, a bug, or a wild idea?
          </h2>
          <p className="mx-auto mt-3 max-w-md text-[15px] leading-relaxed text-smoke">
            Ek Aur is a hobby app made by one person — so real emails get real replies. Send a bug, a
            feature you want, or just tell me the leaderboard ruined your sleep schedule.
          </p>

          <a
            href={MAILTO}
            className="ig-gradient mt-7 inline-flex items-center gap-2.5 rounded-full px-6 py-3.5 text-[15px] font-semibold text-white shadow-pill transition-all duration-200 hover:-translate-y-0.5 hover:brightness-[1.05] active:scale-[0.97] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-acid/45 focus-visible:ring-offset-2 focus-visible:ring-offset-white"
          >
            <MailIcon />
            Email the developer
          </a>

          <p className="mt-4 text-xs text-ash">
            or reach out at{" "}
            <a href={`mailto:${EMAIL}`} className="font-semibold text-blue underline-offset-2 hover:underline">
              {EMAIL}
            </a>
          </p>
        </div>
      </Reveal>
    </section>
  );
}
