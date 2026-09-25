/**
 * The same app badges the Android app uses: a film strip for Reels on the
 * Instagram colours, a phone with a play mark for Shorts on red. Plain shapes,
 * never either company's logo.
 */
export type AppKind = "reels" | "shorts";

const FILL: Record<AppKind, string> = {
  reels: "linear-gradient(135deg, #8134AF, #DD2A7B, #F58529)",
  shorts: "linear-gradient(135deg, #FF4E45, #D0001A)",
};

export function AppIcon({ app, size = 20, color = "currentColor" }: { app: AppKind; size?: number; color?: string }) {
  const stroke = { fill: "none", stroke: color, strokeWidth: 2, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };
  return app === "reels" ? (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden>
      <path d="M4 4h16v16H4z" {...stroke} />
      <path d="M8 4v16M16 4v16M4 9h4M4 15h4M16 9h4M16 15h4" {...stroke} />
    </svg>
  ) : (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden>
      <path d="M7 2.5h10v19H7z" {...stroke} />
      <path d="M10.8 18.8h2.4" {...stroke} />
      <path d="M10.5 8.8v5.4l4.4-2.7z" fill={color} />
    </svg>
  );
}

export function AppBadge({ app, size = 40, className = "" }: { app: AppKind; size?: number; className?: string }) {
  return (
    <span
      className={"inline-grid shrink-0 place-items-center " + className}
      style={{ width: size, height: size, borderRadius: size * 0.3, background: FILL[app] }}
      aria-label={app === "reels" ? "Reels" : "Shorts"}
      role="img"
    >
      <AppIcon app={app} size={Math.round(size * 0.58)} color="#fff" />
    </span>
  );
}
