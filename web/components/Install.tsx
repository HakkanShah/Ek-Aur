import { PlayProtectGuide } from "./PlayProtectGuide";
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
      <Reveal>
        <h2 className="text-3xl font-extrabold tracking-tight text-ink sm:text-4xl">
          Installing it (the honest version)
        </h2>
        <p className="mt-4 max-w-2xl text-[15px] leading-relaxed text-smoke">
          Ek Aur isn't on the Play Store. It's a hobby app you sideload, so Android shows a couple of
          scary-looking warnings on the way in. They're expected, and each one takes seconds.
        </p>
      </Reveal>

      <Reveal delay={0.05} className="mt-10">
        <PlayProtectGuide />
      </Reveal>

      <div className="mt-14 grid gap-12 md:grid-cols-2 md:gap-16">
        <Reveal>
          <div className="md:sticky md:top-24">
            <h3 className="text-2xl font-extrabold tracking-tight text-ink">Then two more things</h3>
            <p className="mt-3 max-w-md text-[15px] leading-relaxed text-smoke">
              Once it's installed, the app's guided setup walks you through the rest. Here's what to
              expect, in case you get stuck.
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
            {/* Restricted setting — the second wall, on the first switch */}
            <div className="rounded-card border-2 border-acid/25 bg-white p-6 shadow-card">
              <div className="mb-1 inline-flex items-center gap-2 rounded-full bg-blush px-2.5 py-1 text-[11px] font-bold text-acid">
                Most common after installing
              </div>
              <h3 className="mt-2 text-lg font-bold text-ink">
                “Restricted setting” on the accessibility switch
              </h3>
              <p className="mt-1.5 text-[14px] leading-relaxed text-smoke">
                Android 13+ blocks the accessibility switch for apps not from the Play Store. The app
                walks you through this on its first screen; here it is in full, in case you're stuck:
              </p>
              <ol className="mt-4 space-y-2 text-[14px] text-ink">
                {[
                  "Settings → Accessibility → Ek Aur → tap the switch. It won't turn on yet — press OK on the popup.",
                  "Open the app's App info page (Settings → Apps → Ek Aur) → ⋮ in the top-right → “Allow restricted settings”.",
                  "Back to Accessibility → Ek Aur → switch on. This time it sticks.",
                ].map((s, i) => (
                  <li key={i} className="flex gap-3">
                    <span className="mt-[9px] h-1.5 w-1.5 shrink-0 rounded-full ig-gradient" />
                    {s}
                  </li>
                ))}
              </ol>
              <div className="mt-4 rounded-2xl bg-lav/60 p-4 text-[13.5px] leading-relaxed text-ink">
                <span className="font-bold">Can't find the ⋮ menu item?</span> It only appears{" "}
                <span className="font-bold">after</span> you've tapped the switch once (step 1). Close
                Settings from Recents, tap the switch, press OK, then open App info fresh. On Xiaomi
                it's under Settings → Apps → Manage apps; on realme, OPPO, OnePlus and vivo it's
                under Settings → Apps → App management; on Samsung and Pixel, Settings → Apps.
              </div>
            </div>

            {/* the rest */}
            <div className="rounded-card border border-hairline bg-white p-6 shadow-card">
              <div className="space-y-6">
                <Step n={1} title="Allow “install unknown apps”">
                  When your browser or Files app asks, allow it to install. You only do this once per
                  app.
                </Step>
                <Step n={2} title="Then, two switches in the app">
                  Accessibility (how Reels and Shorts get counted) and Overlay (so the counter can float). The
                  app's guided setup takes you to each one and notices when it's on.
                </Step>
                <Step n={3} title="“App not installed”? It's a downgrade">
                  That just means a newer build is already on your phone. Grab the newest file — the
                  Download button up top always points to it.
                </Step>
                <Step n={4} title="After that, it updates itself">
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
