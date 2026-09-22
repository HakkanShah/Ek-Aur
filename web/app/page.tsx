import { Nav } from "@/components/Nav";
import { Hero } from "@/components/Hero";
import { Pitch } from "@/components/Pitch";
import { Features } from "@/components/Features";
import { MotionSection } from "@/components/MotionSection";
import { Install } from "@/components/Install";
import { DownloadCTA } from "@/components/DownloadCTA";
import { Footer } from "@/components/Footer";

export default function Home() {
  return (
    <div className="min-h-screen bg-canvas text-ink">
      <Nav />
      <main>
        <Hero />
        <Pitch />
        <Features />
        <MotionSection />
        <Install />
        <DownloadCTA />
      </main>
      <Footer />
    </div>
  );
}
