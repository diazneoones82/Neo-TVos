package com.tvos.neo;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Optional restrained ion exhaust, anchored to the movie's existing craft. */
final class NeoEngineGlow extends View {
    interface Playback { int position(); }
    private final Playback playback;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean active = true;
    NeoEngineGlow(Context context, Playback playback) {
        super(context); this.playback = playback; setFocusable(false); setClickable(false);
    }
    static float shipX(float seconds) { return -640 + 100 * seconds; }
    static float shipY(float seconds) { return 632.4f - 5.4f * seconds; }
    static float breathing(float seconds) { return .55f + .45f * (float) (.5 - .5 * Math.cos(2 * Math.PI * seconds / 4)); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); if (!active) return;
        int milliseconds = playback.position();
        if (milliseconds >= 0) {
            float seconds = (milliseconds % 32000) / 1000f;
            float scale = Math.max(getWidth() / 1920f, getHeight() / 1080f);
            canvas.save();
            canvas.translate((getWidth() - 1920 * scale) / 2, (getHeight() - 1080 * scale) / 2);
            canvas.scale(scale, scale);
            canvas.translate(shipX(seconds) + 280, shipY(seconds) + 210);
            canvas.rotate((float) Math.toDegrees(.055 * Math.sin(2 * Math.PI * seconds / 32)));
            float breath = breathing(seconds);
            // Same 500px sprite, centered in the movie's 560x420 banking box.
            for (float y : new float[]{-12, 12}) {
                float nozzle = -221, length = 31 + 16 * breath;
                paint.setShader(new RadialGradient(nozzle - 8, y, 25,
                        new int[]{Color.argb((int) (120 * breath), 65, 170, 255), Color.TRANSPARENT}, null, Shader.TileMode.CLAMP));
                canvas.drawOval(new RectF(nozzle - length - 10, y - 13, nozzle + 9, y + 13), paint);
                paint.setShader(new LinearGradient(nozzle - length, y, nozzle + 2, y,
                        new int[]{Color.TRANSPARENT, Color.argb((int) (155 * breath), 47, 149, 255), Color.argb((int) (220 * breath), 190, 235, 255)},
                        new float[]{0, .78f, 1}, Shader.TileMode.CLAMP));
                canvas.drawOval(new RectF(nozzle - length, y - 3.4f * breath, nozzle + 3, y + 3.4f * breath), paint);
                paint.setShader(null);
            }
            canvas.restore();
        }
        postInvalidateOnAnimation();
    }
    void stop() { active = false; }
}
