# Original cinematic artwork and animation

The current space screensaver is built from text-generated artwork and a custom animation renderer. No external scientific visualization, downloaded black-hole footage, movie frame or film spaceship is used as a rendering input. This is imaginative cinematic artwork, not a numerical general-relativity simulation or a claim of scientific accuracy.

## Current inputs

| Asset | Origin | Treatment |
| --- | --- | --- |
| `media-sources/imaginary-black-hole-v1.png` | New built-in image_gen generation, text only, no input image | Orbital brightness texture with faster inner-streamline harmonics, fixed lensed arcs and a dark center |
| `media-sources/distant-galaxy-v1.png` | New built-in image_gen generation, genuine alpha | Small distant galaxies drifting outward with staggered smooth glow/dissolve phases |
| `media-sources/cinematic-craft-v1.png` | Existing generated spacecraft | Unchanged pixels; same slow crossing trajectory and bank |
| `media-sources/asteroid-fragment-v1.png` | Existing generated debris | Unchanged pixels; same three small rotating trajectories |

Renderer: `media-sources/render-original-space.py`. Output: `neo/assets/neo/video/imaginary-space-v1.mp4`, 32 seconds, 1920×1080 at 60 frames/second. Integer motion harmonics close at the loop boundary; the craft is off-screen at both ends. Galaxy positions move away from the center while visible; their return phase is concealed with staggered dissolves so a repeating loop has no abrupt reset. This is a cinematic animation, not an assertion of cosmological recession rates.

The base artwork depicts an asymmetric luminous disk, a photon-halo-like boundary and lensed arcs. The custom orbital light pattern animates flowing brightness in the disk; it does not import an existing simulation's motion. Android plays the pre-rendered video instead of generating those effects on the TV CPU.

## Generation prompts

Mode: built-in image_gen for both new assets; no CLI/API fallback and no input images. The generated originals were copied into the project before rendering.

### New black hole

Use case: photorealistic-natural cinematic science-fiction visual effects. Generate a brand-new original 16:9 wide movie-quality image for an imaginary deep-space screensaver, created entirely from this text, with no reference image, no NASA visualization, no movie frame or recognizable franchise composition. Subject: a colossal dark gravitational void slightly left of center at x=48%, y=49%, with a perfectly opaque near-black event horizon, fine pale amber photon halo, and an exquisitely detailed turbulent ivory-gold accretion disk viewed at a low oblique angle, asymmetric luminous bands and softly lensed arcs rising above and dipping beneath the void. Physically inspired but explicitly imaginative artwork, intricate plasma texture, delicate warm highlights, deep blacks within the void; realistic mature cinematic rendering and restrained exposure. Surrounding space is dark navy with fine natural pinpoint stars and faint dusty indigo nebulae. Keep the outer left and right thirds uncluttered for separate animated distant-galaxy layers. Black hole and disk entirely in frame with comfortable margins; central dark void at least 13% of image width. No spacecraft, no planets, no big spiral galaxies, no foreground asteroids, no words, no captions, no watermark, no logo, no neon cartoon glow. Wide 16:9 landscape composition, high resolution.

### New distant galaxy

Use case: photorealistic-natural astronomical visual effects asset. Create one original distant spiral galaxy isolated on a genuine transparent background, no source/reference images. An exquisite tiny deep-space spiral viewed at a 35-degree incline, a soft luminous warm ivory central bulge with delicate asymmetric blue-violet spiral arms, pinpoint star clusters and faint brown dust lanes, realistic long-exposure astrophotography appearance with gently fading translucent outskirts and generous transparent margins. Galaxy centered, entire object in frame, wide horizontal composition 3:2. Natural restrained colors and brightness; no black background, no rectangular background patch, no text, no watermark, no planets, no spacecraft. This will be composited as a small faraway glowing object at the edge of a cinematic space scene; mature realistic detail.

### Existing spacecraft (retained unchanged)

“Use case: photorealistic-natural / cinematic visual effects asset. Create an original physically believable deep-space exploration spacecraft as an isolated transparent cutout for compositing into an Interstellar-inspired black-hole screensaver. A mature hard-science-fiction film miniature, not an illustration or game icon: a compact circular rotating habitat ring with twelve weathered ivory modules, intricate dark central trusses, slender antennas, metallic radiators, layered thermal insulation and subtle micro-surface wear. View from an oblique three-quarter angle, seeing the front and near underside, the vessel oriented traveling toward the right and slightly away. Warm pale-gold rim light from above-right, cool faint blue fill, deep realistic shadows and fine specular highlights. Entire craft fully inside the frame with comfortable transparent margins. High detail, photographic sharpness appropriate to a movie miniature, no text, no logos, no background, no glow aura, no giant thruster flames, no cartoon styling, no triangular fighter jet, no exaggerated neon. Genuine alpha transparency.”

### Existing debris (retained unchanged)

“Photorealistic cinematic visual-effects cutout of ONE small irregular rocky asteroid fragment, isolated on genuine transparent background. Dark charcoal silicate rock with pitted craters, fractured edges and tiny metallic mineral specks, three-dimensional natural asymmetrical shape, soft warm rim light from the upper right and cool very faint blue fill, deep natural shadows. Entire rock centered and fully within frame with transparent margins. This will be a distant small drifting object in a realistic deep-space black-hole movie loop. No scene, no stars, no spacecraft, no flame, no smoke, no halo, no words, no cartoon or illustration.”

## Optional engine glow

Neo Settings → Screensaver → Spaceship engine exhaust toggles a subtle breathing-blue ion glow. The default is Off. It is a small transparent Canvas layer; the spaceship and debris PNGs and the encoded craft trajectory are unchanged. Glow position, bank and breathing phase come from MediaPlayer's current playback position so seeking, pausing and looping do not use an independent wall clock. The glow renderer stops with the player. This is an artistic engine effect, not a propulsion simulation.

## Spatial swirl and attached engine update

The current renderer advects the generated plasma's material detail around elliptical orbital coordinates: outer material completes one orbit per 32-second loop, and inner material completes three. Upper and lower luminous arcs also carry circulating detail, completing two rotations per loop. Mirrored tile edges and integer orbital periods close the source texture motion at the boundary. This replaces the earlier brightness-only modulation. An independently coded fixed lensed outline and dark-center mask preserve the original generated silhouette. Published black-hole visualizations were consulted as conceptual references for disk motion/lensing; their pixels, video and simulation code are not render inputs.

Engine glow begins at the rear tip behind the habitat ring (source coordinate approximately 98,510), extending away from the ship. The unchanged craft alpha fully masks the overlay, so no flame or artificial nozzle paints over the front of the ring. The PNG spacecraft/debris remain byte-identical. Playback JNI calls run on a dedicated decoder thread; the Canvas effect uses a sampled/extrapolated playback clock instead of querying native media from the UI thread.
