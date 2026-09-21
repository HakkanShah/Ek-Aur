import { Reveal } from "./Reveal";

export function Pitch() {
  return (
    <section className="mx-auto max-w-4xl px-5 py-14 text-center md:py-20">
      <Reveal>
        <p className="text-2xl font-semibold leading-snug tracking-tight text-ink sm:text-3xl">
          Most apps beg you to stop.{" "}
          <span className="text-smoke">Ek Aur doesn&apos;t.</span> It hands you the number — out loud —
          and lets you decide whether that&apos;s a flex or a cry for help.
        </p>
      </Reveal>
    </section>
  );
}
