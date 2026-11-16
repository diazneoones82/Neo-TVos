# Visual asset provenance and generation

The cinematic black-hole background derives from NASA Goddard / Jeremy Schnittman's edge-on accretion-disk visualization: https://svs.gsfc.nasa.gov/13326/ . The pale-gold grade and slow spacecraft pacing use Interstellar as a stylistic reference; they do not use footage from the film. References: https://www.dneg.com/news/new-black-hole-imagery and https://authors.library.caltech.edu/records/njdcq-95891 .

The original spacecraft cutout was generated using the built-in image_gen tool with true alpha transparency. Saved project asset: media-sources/cinematic-craft-v1.png .

Final prompt used:
“Use case: photorealistic-natural / cinematic visual effects asset. Create an original physically believable deep-space exploration spacecraft as an isolated transparent cutout for compositing into an Interstellar-inspired black-hole screensaver. A mature hard-science-fiction film miniature, not an illustration or game icon: a compact circular rotating habitat ring with twelve weathered ivory modules, intricate dark central trusses, slender antennas, metallic radiators, layered thermal insulation and subtle micro-surface wear. View from an oblique three-quarter angle, seeing the front and near underside, the vessel oriented traveling toward the right and slightly away. Warm pale-gold rim light from above-right, cool faint blue fill, deep realistic shadows and fine specular highlights. Entire craft fully inside the frame with comfortable transparent margins. High detail, photographic sharpness appropriate to a movie miniature, no text, no logos, no background, no glow aura, no giant thruster flames, no cartoon styling, no triangular fighter jet, no exaggerated neon. Genuine alpha transparency.”

Video processing uses FFmpeg. The 4-second background cycle crossfades its tail into its head, receives a warm pale-gold grade, and is motion-interpolated to 60 fps. The 32-second final H.264 video composites the original detailed craft with slow drift and bank. Its trajectory begins and ends off-screen, so no visible craft position reset occurs at the loop seam. All motion is baked into the video; the previous canvas fighter/blue-exhaust overlay is not used.

Build scripts: media-sources/render-cinematic-v2.py and media-sources/render-cinematic-smooth.py. Final project video: neo/assets/neo/video/cinematic-black-hole-v2.mp4 .

The companion reuses the launcher's lake background and theme JSON token palettes. No film frame or movie spaceship asset is packaged.

## Starfield and debris update

Built-in image_gen generated two additional project assets:
- media-sources/cosmic-background-v1.png
- media-sources/asteroid-fragment-v1.png (alpha transparency preserved)

Background prompt:
“Use case: photorealistic-natural cinematic deep-space background for a realistic black-hole screensaver. Create a wide 16:9 high-resolution astronomical star field with convincing depth: many tiny sharp distant stars of varied brightness, faint dusty blue and violet galactic wisps, a few small distant spiral galaxies, very subtle warm interstellar dust. Quiet, mature science-fiction cinematography, dark charcoal/navy rather than empty pitch black, rich but restrained natural colors, no neon fantasy clouds. Keep the central third visually quiet and relatively dark for a black-hole composite; place the slightly brighter galaxy detail toward the far upper left and far right edges. No spacecraft, no black hole, no text, no logos, no large foreground planets. Pinpoint stars, realistic exposures and photographic detail.”

Debris prompt:
“Photorealistic cinematic visual-effects cutout of ONE small irregular rocky asteroid fragment, isolated on genuine transparent background. Dark charcoal silicate rock with pitted craters, fractured edges and tiny metallic mineral specks, three-dimensional natural asymmetrical shape, soft warm rim light from the upper right and cool very faint blue fill, deep natural shadows. Entire rock centered and fully within frame with transparent margins. This will be a distant small drifting object in a realistic deep-space black-hole movie loop. No scene, no stars, no spacecraft, no flame, no smoke, no halo, no words, no cartoon or illustration.”

media-sources/render-cinematic-stars.py composites this field behind the black-hole footage and adds three small tumbling fragments on periodic paths. A feathered shadow mask keeps the central black hole opaque. Starfield and debris phases match at the 32-second boundary. Final video: neo/assets/neo/video/cinematic-black-hole-v3.mp4 . The detailed spacecraft, pale-gold grade, 1080p60 encoding and seamless cycle are retained.
