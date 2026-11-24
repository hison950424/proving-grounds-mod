# 檔案: make_cough_sound.py
# 目的: 用程式合成原創的咳嗽聲（不取樣任何錄音），給毒氣彈用。
#   模擬一聲咳嗽的三個階段：
#     1. 爆發：聲門突然打開，約 40 毫秒的強烈寬頻氣流聲；
#     2. 氣流：約 0.2 秒慢慢減弱的喉嚨氣音（白噪音經過人聲共鳴的濾波，大約是「ㄜ」的共鳴位置）；
#     3. 聲帶振動：前段帶一點低沉的聲帶脈衝（基頻約 110～150 Hz，往下滑）。
#   產生 3 個版本（音高、長短、共鳴不同），存成 44.1 kHz 單聲道 WAV，再用 ffmpeg 轉成 Minecraft 要的 .ogg。
# 用法: python tools/make_cough_sound.py <ffmpeg.exe 路徑>
import math, os, random, struct, subprocess, sys, tempfile, wave

SR = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'provinggrounds', 'sounds', 'gas')


class Biquad:
    """帶通濾波器（RBJ cookbook，固定 0 dB 峰值增益）。"""
    def __init__(self, freq, q):
        w = 2 * math.pi * freq / SR
        alpha = math.sin(w) / (2 * q)
        a0 = 1 + alpha
        self.b0, self.b1, self.b2 = alpha / a0, 0.0, -alpha / a0
        self.a1, self.a2 = -2 * math.cos(w) / a0, (1 - alpha) / a0
        self.x1 = self.x2 = self.y1 = self.y2 = 0.0

    def __call__(self, x):
        y = self.b0 * x + self.b1 * self.x1 + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2
        self.x2, self.x1, self.y2, self.y1 = self.x1, x, self.y1, y
        return y


def cough(seed, length, f0_start, f0_end, formants, burst_ms, tau_ms, voiced_amount):
    rnd = random.Random(seed)
    n = int(SR * length)
    # 共鳴（人聲的共振峰）：氣音與聲帶聲都經過
    noise_f = [(Biquad(f, q), g) for f, q, g in formants]
    voice_f = [(Biquad(f, q * 1.5), g) for f, q, g in formants]
    hiss = Biquad(3200, 1.2)  # 喉嚨的嘶聲
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SR
        ms = t * 1000
        # 振幅包絡：極快的起音，爆發段後快速衰減到氣流段，再慢慢收尾
        attack = min(1.0, ms / 4.0)
        if ms < burst_ms:
            env = attack
        else:
            env = 0.55 * math.exp(-(ms - burst_ms) / tau_ms) + 0.45 * math.exp(-(ms - burst_ms) / (tau_ms * 0.35))
        env *= attack
        # 氣流：白噪音 → 共振峰 + 一點高頻嘶聲
        w = rnd.uniform(-1, 1)
        breath = sum(f(w) * g for f, g in noise_f) + hiss(w) * 0.35
        # 聲帶：鋸齒狀脈衝，基頻往下滑，只在前段
        f0 = f0_start + (f0_end - f0_start) * min(1.0, t / length)
        phase += f0 / SR
        if phase >= 1:
            phase -= 1
        pulse = (1 - 2 * phase) * (1 + 0.15 * rnd.uniform(-1, 1))
        voice = sum(f(pulse) * g for f, g in voice_f)
        voice_env = voiced_amount * math.exp(-ms / (tau_ms * 0.9))
        # 爆發段額外的寬頻能量（「咳」的爆破感）
        burst = (rnd.uniform(-1, 1) * 0.6) * max(0.0, 1 - ms / burst_ms) if ms < burst_ms else 0.0
        out.append(env * (breath * 1.4 + burst) + voice * voice_env * attack)
    # 結尾 15 毫秒淡出、正規化到 0.85
    fade = int(SR * 0.015)
    for k in range(fade):
        out[-1 - k] *= k / fade
    peak = max(abs(v) for v in out) or 1
    return [v / peak * 0.85 for v in out]


def write_wav(path, samples):
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, s)) * 32767)) for s in samples))


VARIANTS = [
    # 種子, 長度(秒), 基頻起, 基頻終, 共振峰 [(頻率, Q, 增益)], 爆發毫秒, 衰減毫秒, 聲帶量
    (11, 0.34, 150, 110, [(520, 2.2, 1.0), (1350, 3.0, 0.7), (2600, 4.0, 0.35)], 40, 85, 0.55),
    (23, 0.28, 135, 105, [(600, 2.0, 1.0), (1500, 3.2, 0.6), (2800, 4.5, 0.3)], 32, 70, 0.45),
    (37, 0.40, 125, 95, [(480, 2.4, 1.0), (1250, 2.8, 0.75), (2500, 4.0, 0.4)], 45, 100, 0.6),
]

if __name__ == '__main__':
    ffmpeg = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    tmp = tempfile.mkdtemp()
    for idx, v in enumerate(VARIANTS, 1):
        wav = os.path.join(tmp, 'cough%d.wav' % idx)
        write_wav(wav, cough(*v))
        ogg = os.path.join(OUT, 'cough%d.ogg' % idx)
        subprocess.run([ffmpeg, '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', ogg], check=True)
        print(ogg, os.path.getsize(ogg))
