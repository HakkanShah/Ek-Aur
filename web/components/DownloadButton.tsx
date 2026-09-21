"use client";

import { useEffect, useRef, useState } from "react";

interface Latest {
  version: string | null;
  sizeMB: number | null;
  pageUrl: string;
}

function DownloadIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3v12m0 0 4-4m-4 4-4-4" />
      <path d="M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2" />
    </svg>
  );
}

function Spinner() {
  return (
    <svg className="animate-spin" width="18" height="18" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2.4" strokeOpacity="0.25" />
      <path d="M21 12a9 9 0 0 0-9-9" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}

type Phase = "idle" | "working" | "done";

export function DownloadButton({
  variant = "solid",
  className = "",
  subClassName = "text-smoke",
  label = "Download for Android",
}: {
  variant?: "solid" | "ghost" | "white";
  className?: string;
  subClassName?: string;
  label?: string;
}) {
  const [meta, setMeta] = useState<Latest | null>(null);
  const [phase, setPhase] = useState<Phase>("idle");
  const timers = useRef<ReturnType<typeof setTimeout>[]>([]);

  useEffect(() => {
    let alive = true;
    fetch("/api/latest")
      .then((r) => r.json())
      .then((d) => alive && setMeta(d))
      .catch(() => {});
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    return () => timers.current.forEach(clearTimeout);
  }, []);

  // Let the anchor's default navigation fire the actual download; we only drive
  // the visual feedback here.
  function handleClick() {
    timers.current.forEach(clearTimeout);
    timers.current = [];
    setPhase("working");
    timers.current.push(setTimeout(() => setPhase("done"), 1500));
    timers.current.push(setTimeout(() => setPhase("idle"), 4200));
  }

  const sub =
    phase === "working"
      ? "Fetching the latest build…"
      : phase === "done"
      ? "Check your notifications / Downloads folder"
      : meta?.version != null
      ? `v${meta.version}${meta.sizeMB ? ` · ${meta.sizeMB} MB` : ""} · Android 8+`
      : "Latest release · Android 8+";

  const base =
    "group inline-flex min-w-[230px] items-center justify-center gap-2.5 rounded-full px-6 py-3.5 text-[15px] font-semibold transition-transform duration-200 active:scale-[0.97] hover:-translate-y-0.5 ";
  const styles = {
    solid: "ig-gradient text-white shadow-pill",
    ghost: "grad-border bg-white text-ink",
    white: "bg-white text-ink shadow-pill",
  } as const;

  return (
    <div className={"flex flex-col items-center gap-2 " + className}>
      <a
        href="/api/download"
        onClick={handleClick}
        aria-live="polite"
        className={base + styles[variant] + (phase !== "idle" ? " pointer-events-none" : "")}
      >
        {phase === "working" ? (
          <>
            <Spinner />
            Starting download…
          </>
        ) : phase === "done" ? (
          <>
            <CheckIcon />
            Download started
          </>
        ) : (
          <>
            <DownloadIcon />
            {label}
          </>
        )}
      </a>
      <span className={"text-xs " + subClassName}>{sub}</span>
    </div>
  );
}
