"""Render an original AI-art scene with periodic orbital flow and outward galaxy drift.

Inputs are generated PNG assets only. No downloaded visualization or film input.
Run with --preview for a small/short QA render; production is 1080p60, 32 seconds.
"""
from pathlib import Path
import argparse
import math
import subprocess
import imageio_ffmpeg
import numpy as np
import cv2
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PERIOD = 32.0
FPS = 60


def galaxy_phase(seconds, offset=0):
    phase = ((seconds + offset) % PERIOD) / PERIOD
    opacity = math.sin(math.pi * phase) ** 2
    return phase, opacity


def orbital_geometry(width, height, artwork):
    y, x = np.mgrid[0:height, 0:width].astype(np.float32)
    x = x / width - .47
    y = y / height - .50
    disk_x = x * .985 - y * .173
    disk_y = x * .173 + y * .985
    angle = np.arctan2(disk_y / .32, disk_x)
    radius = np.hypot(disk_x, disk_y / .32)
    luminance = artwork.mean(axis=2) / 255
    envelope = np.clip((.54 - radius) / .12, 0, 1) * np.clip((luminance - .045) / .20, 0, 1)
    inner = np.clip((.31 - radius) / .20, 0, 1)
    # Material is sampled from this project's generated plasma artwork only.
    # Mirroring closes its texture seam; coordinate advection moves the actual
    # detail around streamlines rather than merely pulsing a static image.
    tile = artwork[int(height*.35):int(height*.61), int(width*.64):int(width*.91)].mean(axis=2)
    material = np.clip(tile / (cv2.GaussianBlur(tile, (0,0), 7) + 5), .48, 1.7)
    material = np.concatenate((material, material[:,::-1]),axis=1)
    material = np.concatenate((material, material[::-1]),axis=0).astype(np.float32)
    circular_angle=np.arctan2(y*height/width,x)
    circular_radius=np.hypot(x,y*height/width)
    arc_weight=np.clip((np.abs(disk_y)-.025)/.075,0,1)
    whole_envelope=np.clip((.42-circular_radius)/.10,0,1)*np.clip((luminance-.045)/.20,0,1)
    envelope=np.maximum(envelope,whole_envelope)
    return angle,radius,envelope,inner,material,circular_angle,circular_radius,arc_weight


def render_background(destination, width, height, seconds, ffmpeg, log):
    image = Image.open(ROOT / 'media-sources/imaginary-black-hole-v1.png').convert('RGB')
    image = image.resize((width, height), Image.Resampling.LANCZOS)
    base = np.asarray(image).astype(np.float32)
    cv2.setNumThreads(4)
    angle,radius,envelope,inner,material,circle_angle,circle_radius,arc_weight=orbital_geometry(width,height,base)
    mh,mw=material.shape
    angular=(angle/(2*np.pi)+radius*.42)*mw
    radial=radius*mh*3
    arc_angular=(circle_angle/(2*np.pi)+circle_radius*.5)*mw
    arc_radial=circle_radius*mh*5
    command = [ffmpeg, '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{width}x{height}',
               '-r', str(FPS), '-i', '-', '-an', '-c:v', 'libx264', '-threads', '4',
               '-preset', 'ultrafast', '-crf', '0', '-pix_fmt', 'yuv420p', str(destination)]
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=log, stderr=log)
    try:
        for frame in range(round(seconds * FPS)):
            phase = 2 * math.pi * (frame / FPS) / PERIOD
            cycle=(frame/FPS)/PERIOD
            # One outer orbit and three inner orbits per closed cycle; both
            # have genuine spatial texture motion and exact periodic endpoints.
            outer=cv2.remap(material, (angular-cycle*mw).astype(np.float32),(radial+cycle*mh).astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_WRAP)
            fast=cv2.remap(material, (angular-cycle*mw*3).astype(np.float32),(radial+cycle*mh*2).astype(np.float32), cv2.INTER_LINEAR, borderMode=cv2.BORDER_WRAP)
            arcs=cv2.remap(material,(arc_angular-cycle*mw*2).astype(np.float32),(arc_radial+cycle*mh).astype(np.float32),cv2.INTER_LINEAR,borderMode=cv2.BORDER_WRAP)
            disk_detail=outer*(1-inner)+fast*inner
            detail=disk_detail*(1-arc_weight)+arcs*arc_weight
            knots=.08*np.sin(7*angle+29*radius-phase*3)
            light=1+envelope*(1.20*(detail-1)+knots)
            pixels=np.clip(base*light[:,:,None],0,255).astype(np.uint8)
            process.stdin.write(pixels.tobytes())
            if frame % (FPS * 4) == 0:
                print(f'Orbital flow: {frame // FPS}/{seconds:g}s', flush=True)
    finally:
        process.stdin.close()
    if process.wait() != 0:
        raise RuntimeError('Orbital-flow encoding failed; inspect render log')


