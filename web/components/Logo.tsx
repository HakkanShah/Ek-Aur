export function Logo({ size = 40, rounded = 26 }: { size?: number; rounded?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 108 108"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="igLogo" x1="0" y1="108" x2="108" y2="0" gradientUnits="userSpaceOnUse">
          <stop stopColor="#515BD4" />
          <stop offset="0.3" stopColor="#8134AF" />
          <stop offset="0.58" stopColor="#DD2A7B" />
          <stop offset="0.82" stopColor="#F58529" />
          <stop offset="1" stopColor="#FEDA77" />
        </linearGradient>
      </defs>
      <rect width="108" height="108" rx={rounded} fill="url(#igLogo)" />
      <g stroke="#FFFFFF" strokeWidth="7.5" strokeLinecap="round">
        <line x1="38" y1="39" x2="38" y2="69" />
        <line x1="49" y1="39" x2="49" y2="69" />
        <line x1="60" y1="39" x2="60" y2="69" />
        <line x1="71" y1="39" x2="71" y2="69" />
        <line x1="32" y1="72" x2="77" y2="36" />
      </g>
    </svg>
  );
}
