import { DownloadButton } from "./DownloadButton";
import { Reveal } from "./Reveal";

export function DownloadCTA() {
  return (
    <section className="mx-auto max-w-6xl px-5 pb-24">
      <Reveal>
        <div className="ig-gradient relative overflow-hidden rounded-[36px] px-6 py-16 text-center shadow-pill sm:px-10 sm:py-20">
          {/* soft glow accents */}
          <div className="pointer-events-none absolute -left-10 -top-10 h-48 w-48 rounded-full bg-white/20 blur-3xl" />
          <div className="pointer-events-none absolute -bottom-12 -right-8 h-56 w-56 rounded-full bg-white/10 blur-3xl" />

          <div className="relative">
            <div className="text-sm font-bold uppercase tracking-[0.25em] text-white/80">
              Ek Aur · one more
            </div>
            <h2 className="mx-auto mt-4 max-w-xl text-4xl font-extrabold leading-tight text-white sm:text-5xl">
              One more won't hurt.
            </h2>
            <p className="mx-auto mt-4 max-w-md text-[15px] text-white/85">
              Get the counter that agrees with you — out loud — and let the number do the arguing.
            </p>
            <div className="mt-9 flex justify-center">
              <DownloadButton variant="white" subClassName="text-white/80" />
            </div>
          </div>
        </div>
      </Reveal>
    </section>
  );
}
