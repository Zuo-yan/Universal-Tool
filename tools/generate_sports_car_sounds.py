"""Generate mono Ogg Vorbis sound effects for the Gulf GT sports car using NumPy + bundled ffmpeg."""

from __future__ import annotations

import subprocess
import tempfile
import wave
from pathlib import Path

import imageio_ffmpeg
import numpy as np

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "src/main/resources/assets/universal_tool/sounds/entity"
SAMPLE_RATE = 44100


def periodic_band_noise(n_samples: int, low_hz: float, high_hz: float, rng: np.random.Generator, slope: float = 0.0) -> np.ndarray:
    """Generate mathematically periodic band-limited noise via rFFT so t=0 and t=T wrap with zero seam."""
    freqs = np.fft.rfftfreq(n_samples, d=1.0 / SAMPLE_RATE)
    spec = np.zeros_like(freqs, dtype=np.complex128)
    mask = (freqs >= low_hz) & (freqs <= high_hz)
    phases = rng.uniform(0.0, 2.0 * np.pi, size=mask.sum())
    # Smooth cosine-tapered spectral envelope
    f_band = freqs[mask]
    norm_pos = (f_band - low_hz) / max(1.0, high_hz - low_hz)
    taper = np.sin(np.pi * norm_pos) ** 0.7
    if slope != 0.0:
        taper *= (f_band / max(1.0, low_hz)) ** slope
    spec[mask] = taper * np.exp(1j * phases)
    sig = np.fft.irfft(spec, n=n_samples)
    peak = np.max(np.abs(sig))
    return sig / peak if peak > 1e-9 else sig


