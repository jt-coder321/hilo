"""Synthesizes the game's sound effects into app/src/main/res/raw as 16-bit mono WAV."""
import os, sys, wave
import numpy as np

SR = 44100
rng = np.random.default_rng(7)
OUT = sys.argv[1]

def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.minimum(1, t / max(attack, 1e-4))
    return a * np.exp(-t / decay)

def bandnoise(n, lo, hi):
    x = rng.standard_normal(n)
    f = np.fft.rfft(x)
    freqs = np.fft.rfftfreq(n, 1 / SR)
    f[(freqs < lo) | (freqs > hi)] = 0
    y = np.fft.irfft(f, n)
    return y / (np.abs(y).max() + 1e-9)

def tone(freq, dur, decay, harmonics=((1, 1.0),)):
    n = int(SR * dur)
    t = np.arange(n) / SR
    y = sum(a * np.sin(2 * np.pi * freq * h * t) for h, a in harmonics)
    return y * env(n, 0.004, decay)

def place(buf, clip, at):
    i = int(at * SR)
    end = min(len(buf), i + len(clip))
    buf[i:end] += clip[: end - i]

def save(name, y, gain=0.8):
    y = y / (np.abs(y).max() + 1e-9) * gain
    pcm = (y * 32767).astype(np.int16)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())

def snap(dur=0.05, lo=1800, hi=9000, decay=0.012):
    n = int(SR * dur)
    return bandnoise(n, lo, hi) * env(n, 0.0008, decay)

# Card dealt: a soft slide followed by a crisp snap on the felt.
n = int(SR * 0.16)
slide = bandnoise(n, 700, 5000) * env(n, 0.03, 0.035) * 0.35
deal = np.zeros(int(SR * 0.2)); place(deal, slide, 0); place(deal, snap(), 0.05)
save("card_deal", deal, 0.7)

# Card flip: two quick ticks.
flip = np.zeros(int(SR * 0.12)); place(flip, snap(0.03, 2500, 9000, 0.006), 0); place(flip, snap(0.04, 1500, 7000, 0.01) * 0.8, 0.045)
save("card_flip", flip, 0.65)

# Riffle shuffle: accelerating burst of card ticks, then the bridge.
shuf = np.zeros(int(SR * 1.5))
t = 0.0
for i in range(70):
    place(shuf, snap(0.02, 2000, 10000, 0.004) * rng.uniform(0.4, 1.0), t)
    t += 0.012 + 0.006 * np.sin(i / 70 * np.pi)
n = int(SR * 0.45)
bridge = bandnoise(n, 400, 4000) * env(n, 0.15, 0.12) * 0.6
place(shuf, bridge, t + 0.05)
place(shuf, snap(0.06, 600, 4000, 0.02), t + 0.55)
save("shuffle", shuf, 0.7)

# Chip: ceramic clack, two chips touching.
def clack():
    y = tone(3100, 0.09, 0.018, ((1, 1.0), (1.47, 0.6), (2.31, 0.35)))
    place(y, snap(0.02, 3000, 12000, 0.003) * 0.5, 0)
    return y
chip = np.zeros(int(SR * 0.16)); place(chip, clack(), 0); place(chip, clack() * 0.6, 0.035)
save("chip", chip, 0.6)

# Win: soft rising arpeggio.
win = np.zeros(int(SR * 0.7))
for i, f in enumerate([523.25, 659.25, 783.99]):
    place(win, tone(f, 0.5, 0.18, ((1, 1), (2, 0.25), (3, 0.08))), i * 0.08)
save("win", win, 0.55)

# Blackjack: brighter, four notes with a sparkle.
bj = np.zeros(int(SR * 1.0))
for i, f in enumerate([523.25, 659.25, 783.99, 1046.5]):
    place(bj, tone(f, 0.6, 0.22, ((1, 1), (2, 0.3), (4, 0.1))), i * 0.07)
for i in range(6):
    place(bj, tone(2093 * rng.uniform(1, 1.5), 0.15, 0.04) * 0.25, 0.3 + i * 0.05)
save("blackjack", bj, 0.6)

# Lose: gentle descending pair.
lose = np.zeros(int(SR * 0.6))
place(lose, tone(392.0, 0.4, 0.15, ((1, 1), (2, 0.2))), 0)
place(lose, tone(311.1, 0.45, 0.18, ((1, 1), (2, 0.2))), 0.12)
save("lose", lose, 0.4)

# Mistake: short low double buzz.
mis = np.zeros(int(SR * 0.3))
b = tone(180, 0.1, 0.05, ((1, 1), (2, 0.5), (3, 0.3)))
place(mis, b, 0); place(mis, b, 0.12)
save("mistake", mis, 0.45)

# Correct answer: single bell ding.
save("correct", tone(1318.5, 0.5, 0.15, ((1, 1), (2.76, 0.2), (5.4, 0.08))), 0.45)
print("ok")

# Table chatter: a short murmur of voices (band-limited noise with syllable-like bursts).
n = int(SR * 0.9)
chat = np.zeros(n)
t = 0.0
while t < 0.8:
    d = rng.uniform(0.06, 0.14)
    m = int(SR * d)
    f0 = rng.uniform(140, 260)
    tt = np.arange(m) / SR
    voice = np.sin(2 * np.pi * f0 * tt) * 0.5 + np.sin(2 * np.pi * f0 * 2.02 * tt) * 0.3 + bandnoise(m, 300, 2500) * 0.4
    place(chat, voice * np.hanning(m) * rng.uniform(0.4, 1.0), t)
    t += d * rng.uniform(0.7, 1.2)
chat += bandnoise(n, 200, 3000) * 0.15
chat *= np.hanning(n)
save("chatter", chat, 0.45)
