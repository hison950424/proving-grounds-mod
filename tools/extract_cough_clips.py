# 檔案: extract_cough_clips.py
# 目的: 從公眾領域的咳嗽錄音切出一聲一聲的咳嗽，過濾雜音後存成 Minecraft 用的 .ogg（毒氣彈的咳嗽聲）。
#   素材：Wikimedia Commons「File:Cough 1.ogg」，作者 ezwa，授權 Public domain（公眾領域，可自由使用、修改、散佈）。
#   每一聲的處理（ffmpeg）：
#     1. 高通 90 Hz、低通 5.5 kHz（兩次，較陡）、6 kHz 附近再壓 8 dB：去掉低頻嗡聲與咳嗽爆發時的「嘶／淅淅」高頻；
#     2. 不用頻譜降噪：降噪在聲音變大時會產生「淅淅」的沙沙聲；背景雜音約 -60 dB，咳嗽前後又整段裁掉，不需要降噪；
#     3. 只留咳嗽的爆發：找出最大聲的那一下（含緊接著的連咳），音量要在最大聲 9 dB 以內，
#        咳嗽前後持續的「啊——」聲、吸氣、喘氣全部裁掉；前留 20 毫秒、後留 150 毫秒自然衰減，5 毫秒淡入、60 毫秒淡出；
#     5. 單聲道 44.1 kHz，音量統一到最大 -1 dB。
# 用法: python tools/extract_cough_clips.py <ffmpeg.exe> <Cough_1.ogg>
import math, os, re, struct, subprocess, sys, tempfile, wave

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'provinggrounds', 'sounds', 'gas')

# (開始秒, 結束秒)：錄音裡最清楚的 4 聲咳嗽（3.7 秒那聲前面有一大段「啊——」濾不掉，不用；避開 2.5～3.3、14.8～15.6 秒的呼吸／哼聲）
CLIPS = [(7.40, 8.10), (9.40, 9.95), (13.64, 14.30), (15.82, 16.45)]

CLEAN = "highpass=f=90,lowpass=f=5500,lowpass=f=5500,equalizer=f=6000:t=q:w=1.2:g=-8"  # v2：拿掉降噪（會產生沙沙聲），高頻收到 5.5 kHz、壓低 4～8 kHz 的嘶聲
SR = 44100


def keep_cough_only(samples):
    """
    只留咳嗽的爆發：找出最大聲的那一下，往前後合併間隔小於 60 毫秒、音量在最大聲 9 dB 以內的部分（連咳），
    前留 20 毫秒、後留 150 毫秒自然衰減；其他（咳嗽前後持續的「啊——」聲、吸氣、喘氣）全部裁掉或壓成靜音。
    """
    win = int(SR * 0.005)
    env = []
    for i in range(0, len(samples), win):
        chunk = samples[i:i + win]
        env.append(math.sqrt(sum(x * x for x in chunk) / max(1, len(chunk))))
    peak_i = max(range(len(env)), key=lambda i: env[i])
    thr = env[peak_i] * 0.35  # -9 dB
    gap = 12  # 60 毫秒
    first = last = peak_i
    i, quiet = peak_i, 0
    while i > 0 and quiet <= gap:
        i -= 1
        if env[i] >= thr: first, quiet = i, 0
        else: quiet += 1
    i, quiet = peak_i, 0
    while i < len(env) - 1 and quiet <= gap:
        i += 1
        if env[i] >= thr: last, quiet = i, 0
        else: quiet += 1
    # 增益：爆發區間內夠大聲 = 1，其餘慢慢降到 0（釋放較慢，保留咳嗽自然的尾音）
    gains, g = [], 0.0
    for k, e in enumerate(env):
        t = 1.0 if first <= k <= last and e >= thr * 0.5 else 0.0
        g += (t - g) * (0.7 if t > g else 0.12)
        gains.append(g)
    start = max(0, (first - 4) * win)
    end = min(len(samples), (last + 30) * win)
    out = [samples[k] * gains[min(len(gains) - 1, k // win)] for k in range(start, end)]
    fi, fo = int(SR * 0.005), int(SR * 0.06)
    for k in range(min(fi, len(out))):
        out[k] *= k / fi
    for k in range(min(fo, len(out))):
        out[-1 - k] *= k / fo
    m = max(abs(x) for x in out) or 1
    return [x / m * 10 ** (-1 / 20) for x in out]


def read_wav(path):
    with wave.open(path, 'rb') as w:
        raw = w.readframes(w.getnframes())
    return [v / 32768 for v in struct.unpack('<%dh' % (len(raw) // 2), raw)]


def write_wav(path, samples):
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, s)) * 32767)) for s in samples))


def run(args):
    return subprocess.run(args, capture_output=True, text=True, encoding='utf-8', errors='replace')


def main():
    ffmpeg, src = sys.argv[1], sys.argv[2]
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if re.match(r'cough\d+\.ogg$', f):
            os.remove(os.path.join(OUT, f))
    tmp = tempfile.mkdtemp()
    for i, (a, b) in enumerate(CLIPS, 1):
        wav = os.path.join(tmp, 'c%d.wav' % i)
        run([ffmpeg, '-y', '-loglevel', 'error', '-ss', str(a), '-to', str(b), '-i', src, '-af', CLEAN, '-ac', '1', '-ar', '44100', wav])
        clip = keep_cough_only(read_wav(wav))
        write_wav(wav, clip)
        ogg = os.path.join(OUT, 'cough%d.ogg' % i)
        run([ffmpeg, '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '6', ogg])
        print('cough%d.ogg  %.2f s' % (i, len(clip) / SR))


if __name__ == '__main__':
    main()