def composite(base, output, seconds, ffmpeg, log):
    filters = ['[0:v]scale=1920:1080,setsar=1,format=gbrp[scene0]',
               '[1:v]format=rgba,split=4[ga][gb][gc][gd]']
    # Staggered periodic dissolves hide each outward trajectory reset. Each
    # visible galaxy travels away from the center and its glow swells gently.
    for index, (label, side, offset, size, y, origin) in enumerate([
            ('ga', -1, 0, 205, 150, 235), ('gb', -1, 16, 95, 800, 390),
            ('gc', 1, 0, 145, 210, 1570), ('gd', 1, 16, 75, 100, 1430)]):
        phase = f'mod(T+{offset},32)/32'
        alpha = f'alpha(X,Y)*(0.68+0.10*sin(2*PI*T/32))*pow(sin(PI*({phase})),2)'
        mirror = ',hflip' if side == 1 else ''
        filters.append(f'[{label}]scale={size}:-1{mirror},format=rgba,geq=r=\'r(X,Y)\':g=\'g(X,Y)\':b=\'b(X,Y)\':a=\'{alpha}\'[gal{index}]')
        phase_t = f'mod(t+{offset},32)/32'
        x = f'{origin}+({side})*80*({phase_t})'
        filters.append(f'[scene{index}][gal{index}]overlay=x=\'{x}\':y={y}:eval=frame:format=auto[scene{index+1}]')
    filters += [
        "[2:v]format=rgba,scale=500:-1,rotate='0.055*sin(2*PI*t/32)':ow=560:oh=420:c=none[craft]",
        "[scene4][craft]overlay=x='-w-80+(W+2*w+160)*t/32':y='H*(0.78-0.16*t/32)-h/2':eval=frame:format=auto[ship]",
        '[3:v]format=rgba,split=3[a][b][c]',
        "[a]scale=76:-1,rotate='2*PI*t/32':ow=100:oh=100:c=none[d1]",
        "[b]scale=42:-1,rotate='-2*PI*t/32':ow=60:oh=60:c=none[d2]",
        "[c]scale=25:-1,rotate='2*PI*t/32+1.2':ow=36:oh=36:c=none[d3]",
        "[ship][d1]overlay=x='170+70*sin(2*PI*t/32)':y='870+36*cos(2*PI*t/32)':eval=frame:format=auto[s1]",
        "[s1][d2]overlay=x='1660+65*sin(2*PI*t/32+1)':y='180+30*cos(2*PI*t/32+1)':eval=frame:format=auto[s2]",
        "[s2][d3]overlay=x='1500+50*cos(2*PI*t/32)':y='940+22*sin(2*PI*t/32)':eval=frame:format=auto,format=yuv420p[out]",
    ]
    command = [ffmpeg, '-y', '-i', str(base)]
    for asset in ['distant-galaxy-v1.png', 'cinematic-craft-v1.png', 'asteroid-fragment-v1.png']:
        command += ['-loop', '1', '-framerate', str(FPS), '-i', str(ROOT / 'media-sources' / asset)]
    command += ['-filter_complex_threads', '4', '-filter_complex', ';'.join(filters), '-map', '[out]',
                '-t', str(seconds), '-an', '-c:v', 'libx264', '-threads', '4', '-profile:v', 'high',
                '-level:v', '4.2', '-preset', 'fast', '-crf', '16', '-pix_fmt', 'yuv420p',
                '-movflags', '+faststart', str(output)]
    subprocess.run(command, stdout=log, stderr=log, check=True)


if __name__ == '__main__':
    arguments = argparse.ArgumentParser()
    arguments.add_argument('--preview', action='store_true')
    args = arguments.parse_args()
    (ROOT / 'audit').mkdir(exist_ok=True)
    duration = 3 if args.preview else PERIOD
    base = ROOT / 'audit/original-orbital-flow.mp4'
    output = ROOT / ('audit/original-space-preview.mp4' if args.preview else 'neo/assets/neo/video/imaginary-space-v1.mp4')
    with (ROOT / 'audit/render-original-space.log').open('w') as log:
        render_background(base, 1280 if args.preview else 1920, 720 if args.preview else 1080, duration, imageio_ffmpeg.get_ffmpeg_exe(), log)
        print('Compositing outward galaxies, original spaceship and debris', flush=True)
        temporary=ROOT/'audit/original-space-finished.mp4'
        composite(base, temporary, duration, imageio_ffmpeg.get_ffmpeg_exe(), log)
        temporary.replace(output)
    print(f'Rendered {output.name}: {output.stat().st_size} bytes', flush=True)
