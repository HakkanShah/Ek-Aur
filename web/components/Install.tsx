import { Reveal } from "./Reveal";

function Step({
  n,
  title,
  children,
}: {
  n: number;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex gap-4">
      <div className="ig-gradient grid h-9 w-9 shrink-0 place-items-center rounded-full text-sm font-bold text-white shadow-soft">
        {n}
      </div>
      <div className="pt-1">
        <h4 className="text-[15px] font-bold text-ink">{title}</h4>
        <p className="mt-1 text-[14px] leading-relaxed text-smoke">{children}</p>
      </div>
    </div>
  );
}

export function Install() {
  return (
    <section id="install" className="mx-auto max-w-6xl scroll-mt-20 px-5 py-20 md:py-28">
      <div className="grid gap-12 md:grid-cols-2 md:gap-16">
        <Reveal>
          <div className="md:sticky md:top-24">
            <h2 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
              Installing it (the honest version)
            </h2>
            <p className="mt-4 max-w-md text-[15px] leading-relaxed text-smoke">
              Ek Aur isn't on the Play Store — it's a hobby app you sideload. So Android will throw a
              couple of scary-looking warnings on the way in. They're expected. Here's how to breeze
              past them.
            </p>

            {/* the honest "why" */}
            <div className="mt-6 rounded-card border border-hairline bg-lav/50 p-5">
              <div className="text-sm font-bold text-ink">Why the warnings?</div>
              <p className="mt-1.5 text-[13.5px] leading-relaxed text-smoke">
                Google flags anything sideloaded that can see the screen — not because Ek Aur does
                anything shady (it can't read your screen, only the swipe), but because it didn't come
                from the Play Store. Same reason a Play-installed app never gets flagged and this one
                does.
              </p>
            </div>

            <a
              href="#get"
              className="mt-8 hidden items-center gap-2 text-sm font-semibold text-blue underline-offset-4 hover:underline md:inline-flex"
            >
              Jump to download
              <span aria-hidden>↓</span>
            </a>
          </div>
        </Reveal>

        <Reveal delay={0.1}>
          <div className="space-y-6">
            {/* Play Protect — the big one, first */}
            <div className="rounded-card border-2 border-acid/25 bg-white p-6 shadow-card">
              <div className="mb-1 inline-flex items-center gap-2 rounded-full bg-blush px-2.5 py-1 text-[11px] font-bold text-acid">
                Most common
              </div>
              <h3 className="mt-2 text-lg font-bold text-ink">
                “App blocked” by Play Protect
              </h3>
              <p className="mt-1.5 text-[14px] leading-relaxed text-smoke">
                If Play Protect stops the install, don't panic — turn its scan off for a minute:
              </p>
              <ol className="mt-4 space-y-2 text-[14px] text-ink">
                {[
                  "Open the Play Store, tap your profile picture (top-right)",
                  "Play Protect → the ⚙ (settings) icon",
                  "Turn off “Scan apps with Play Protect”",
                  "Install Ek Aur — then turn scanning back on",
                ].map((s, i) => (
                  <li key={i} className="flex gap-3">
                    <span className="mt-[9px] h-1.5 w-1.5 shrink-0 rounded-full ig-gradient" />
                    {s}
                  </li>
                ))}
              </ol>
            </div>

            {/* the rest */}
            <div className="rounded-card border border-hairline bg-white p-6 shadow-card">
              <div className="space-y-6">
                <Step n={1} title="Allow “install unknown apps”">
                  When your browser or Files app asks, allow it to install. You only do this once per
                  app.
                </Step>
                <Step n={2} title="“App not installed”? It's a downgrade">
                  That just means a newer build is already on your phone. Grab the newest file — the
                  Download button up top always points to it.
                </Step>
                <Step n={3} title="After that, it updates itself">
                  Ek Aur checks this same release page and offers a one-tap update — you never chase
                  APKs again.
                </Step>
              </div>
            </div>
          </div>
        </Reveal>
      </div>
    </section>
  );
}
