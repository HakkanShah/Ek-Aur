export function faceFor(count: number): string {
  if (count >= 500) return "☠️";
  if (count >= 300) return "💀";
  if (count >= 150) return "😈";
  if (count >= 75) return "😵‍💫";
  if (count >= 25) return "🌚";
  return "😎";
}

export function Pill({
  count,
  message,
  className = "",
}: {
  count: number | string;
  message?: string;
  className?: string;
}) {
  const n = typeof count === "number" ? count : parseInt(count, 10) || 0;
  return (
    <div
      className={
        "inline-flex max-w-full items-center gap-2 rounded-full border border-acid/30 bg-pill/95 px-3.5 py-1.5 shadow-pill backdrop-blur " +
        className
      }
    >
      <span className="text-[15px] leading-none">{faceFor(n)}</span>
      <span className="font-extrabold tabular-nums leading-none tracking-tight text-white">
        {count}
      </span>
      {message ? (
        <>
          <span className="text-white/25">·</span>
          <span className="truncate text-[12.5px] font-medium leading-tight text-white/90">
            {message}
          </span>
        </>
      ) : null}
    </div>
  );
}
