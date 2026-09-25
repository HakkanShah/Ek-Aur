import { Nav } from "@/components/Nav";
import { Hero } from "@/components/Hero";
import { Pitch } from "@/components/Pitch";
import { Shorts } from "@/components/Shorts";
import { Features } from "@/components/Features";
import { MotionSection } from "@/components/MotionSection";
import { Install } from "@/components/Install";
import { Faq } from "@/components/Faq";
import { DownloadCTA } from "@/components/DownloadCTA";
import { Contact } from "@/components/Contact";
import { Footer } from "@/components/Footer";

export default function Home() {
  return (
    <div className="min-h-screen bg-canvas text-ink">
      <Nav />
      <main>
        <Hero />
        <Pitch />
        <Shorts />
        <Features />
        <MotionSection />
        <Install />
        <Faq />
        <DownloadCTA />
        <Contact />
      </main>
      <Footer />
    </div>
  );
}
