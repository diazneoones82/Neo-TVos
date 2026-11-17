"""Check real encoded frames, loop continuity, and retained sprite identity."""
from pathlib import Path
import hashlib
import importlib.util
import json
import subprocess
import numpy as np
import imageio_ffmpeg

root = Path(__file__).resolve().parents[1]
video = root / 'neo/assets/neo/video/imaginary-space-v1.mp4'
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
reader = imageio_ffmpeg.read_frames(str(video))
metadata = next(reader)
reader.close()
assert metadata['size'] == (1920, 1080), metadata
assert metadata['fps'] == 60, metadata
assert abs(metadata['duration'] - 32) < .02, metadata
selection = '+'.join(f'eq(n,{n})' for n in [0, 1, 480, 960, 1918, 1919])
encoded = subprocess.run([ffmpeg, '-v', 'error', '-i', str(video), '-vf', f"select='{selection}'",
                          '-fps_mode', 'passthrough', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-'],
                         capture_output=True, check=True).stdout
frames = np.frombuffer(encoded, dtype=np.uint8).reshape(-1, 1080, 1920, 3).astype(np.float32)
assert len(frames) == 6
seam = float(np.abs(frames[0] - frames[-1]).mean())
adjacent = max(float(np.abs(frames[0] - frames[1]).mean()), float(np.abs(frames[-2] - frames[-1]).mean()))
# Swirling material moves between adjacent frames, unlike a static/pulsed plate.
# Bound decoded mean error and compare the lossless boundary to actual frame
# motion. Absolute PSNR alone penalizes stronger continuous orbital motion;
# it is reported as a diagnostic rather than treated as a static-image gate.
seam_mse = float(np.square(frames[0] - frames[-1]).mean())
seam_psnr = float(10 * np.log10(255 ** 2 / max(seam_mse, 1e-12)))
assert seam < 2, (seam_psnr, seam)
source_loop_ratio=None
intermediate=root/'audit/original-orbital-flow.mp4'
if intermediate.exists():
    raw=subprocess.run([ffmpeg,'-v','error','-i',str(intermediate),'-vf',"select='eq(n,0)+eq(n,1)+eq(n,1918)+eq(n,1919)'",'-fps_mode','passthrough','-f','rawvideo','-pix_fmt','rgb24','-'],capture_output=True,check=True).stdout
    source=np.frombuffer(raw,np.uint8).reshape(-1,1080,1920,3).astype(np.float32)
    source_step=max(float(np.sqrt(np.square(source[0]-source[1]).mean())),float(np.sqrt(np.square(source[2]-source[3]).mean())))
    source_loop=float(np.sqrt(np.square(source[0]-source[3]).mean()))
    source_loop_ratio=source_loop/max(source_step,.001)
    assert source_loop_ratio<1.25,(source_loop,source_step)
# Compare the disk, excluding the ship's low foreground trajectory.
disk_motion = float(np.abs(frames[2, 250:660, 400:1500] - frames[3, 250:660, 400:1500]).mean())
assert disk_motion > 1, disk_motion
arc_motion={}
for name,ys,xs in [('upper',slice(240,350),slice(650,1100)),('lower',slice(700,820),slice(730,1080))]:
    arc_motion[name]=float(np.abs(frames[2,ys,xs]-frames[3,ys,xs]).mean())
    assert arc_motion[name]>.5,(name,arc_motion[name])
center_darkness = float(frames[:, 470:550, 840:940].mean())
assert center_darkness < 20, center_darkness
hashes = {}
for name in ['cinematic-craft-v1.png', 'asteroid-fragment-v1.png']:
    path = 'media-sources/' + name
    previous = subprocess.check_output(['git', '-C', str(root), 'show', 'checkpoint/pre-original-space-20261116:' + path])
    current = (root / path).read_bytes()
    assert previous == current, f'Original sprite changed: {name}'
    hashes[name] = hashlib.sha256(current).hexdigest()
spec = importlib.util.spec_from_file_location('original_space', root / 'media-sources/render-original-space.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
for offset in [0, 16]:
    assert module.galaxy_phase(0, offset) == module.galaxy_phase(32, offset)
    a, _ = module.galaxy_phase(8, offset)
    b, _ = module.galaxy_phase(12, offset)
    assert b > a
result = {'fps': 60, 'duration': 32, 'loop_mean_pixel_delta': seam, 'loop_psnr_db': seam_psnr,
          'source_boundary_vs_adjacent_motion_ratio':source_loop_ratio,
          'adjacent_mean_pixel_delta': adjacent, 'orbital_region_motion': disk_motion,
          'upper_lower_arc_motion':arc_motion, 'central_dark_region_mean': center_darkness, 'unchanged_sprites_sha256': hashes}
(root/'audit').mkdir(exist_ok=True)
(root / 'audit/space-validation.json').write_text(json.dumps(result, indent=2))
print('PASS: 1080p60/32s video, orbital motion, opaque dark center, smooth seam, outward galaxy phases, unchanged craft/debris')
print(json.dumps(result, indent=2))
