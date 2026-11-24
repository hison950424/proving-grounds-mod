# 檔案: mono_gunpack_sounds.py
# 目的: 槍聲沒有方向性的修正。Minecraft 只替「單聲道」音效算方向（左右、遠近）；立體聲的音效會直接在兩耳播放，
#   聽不出槍聲從哪裡來。很多槍包（Suffuse、TACZ 內建、CS2 刀包）的音效是立體聲，所以有些槍有方向、有些沒有。
#   這個工具把槍包資料夾裡所有立體聲的 .ogg 轉成單聲道（兩聲道平均混合），原檔先整包備份。
# 用法: python tools/mono_gunpack_sounds.py <ffmpeg.exe> <ffprobe.exe> <tacz 資料夾> <備份資料夾>
import os, shutil, subprocess, sys


def channels(ffprobe, path):
    r = subprocess.run([ffprobe, '-v', 'error', '-select_streams', 'a:0', '-show_entries', 'stream=channels',
                        '-of', 'csv=p=0', path], capture_output=True, text=True)
    try:
        return int(r.stdout.strip() or 0)
    except ValueError:
        return 0


def main():
    ffmpeg, ffprobe, tacz, backup = sys.argv[1:5]
    converted = skipped = failed = 0
    for pack in sorted(os.listdir(tacz)):
        pdir = os.path.join(tacz, pack)
        if not os.path.isdir(pdir):
            continue
        stereo = []
        for root, _, files in os.walk(pdir):
            for f in files:
                if f.lower().endswith('.ogg'):
                    p = os.path.join(root, f)
                    if channels(ffprobe, p) >= 2:
                        stereo.append(p)
                    else:
                        skipped += 1
        if not stereo:
            continue
        dst = os.path.join(backup, pack)
        if not os.path.exists(dst):
            shutil.copytree(pdir, dst)  # 整包備份（含原本的立體聲音效）
        for p in stereo:
            tmp = p + '.mono.ogg'
            r = subprocess.run([ffmpeg, '-y', '-loglevel', 'error', '-i', p, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '6', tmp])
            if r.returncode == 0 and os.path.getsize(tmp) > 0:
                os.replace(tmp, p)
                converted += 1
            else:
                failed += 1
                if os.path.exists(tmp):
                    os.remove(tmp)
        print(pack, 'converted', len(stereo))
    print('total converted', converted, 'already mono', skipped, 'failed', failed)


if __name__ == '__main__':
    main()
