# 檔案: make_cough_sound.py
# 目的: 用程式合成原創的咳嗽聲（不取樣任何錄音），給毒氣彈用。
#   v2（使用者回饋「太像噴氣」）：以「聲帶發聲」為主、氣流聲為輔：
#     1. 爆發：聲門突然打開，約 20 毫秒的短促爆破（低通過的噪音，不尖）；
#     2. 喉音：聲帶振動（Rosenberg 聲門脈衝，基頻約 95～150 Hz 往下滑），
#        加上每次振動的細微不規則（jitter／shimmer）與「氣泡音」的粗糙感（隔一次振動變弱），聽起來沙啞；
#        經過「ㄏㄜ」母音的共振峰（約 650、1150、2450 Hz）；
#     3. 氣流：少量白噪音經過同樣的共振峰，跟著喉音一起衰減；
#     最後整體低通（約 3.5 kHz），去掉高頻嘶聲。
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


class LowPass:
    """一階低通。"""
    def __init__(self, freq):
        self.a = 1 - math.exp(-2 * math.pi * freq / SR)
        self.y = 0.0

    def __call__(self, x):
        self.y += self.a * (x - self.y)
        return self.y


def glottal(phase, open_q=0.6):
    """Rosenberg 聲門脈衝：張開段慢慢升起、關閉段快速落下、之後閉合（0）。回傳的是氣流的變化率（比較像真的聲音）。"""
    tp = open_q * 0.66
    tn = open_q * 0.34
    if phase < tp:
        return math.sin(math.pi * phase / tp) * 0.5 * math.pi / tp * 0.02
    if phase < tp + tn:
        return -math.sin(math.pi / 2 * (phase - tp) / tn) * math.pi / (2 * tn) * 0.02
    return 0.0


def cough(seed, length, f0_start, f0_end, formants, burst_ms, tau_ms, noise_amount):
    rnd = random.Random(seed)
    n = int(SR * length)
    voice_f = [(Biquad(f, q), g) for f, q, g in formants]
    noise_f = [(Biquad(f, q * 0.7), g) for f, q, g in formants]
    burst_lp = LowPass(1800)
    out_lp = LowPass(3500)
    out_lp2 = LowPass(5000)
    vs, bs, ns = [], [], []
    phase, period_amp, period_idx = 0.0, 1.0, 0
    f0_jit = 1.0
    for i in range(n):
        t = i / SR
        ms = t * 1000
        attack = min(1.0, ms / 3.0)
        # 喉音包絡：很快到最大，維持一小段，然後衰減
        hold = 35
        venv = attack if ms < hold else math.exp(-(ms - hold) / tau_ms)
        # 聲帶振動：每個週期重新抽一次不規則量；氣泡音＝隔一次振動變弱
        f0 = (f0_start + (f0_end - f0_start) * min(1.0, t / length)) * f0_jit
        phase += f0 / SR
        if phase >= 1:
            phase -= 1
            period_idx += 1
            f0_jit = 1 + rnd.uniform(-0.05, 0.05)
            period_amp = (1 + rnd.uniform(-0.2, 0.2)) * (0.6 if period_idx % 2 else 1.0)
        src = glottal(phase) * period_amp
        vs.append(sum(f(src) * g for f, g in voice_f) * venv)
        w = rnd.uniform(-1, 1)
        ns.append(sum(f(w) * g for f, g in noise_f) * venv)
        bs.append(burst_lp(rnd.uniform(-1, 1)) * max(0.0, 1 - ms / burst_ms) if ms < burst_ms else 0.0)
    # 三個成分各自正規化，再照比例混合：喉音為主（1.0）、爆發 0.45、氣流 noise_amount
    def norm(a):
        m = max(abs(x) for x in a) or 1
        return [x / m for x in a]
    vs, bs, ns = norm(vs), norm(bs), norm(ns)
    out = [out_lp2(out_lp(v + 0.45 * b + noise_amount * x)) for v, b, x in zip(vs, bs, ns)]
    fade = int(SR * 0.02)
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
    # 種子, 長度(秒), 基頻起, 基頻終, 共振峰 [(頻率, Q, 增益)], 爆發毫秒, 衰減毫秒, 氣流量
    (11, 0.30, 150, 115, [(650, 4.0, 1.0), (1150, 5.0, 0.6), (2450, 6.0, 0.25)], 20, 70, 0.25),
    (23, 0.26, 135, 105, [(700, 4.0, 1.0), (1200, 5.0, 0.55), (2500, 6.0, 0.22)], 18, 60, 0.2),
    (37, 0.36, 120, 95, [(620, 4.5, 1.0), (1100, 5.0, 0.65), (2400, 6.0, 0.28)], 22, 85, 0.3),
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
