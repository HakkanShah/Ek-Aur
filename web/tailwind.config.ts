import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{ts,tsx}", "./components/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        canvas: "#FBF7FB",
        surface: "#FFFFFF",
        lav: "#F3EEFB",
        blush: "#FDEEF4",
        peach: "#FFF2E9",
        hairline: "#ECE7F2",
        ink: "#1C1C1E",
        smoke: "#8A8A99",
        ash: "#B4B4C0",
        acid: "#DD2A7B",
        blue: "#0095F6",
        good: "#22C55E",
        heat: "#ED4956",
        // Instagram gradient stops
        g1: "#515BD4",
        g2: "#8134AF",
        g3: "#DD2A7B",
        g4: "#F58529",
        g5: "#FEDA77",
        pill: "#0A0A0A",
      },
      fontFamily: {
        sans: ["var(--font-poppins)", "system-ui", "sans-serif"],
      },
      borderRadius: {
        card: "24px",
      },
      boxShadow: {
        card: "0 10px 40px -12px rgba(28,28,30,0.18)",
        soft: "0 8px 30px -10px rgba(129,52,175,0.20)",
        pill: "0 18px 50px -12px rgba(129,52,175,0.45)",
      },
      keyframes: {
        floaty: {
          "0%,100%": { transform: "translateY(0)" },
          "50%": { transform: "translateY(-10px)" },
        },
        drift: {
          "0%,100%": { transform: "translate(0,0) scale(1)" },
          "50%": { transform: "translate(3%,-4%) scale(1.08)" },
        },
      },
      animation: {
        floaty: "floaty 6s ease-in-out infinite",
        drift: "drift 18s ease-in-out infinite",
      },
    },
  },
  plugins: [],
};

export default config;