def write_mono_ogg(samples: np.ndarray, out_path: Path, ffmpeg_exe: str) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    clipped = np.clip(samples, -0.98, 0.98)
    pcm16 = (clipped * 32767.0).astype(np.int16)

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = Path(tmp.name)

    try:
        with wave.open(str(wav_path), "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(SAMPLE_RATE)
            wf.writeframes(pcm16.tobytes())

        cmd = [
            ffmpeg_exe,
            "-y",
            "-v",
            "error",
            "-i",
            str(wav_path),
            "-ac",
            "1",
            "-ar",
            str(SAMPLE_RATE),
            "-c:a",
            "libvorbis",
            "-q:a",
            "5",
            str(out_path),
        ]
        subprocess.run(cmd, check=True)
    finally:
        wav_path.unlink(missing_ok=True)


def generate_engine() -> np.ndarray:
    """1.60s seamless GT V8 sports car engine loop (fundamental 75 Hz -> 120 exact cycles)."""
    duration = 1.60
    n = int(round(duration * SAMPLE_RATE))
    t = np.arange(n) / SAMPLE_RATE
    rng = np.random.default_rng(101)

    # Exact integer-cycle firing frequency (75 Hz = 120 cycles in 1.6s)
    f0 = 75.0
    # Subtle periodic engine lope (3.75 Hz = 6 cycles in 1.6s)
    lope = 1.0 + 0.08 * np.sin(2.0 * np.pi * 3.75 * t)

    # Harmonic stack: sub-firing rumble (0.5x), primary (1x), V8 burble (1.5x, 2x, 3x, 4x, 6x)
    harmonics = [
        (0.5, 0.30, 0.2),
        (1.0, 0.85, 0.0),
        (1.5, 0.42, 0.9),
        (2.0, 0.65, 0.4),
        (3.0, 0.36, 1.3),
        (4.0, 0.24, 0.7),
        (6.0, 0.12, 2.1),
        (8.0, 0.06, 1.6),
    ]
    core = np.zeros(n, dtype=np.float64)
    for mult, amp, phase in harmonics:
        core += amp * np.sin(2.0 * np.pi * (f0 * mult) * t + phase)

    # Gentle exhaust saturation + intake air texture
    saturated = np.tanh(core * 0.85 * lope)
    exhaust_rumble = periodic_band_noise(n, 70.0, 380.0, rng, slope=-0.4) * 0.22
    exhaust_hiss = periodic_band_noise(n, 450.0, 2200.0, rng, slope=-0.7) * 0.06

    mix = saturated * 0.78 + exhaust_rumble + exhaust_hiss
    mix -= np.mean(mix)
    mix /= np.max(np.abs(mix))
    return mix * 0.72


def generate_drift() -> np.ndarray:
    """1.20s seamless tire rubber friction screech loop."""
    duration = 1.20
    n = int(round(duration * SAMPLE_RATE))
    t = np.arange(n) / SAMPLE_RATE
    rng = np.random.default_rng(202)

    # Dual resonant rubber slip bands + asphalt scrub
    squeal_a = periodic_band_noise(n, 780.0, 1080.0, rng) * 0.55
    squeal_b = periodic_band_noise(n, 1280.0, 1680.0, rng) * 0.42
    scrub = periodic_band_noise(n, 320.0, 2600.0, rng, slope=-0.3) * 0.35

    # Smooth periodic tire slip modulation (2.5 Hz = 3 cycles, 5.0 Hz = 6 cycles)
    mod = 0.84 + 0.12 * np.sin(2.0 * np.pi * 2.5 * t) + 0.04 * np.sin(2.0 * np.pi * 5.0 * t + 1.1)
    mix = (squeal_a + squeal_b + scrub) * mod
    mix = np.tanh(mix * 1.1)
    mix -= np.mean(mix)
    mix /= np.max(np.abs(mix))
    return mix * 0.62


def generate_nitro() -> np.ndarray:
    """1.50s seamless twin-turbo + nitrous whoosh loop."""
    duration = 1.50
    n = int(round(duration * SAMPLE_RATE))
    t = np.arange(n) / SAMPLE_RATE
    rng = np.random.default_rng(303)

    # Jet airflow roar + sub thrust
    sub_thrust = periodic_band_noise(n, 55.0, 220.0, rng, slope=-0.2) * 0.42
    jet_whoosh = periodic_band_noise(n, 300.0, 4200.0, rng, slope=-0.45) * 0.65

    # Clean turbocharger whistle (exact integer cycles: 2100 Hz -> 3150 cycles, 3150 Hz -> 4725 cycles)
    whistle = (
        0.10 * np.sin(2.0 * np.pi * 2100.0 * t)
        + 0.05 * np.sin(2.0 * np.pi * 3150.0 * t + 0.7)
    ) * (0.85 + 0.15 * np.sin(2.0 * np.pi * 2.0 * t))

    # Gentle turbine airflow surge (2.0 Hz = 3 exact cycles)
    surge = 0.90 + 0.10 * np.sin(2.0 * np.pi * 2.0 * t)
    mix = (sub_thrust + jet_whoosh) * surge + whistle
    mix = np.tanh(mix * 0.95)
    mix -= np.mean(mix)
    mix /= np.max(np.abs(mix))
    return mix * 0.70


def generate_door() -> np.ndarray:
    """0.35s crisp mechanical + electronic sports car door latch sound."""
    duration = 0.35
    n = int(round(duration * SAMPLE_RATE))
    t = np.arange(n) / SAMPLE_RATE
    rng = np.random.default_rng(404)

    # Stage 1 (0.00s): Heavy precision door thump + mechanical click
    t1 = t
    freq1 = 44.0 + 75.0 * np.exp(-t1 / 0.018)
    phase1 = 2.0 * np.pi * np.cumsum(freq1) / SAMPLE_RATE
    thump = np.sin(phase1) * np.exp(-t1 / 0.045) * 0.70
    click1 = rng.standard_normal(n) * np.exp(-t1 / 0.006) * 0.30
    chassis_ring = np.sin(2.0 * np.pi * 260.0 * t1) * np.exp(-t1 / 0.024) * 0.28

    # Stage 2 (0.095s): Secondary metallic striker lock + subtle actuator chime
    t2 = np.maximum(0.0, t - 0.095)
    active2 = (t >= 0.095).astype(np.float64)
    lock_clack = (
        np.sin(2.0 * np.pi * 390.0 * t2) * np.exp(-t2 / 0.022) * 0.36
        + np.sin(2.0 * np.pi * 1240.0 * t2) * np.exp(-t2 / 0.012) * 0.22
        + rng.standard_normal(n) * np.exp(-t2 / 0.005) * 0.22
    ) * active2

    # Crisp fade-in (2ms) and tail fade-out (45ms)
    env = np.ones(n, dtype=np.float64)
    fade_in = int(0.002 * SAMPLE_RATE)
    fade_out = int(0.045 * SAMPLE_RATE)
    env[:fade_in] = np.linspace(0.0, 1.0, fade_in)
    env[-fade_out:] = np.linspace(1.0, 0.0, fade_out)

    mix = (thump + click1 + chassis_ring + lock_clack) * env
    mix /= np.max(np.abs(mix))
    return mix * 0.78


def generate_morph() -> np.ndarray:
    """0.55s crisp sci-fi + mechanical wheel retraction & hydrofoil deploy sound."""
    duration = 0.55
    n = int(round(duration * SAMPLE_RATE))
    t = np.arange(n) / SAMPLE_RATE
    rng = np.random.default_rng(505)

    # 1. Smooth hydraulic servo frequency sweep (145 Hz -> 510 Hz -> 380 Hz) with gentle 2x overtone
    servo_freq = 145.0 + 365.0 * np.sin(np.pi * np.minimum(1.0, t / 0.36)) ** 1.2
    servo_phase = 2.0 * np.pi * np.cumsum(servo_freq) / SAMPLE_RATE
    servo_env = np.sin(np.pi * np.minimum(1.0, t / 0.42)) ** 0.8 * (t < 0.44)
    servo = (0.55 * np.sin(servo_phase) + 0.20 * np.sin(2.0 * servo_phase + 0.4)) * servo_env

    # 2. Mechanical latch clacks (wheel fold at 0.24s + hydrofoil lock at 0.38s)
    t_latch1 = np.maximum(0.0, t - 0.24)
    active1 = (t >= 0.24).astype(np.float64)
    latch1 = (
        0.38 * np.sin(2.0 * np.pi * 540.0 * t_latch1) * np.exp(-t_latch1 / 0.018)
        + 0.24 * np.sin(2.0 * np.pi * 1360.0 * t_latch1) * np.exp(-t_latch1 / 0.010)
        + 0.20 * rng.standard_normal(n) * np.exp(-t_latch1 / 0.005)
    ) * active1

    t_latch2 = np.maximum(0.0, t - 0.38)
    active2 = (t >= 0.38).astype(np.float64)
    latch2 = (
        0.42 * np.sin(2.0 * np.pi * 420.0 * t_latch2) * np.exp(-t_latch2 / 0.022)
        + 0.28 * np.sin(2.0 * np.pi * 1680.0 * t_latch2) * np.exp(-t_latch2 / 0.012)
        + 0.18 * rng.standard_normal(n) * np.exp(-t_latch2 / 0.006)
    ) * active2

    # 3. Pneumatic air-cushion startup shimmer (band-limited airflow swelling at 0.18s..0.52s)
    air_noise = periodic_band_noise(n, 750.0, 3400.0, rng, slope=-0.35)
    air_env = np.exp(-((t - 0.34) / 0.11) ** 2) * 0.32
    air_cushion = air_noise * air_env

    # Smooth fade-in (3ms) and tail fade-out (55ms)
    env = np.ones(n, dtype=np.float64)
    fade_in = int(0.003 * SAMPLE_RATE)
    fade_out = int(0.055 * SAMPLE_RATE)
    env[:fade_in] = np.linspace(0.0, 1.0, fade_in)
    env[-fade_out:] = np.linspace(1.0, 0.0, fade_out)

    mix = np.tanh((servo + latch1 + latch2 + air_cushion) * 0.95) * env
    mix -= np.mean(mix)
    mix /= np.max(np.abs(mix))
    return mix * 0.74


def generate_water_glide() -> np.ndarray:
    """1.40s seamless hull/hydrofoil water slicing + splash wash loop."""
    duration = 1.40
    n = int(round(duration * SAMPLE_RATE))
    t = np.arange(n) / SAMPLE_RATE
    rng = np.random.default_rng(606)

    # Low-frequency hull water wave resonance (65-240 Hz)
    wave_low = periodic_band_noise(n, 65.0, 240.0, rng, slope=-0.35) * 0.44
    # Mid-band rushing water wake / hydroplane slice (260-1450 Hz)
    wake_mid = periodic_band_noise(n, 260.0, 1450.0, rng, slope=-0.25) * 0.52
    # Silky high-frequency spray / foam hiss (1500-4800 Hz)
    spray_high = periodic_band_noise(n, 1500.0, 4800.0, rng, slope=-0.65) * 0.24

    # Exact integer-cycle gentle wave modulation (2 cycles & 3 cycles in 1.40s)
    f_mod1 = 2.0 / duration
    f_mod2 = 3.0 / duration
    mod_low = 0.86 + 0.14 * np.sin(2.0 * np.pi * f_mod1 * t + 0.4)
    mod_spray = 0.82 + 0.12 * np.sin(2.0 * np.pi * f_mod2 * t + 1.2) + 0.06 * np.sin(2.0 * np.pi * f_mod1 * t)

    mix = wave_low * mod_low + (wake_mid + spray_high) * mod_spray
    mix = np.tanh(mix * 0.95)
    mix -= np.mean(mix)
    mix /= np.max(np.abs(mix))
    return mix * 0.68


def verify_ogg(path: Path, ffmpeg_exe: str, check_loop: bool) -> None:
    probe = subprocess.run([ffmpeg_exe, "-i", str(path)], capture_output=True, text=True)
    stream_lines = [ln.strip() for ln in probe.stderr.splitlines() if "Audio:" in ln or "Duration:" in ln]
    # Decode back to PCM to verify real sample integrity
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        dec_wav = Path(tmp.name)
    try:
        subprocess.run([ffmpeg_exe, "-y", "-v", "error", "-i", str(path), str(dec_wav)], check=True)
        with wave.open(str(dec_wav), "rb") as wf:
            ch = wf.getnchannels()
            sr = wf.getframerate()
            frames = wf.getnframes()
            pcm = np.frombuffer(wf.readframes(frames), dtype=np.int16).astype(np.float64) / 32768.0
        peak = float(np.max(np.abs(pcm)))
        seam_diff = float(abs(pcm[0] - pcm[-1])) if check_loop else 0.0
        print(
            f"{path.name}: {path.stat().st_size/1024:.1f} KB | ch={ch} sr={sr} "
            f"dur={frames/sr:.3f}s peak={peak:.3f}"
            + (f" seam_diff={seam_diff:.4f}" if check_loop else "")
        )
        for ln in stream_lines:
            print("  ", ln)
        assert ch == 1, f"Expected mono, got {ch} channels"
        assert frames > 0, "Empty decoded audio"
    finally:
        dec_wav.unlink(missing_ok=True)


def main() -> None:
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    jobs = [
        ("sports_car_engine.ogg", generate_engine(), True),
        ("sports_car_drift.ogg", generate_drift(), True),
        ("sports_car_nitro.ogg", generate_nitro(), True),
        ("sports_car_door.ogg", generate_door(), False),
        ("sports_car_morph.ogg", generate_morph(), False),
        ("sports_car_water_glide.ogg", generate_water_glide(), True),
    ]
    for filename, samples, is_loop in jobs:
        out_path = OUT_DIR / filename
        write_mono_ogg(samples, out_path, ffmpeg_exe)
        verify_ogg(out_path, ffmpeg_exe, is_loop)


if __name__ == "__main__":
    main()
