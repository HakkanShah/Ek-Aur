"use client";

import { useEffect, useState } from "react";

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

  const sub =
    meta?.version != null
      ? `v${meta.version}${meta.sizeMB ? ` · ${meta.sizeMB} MB` : ""} · Android 8+`
      : "Latest release · Android 8+";

  const base =
    "group inline-flex items-center gap-2.5 rounded-full px-6 py-3.5 text-[15px] font-semibold transition-transform duration-200 active:scale-[0.97] hover:-translate-y-0.5 ";
  const styles = {
    solid: "ig-gradient text-white shadow-pill",
    ghost: "grad-border bg-white text-ink",
    white: "bg-white text-ink shadow-pill",
  } as const;

  return (
    <div className={"flex flex-col items-center gap-2 " + className}>
      <a href="/api/download" className={base + styles[variant]}>
        <DownloadIcon />
        {label}
      </a>
      <span className={"text-xs " + subClassName}>{sub}</span>
    </div>
  );
}
