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
# Intra/inter H.264 quantization creates different reconstruction noise at the
# boundary even for periodic source pixels. Require >40 dB boundary similarity
# and <2/255 mean error instead of treating codec noise as motion discontinuity.
seam_mse = float(np.square(frames[0] - frames[-1]).mean())
seam_psnr = float(10 * np.log10(255 ** 2 / max(seam_mse, 1e-12)))
assert seam_psnr > 40 and seam < 2, (seam_psnr, seam)
# Compare the disk, excluding the ship's low foreground trajectory.
disk_motion = float(np.abs(frames[2, 250:660, 400:1500] - frames[3, 250:660, 400:1500]).mean())
assert disk_motion > 1, disk_motion
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
          'adjacent_mean_pixel_delta': adjacent, 'orbital_region_motion': disk_motion,
          'central_dark_region_mean': center_darkness, 'unchanged_sprites_sha256': hashes}
(root / 'audit/space-validation.json').write_text(json.dumps(result, indent=2))
print('PASS: 1080p60/32s video, orbital motion, opaque dark center, smooth seam, outward galaxy phases, unchanged craft/debris')
print(json.dumps(result, indent=2))
