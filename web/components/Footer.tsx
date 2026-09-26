import { Logo } from "./Logo";

export function Footer() {
  return (
    <footer className="border-t border-hairline">
      <div className="mx-auto max-w-6xl px-5 py-12">
        <div className="flex flex-col items-start justify-between gap-8 sm:flex-row sm:items-center">
          <div className="flex items-center gap-3">
            <Logo size={34} rounded={10} />
            <div>
              <div className="grad-text text-base font-extrabold tracking-tight">Ek Aur</div>
              <div className="text-xs text-smoke">One More — for scrolling responsibly. Ironically.</div>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm font-medium text-ink/70">
            <a href="#see" className="hover:text-ink">See it</a>
            <a href="#install" className="hover:text-ink">Install</a>
            <a href="#faq" className="hover:text-ink">FAQ</a>
            <a href="#contact" className="hover:text-ink">Feedback</a>
            <a
              href="https://github.com/HakkanShah/Ek-Aur"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 hover:text-ink"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
                <path d="M12 2C6.48 2 2 6.58 2 12.25c0 4.53 2.87 8.37 6.85 9.73.5.1.68-.22.68-.49 0-.24-.01-.87-.01-1.71-2.79.62-3.38-1.37-3.38-1.37-.46-1.19-1.11-1.5-1.11-1.5-.91-.64.07-.62.07-.62 1 .07 1.53 1.06 1.53 1.06.9 1.58 2.36 1.12 2.94.86.09-.67.35-1.12.63-1.38-2.23-.26-4.57-1.14-4.57-5.06 0-1.12.39-2.03 1.03-2.75-.1-.26-.45-1.3.1-2.71 0 0 .84-.28 2.75 1.05a9.4 9.4 0 0 1 5 0c1.91-1.33 2.75-1.05 2.75-1.05.55 1.41.2 2.45.1 2.71.64.72 1.03 1.63 1.03 2.75 0 3.93-2.35 4.79-4.59 5.05.36.32.68.94.68 1.9 0 1.37-.01 2.47-.01 2.81 0 .27.18.6.69.49A10.02 10.02 0 0 0 22 12.25C22 6.58 17.52 2 12 2z" />
              </svg>
              GitHub
            </a>
          </div>
        </div>

        <div className="mt-10 flex flex-col gap-4 border-t border-hairline pt-6 sm:flex-row sm:items-center sm:justify-between">
          <p className="max-w-lg text-xs text-ash">
            Only your name and daily total ever leave the phone (plus a meme request to GIPHY, if
            reminders are on). Everything else stays on it. Not
            affiliated with Instagram or Meta.
          </p>
          <p className="shrink-0 text-xs font-medium text-smoke">
            Developed by a Doomscroller —{" "}
            <a
              href="https://hakkan.is-a.dev"
              target="_blank"
              rel="noreferrer"
              className="grad-text font-bold underline-offset-4 hover:underline"
            >
              Hakkan
            </a>
          </p>
        </div>
      </div>
    </footer>
  );
}
