import type { Metadata, Viewport } from "next";
import { Poppins } from "next/font/google";
import "./globals.css";

const poppins = Poppins({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
  variable: "--font-poppins",
  display: "swap",
});

const SITE = "https://ek-aur.vercel.app";

const description =
  "Ek Aur counts your Instagram Reels with a floating counter that cheers you on for one more — while the number quietly turns you in. An honest dashboard, a global leaderboard, and a dry roast at every milestone. Free, on-device, Android.";

export const metadata: Metadata = {
  metadataBase: new URL(SITE),
  title: {
    default: "Ek Aur — the Instagram Reels counter that roasts you",
    template: "%s · Ek Aur",
  },
  description,
  applicationName: "Ek Aur",
  authors: [{ name: "Hakkan", url: "https://hakkan.is-a.dev" }],
  creator: "Hakkan",
  publisher: "Hakkan",
  category: "productivity",
  keywords: [
    "Ek Aur",
    "Instagram Reels counter",
    "reel counter app",
    "doomscrolling tracker",
    "screen time Android",
    "reels screen time",
    "count reels watched",
    "Android reel counter",
    "floating counter overlay",
    "one more reel",
  ],
  alternates: { canonical: SITE },
  openGraph: {
    type: "website",
    url: SITE,
    siteName: "Ek Aur",
    title: "Ek Aur — the Instagram Reels counter that roasts you",
    description,
    locale: "en_US",
    images: [
      {
        url: "/og.png",
        width: 1200,
        height: 630,
        alt: "Ek Aur — a floating counter for Instagram Reels that cheers you on for one more",
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    title: "Ek Aur — the Instagram Reels counter that roasts you",
    description,
    images: ["/og.png"],
  },
  icons: {
    icon: "/icon.svg",
    apple: "/icon.svg",
  },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true, "max-image-preview": "large" },
  },
};

export const viewport: Viewport = {
  themeColor: "#FBF7FB",
  width: "device-width",
  initialScale: 1,
};

// Structured data — helps search engines and AI/answer engines understand and
// cite the app (a free Android tool), not just index the page text.
const jsonLd = {
  "@context": "https://schema.org",
  "@graph": [
    {
      "@type": "WebSite",
      "@id": `${SITE}/#website`,
      url: SITE,
      name: "Ek Aur",
      description,
      inLanguage: "en",
    },
    {
      "@type": "SoftwareApplication",
      "@id": `${SITE}/#app`,
      name: "Ek Aur (One More)",
      applicationCategory: "UtilitiesApplication",
      operatingSystem: "Android 8.0+",
      url: SITE,
      downloadUrl: `${SITE}/api/download`,
      image: `${SITE}/og.png`,
      description,
      author: { "@type": "Person", name: "Hakkan", url: "https://hakkan.is-a.dev" },
      offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
      featureList: [
        "Floating on-screen counter for Instagram Reels",
        "Honest daily, weekly and all-time dashboard",
        "Global leaderboard ranked on today's reels",
        "Dry sarcastic roasts at each milestone",
        "Auto-off for payments; nothing but your name and daily total ever leaves the phone",
      ],
    },
  ],
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={poppins.variable}>
      <body className="font-sans antialiased">
        {children}
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
      </body>
    </html>
  );
}
