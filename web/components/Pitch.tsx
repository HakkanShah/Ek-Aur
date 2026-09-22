import { Reveal } from "./Reveal";

export function Pitch() {
  return (
    <section className="mx-auto max-w-4xl px-5 py-16 text-center md:py-24">
      <Reveal>
        <span className="ig-gradient mx-auto mb-7 block h-1 w-12 rounded-full" />
        <p className="text-[26px] font-semibold leading-[1.25] tracking-tight text-ink sm:text-[34px]">
          Most apps beg you to stop.{" "}
          <span className="grad-text font-extrabold">Ek Aur doesn&apos;t.</span> It hands you the
          number — out loud — and lets you decide whether that&apos;s a flex or a cry for help.
        </p>
      </Reveal>
    </section>
  );
}
