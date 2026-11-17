"""Generate custom Arcane-Cosmic textures for the flowing wormhole tunnel, rings, and custom particle."""
from pathlib import Path
import math
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
MISC_DIR = ROOT / "src/main/resources/assets/universal_tool/textures/misc"
PARTICLE_DIR = ROOT / "src/main/resources/assets/universal_tool/textures/particle"
MISC_DIR.mkdir(parents=True, exist_ok=True)
PARTICLE_DIR.mkdir(parents=True, exist_ok=True)


def clamp(v: float, lo: float = 0.0, hi: float = 255.0) -> int:
    return int(max(lo, min(hi, round(v))))


def generate_tunnel_wall() -> None:
    w, h = 64, 128
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        v = y / h
        for x in range(w):
            u = x / w
            band1 = 0.5 + 0.5 * math.sin(2.0 * math.pi * (u * 6.0 + v * 2.0))
            band2 = 0.5 + 0.5 * math.sin(2.0 * math.pi * (u * 10.0 - v * 3.0 + 0.35))
            filament = math.pow(0.5 + 0.5 * math.sin(2.0 * math.pi * (u * 16.0 + v * 1.0)), 5.0)
            ridge = math.pow(band1 * 0.65 + band2 * 0.35, 2.0)

            r = 45.0 + 130.0 * ridge + 85.0 * filament
            g = 110.0 + 135.0 * band2 + 110.0 * filament
            b = 200.0 + 55.0 * band1 + 55.0 * filament
            alpha = 24.0 + 105.0 * ridge + 90.0 * filament
            px[x, y] = (clamp(r), clamp(g), clamp(b), clamp(alpha))
    img.save(MISC_DIR / "wormhole_tunnel_wall.png")


def generate_tunnel_ring() -> None:
    """Smooth continuous circular energy hoop texture (no diagonal tooth artifacts)."""
    w, h = 64, 32
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        v = y / (h - 1)  # 0 = leading edge, 1 = trailing edge
        # Bell curve concentrated near v = 0.35 with soft trailing fade
        if v < 0.35:
            profile = math.pow(math.sin((v / 0.35) * (math.pi * 0.5)), 1.4)
        else:
            profile = math.pow(max(0.0, 1.0 - (v - 0.35) / 0.65), 1.8)
        core = math.pow(profile, 2.6)

        for x in range(w):
            u = x / w
            # Gentle continuous harmonic shimmer around the hoop
            shimmer = 0.88 + 0.12 * math.sin(2.0 * math.pi * (u * 8.0))
            r = 120.0 + 135.0 * core
            g = 215.0 + 40.0 * core
            b = 255.0
            alpha = 245.0 * profile * shimmer
            px[x, y] = (clamp(r), clamp(g), clamp(b), clamp(alpha))
    img.save(MISC_DIR / "wormhole_tunnel_ring.png")


def generate_tunnel_streak() -> None:
    w, h = 32, 64
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        v = y / (h - 1)  # 0 = leading head, 1 = trailing tail
        if v < 0.14:
            longitudinal = math.sin((v / 0.14) * (math.pi * 0.5))
        else:
            longitudinal = math.pow(max(0.0, 1.0 - (v - 0.14) / 0.86), 1.75)

        width_scale = 0.50 + 0.50 * math.sin(min(1.0, v / 0.25) * math.pi * 0.5)
        for x in range(w):
            dx = abs((x + 0.5) / w - 0.5) * 2.0
            radial = max(0.0, 1.0 - (dx / max(0.22, width_scale)))
            core = math.pow(radial, 2.5) * longitudinal
            halo = math.pow(radial, 1.2) * longitudinal

            head_t = max(0.0, 1.0 - v * 2.4)
            r = 125.0 + 130.0 * head_t + 65.0 * (1.0 - head_t) * halo
            g = 180.0 + 75.0 * head_t + 40.0 * halo * (1.0 - v)
            b = 255.0
            alpha = min(255.0, (core * 235.0 + halo * 90.0))
            px[x, y] = (clamp(r), clamp(g), clamp(b), clamp(alpha))
    img.save(MISC_DIR / "wormhole_tunnel_streak.png")


def generate_wormhole_spark() -> None:
    size = 16
    img = Image.new("RGBA", (size, size))
    px = img.load()
    cx = (size - 1) * 0.5
    for y in range(size):
        for x in range(size):
            dx = abs(x - cx) / (size * 0.5)
            dy = abs(y - cx) / (size * 0.5)
            dist = math.hypot(dx, dy)
            cross = max(
                max(0.0, 1.0 - dx * 3.5) * max(0.0, 1.0 - dy * 1.15),
                max(0.0, 1.0 - dy * 3.5) * max(0.0, 1.0 - dx * 1.15),
            )
            glow = max(0.0, 1.0 - dist * 1.05)
            intensity = min(1.0, cross * 0.85 + math.pow(glow, 1.8) * 0.9)
            if intensity <= 0.02:
                px[x, y] = (0, 0, 0, 0)
            else:
                core_t = math.pow(intensity, 2.0)
                r = 155.0 + 100.0 * core_t
                g = 140.0 + 115.0 * core_t
                b = 255.0
                px[x, y] = (clamp(r), clamp(g), clamp(b), clamp(intensity * 255.0))
    img.save(PARTICLE_DIR / "wormhole_spark.png")


def generate_wormhole_exit_spark() -> None:
    """Warm solar-amber & rose-gold flare particle for destination exit wormholes."""
    size = 16
    img = Image.new("RGBA", (size, size))
    px = img.load()
    cx = (size - 1) * 0.5
    for y in range(size):
        for x in range(size):
            dx = abs(x - cx) / (size * 0.5)
            dy = abs(y - cx) / (size * 0.5)
            dist = math.hypot(dx, dy)
            # Diagonal + axial flare star
            diag1 = max(0.0, 1.0 - abs(dx - dy) * 3.0) * max(0.0, 1.0 - dist * 1.15)
            cross = max(
                max(0.0, 1.0 - dx * 3.8) * max(0.0, 1.0 - dy * 1.1),
                max(0.0, 1.0 - dy * 3.8) * max(0.0, 1.0 - dx * 1.1),
            )
            glow = max(0.0, 1.0 - dist * 1.05)
            intensity = min(1.0, max(cross, diag1 * 0.75) * 0.85 + math.pow(glow, 1.7) * 0.9)
            if intensity <= 0.02:
                px[x, y] = (0, 0, 0, 0)
            else:
                core_t = math.pow(intensity, 1.8)
                r = 255.0
                g = 125.0 + 130.0 * core_t
                b = 85.0 + 150.0 * (core_t * core_t)
                px[x, y] = (clamp(r), clamp(g), clamp(b), clamp(intensity * 255.0))
    img.save(PARTICLE_DIR / "wormhole_exit_spark.png")


if __name__ == "__main__":
    generate_tunnel_wall()
    generate_tunnel_ring()
    generate_tunnel_streak()
    generate_wormhole_spark()
    generate_wormhole_exit_spark()
    print("Generated custom wormhole tunnel, ring, streak, and spark textures.")
