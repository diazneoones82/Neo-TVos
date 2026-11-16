from pathlib import Path
import subprocess,imageio_ffmpeg,json
root=Path(__file__).resolve().parents[1];(root/'audit').mkdir(exist_ok=True);ff=imageio_ffmpeg.get_ffmpeg_exe();source=root/'media-sources/NASA-black-hole.mp4';base=root/'media-sources/black-hole-seamless-gold.mp4'
filter="[0:v]fps=30,split=2[a][b];[a]trim=start=1:end=5,setpts=PTS-STARTPTS[a1];[b]trim=start=0.5:end=1,setpts=PTS-STARTPTS[b1];[a1][b1]xfade=transition=fade:duration=0.5:offset=3.5,colorchannelmixer=rr=0.9:rg=0.1:gr=0.48:gg=0.7:br=0.33:bg=0.45:bb=0.22,eq=contrast=1.08:saturation=0.72,format=yuv420p[out]"
with (root/'audit/render-cinematic-base.log').open('w') as log:subprocess.run([ff,'-y','-i',str(source),'-filter_complex',filter,'-map','[out]','-an','-t','4','-c:v','libx264','-preset','fast','-crf','16',str(base)],stdout=log,stderr=log,check=True)
output=root/'neo/assets/neo/video/cinematic-black-hole-v2.mp4'
filt="[0:v]fps=60,scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080,setsar=1[bg];[1:v]format=rgba,scale=500:-1,rotate='0.055*sin(2*PI*t/32)':ow=560:oh=420:c=none[craft];[bg][craft]overlay=x='-w-80+(W+2*w+160)*t/32':y='H*(0.78-0.16*t/32)-h/2':eval=frame:format=auto,format=yuv420p[out]"
with (root/'audit/render-cinematic-video.log').open('w') as log:subprocess.run([ff,'-y','-stream_loop','-1','-i',str(base),'-loop','1','-framerate','60','-i',str(root/'media-sources/cinematic-craft-v1.png'),'-filter_complex',filt,'-map','[out]','-t','32','-an','-c:v','libx264','-profile:v','high','-level:v','4.2','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(output)],stdout=log,stderr=log,check=True)
print('Cinematic 1080p60 seamless scene rendered',output.stat().st_size)
