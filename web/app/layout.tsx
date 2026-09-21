import type { Metadata, Viewport } from "next";
import { Poppins } from "next/font/google";
import "./globals.css";

const poppins = Poppins({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
  variable: "--font-poppins",
  display: "swap",
});

const description =
  "Ek Aur counts your Instagram Reels and cheers you on for one more — while the number quietly turns you in. A floating counter, an honest dashboard, a global leaderboard, and dry roasts at every milestone.";

export const metadata: Metadata = {
  title: "Ek Aur — one more reel counter",
  description,
  applicationName: "Ek Aur",
  keywords: ["Ek Aur", "Instagram Reels counter", "screen time", "doomscrolling", "Android"],
  openGraph: {
    title: "Ek Aur — one more",
    description,
    type: "website",
    siteName: "Ek Aur",
  },
  twitter: {
    card: "summary_large_image",
    title: "Ek Aur — one more",
    description,
  },
  icons: {
    icon: "/icon.svg",
    apple: "/icon.svg",
  },
};

export const viewport: Viewport = {
  themeColor: "#FBF7FB",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={poppins.variable}>
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
