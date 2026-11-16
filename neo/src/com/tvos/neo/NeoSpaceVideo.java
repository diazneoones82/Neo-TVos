package com.tvos.neo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

/** Hardware-decoded original scene, with an optional phase-locked engine glow. */
final class NeoSpaceVideo extends FrameLayout implements TextureView.SurfaceTextureListener {
    private final TextureView texture;
    private final NeoEngineGlow engines;
    private MediaPlayer player;
    private boolean stopped;
    NeoSpaceVideo(Context context) {
        super(context); setBackgroundColor(Color.BLACK);
        texture = new TextureView(context); texture.setSurfaceTextureListener(this);
        addView(texture, new LayoutParams(-1, -1));
        engines = context.getSharedPreferences("neo", 0).getBoolean("saver_ship_engines", false)
                ? new NeoEngineGlow(context, this::playbackPosition) : null;
        if (engines != null) addView(engines, new LayoutParams(-1, -1));
    }
    private int playbackPosition() {
        try { return player != null && player.isPlaying() ? player.getCurrentPosition() : -1; }
        catch (IllegalStateException unavailable) { return -1; }
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture source, int width, int height) {
        if (stopped) return;
        try {
            player = new MediaPlayer();
            try (AssetFileDescriptor file = getContext().getAssets().openFd("neo/video/imaginary-space-v1.mp4")) {
                player.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
            }
            Surface surface = new Surface(source);
            try { player.setSurface(surface); } finally { surface.release(); }
            player.setLooping(true); player.setVolume(0, 0);
            player.setOnPreparedListener(media -> { if (!stopped) { resize(); media.start(); } });
            player.setOnErrorListener((media, what, extra) -> { stop(); return true; });
            player.prepareAsync();
        } catch (Exception unavailable) { stop(); }
    }
    private void resize() {
        if (player == null || player.getVideoWidth() == 0 || texture.getWidth() == 0 || texture.getHeight() == 0) return;
        float vw = player.getVideoWidth(), vh = player.getVideoHeight();
        float w = texture.getWidth(), h = texture.getHeight();
        float scale = Math.max(w / vw, h / vh);
        Matrix transform = new Matrix();
        transform.setScale(vw * scale / w, vh * scale / h, w / 2, h / 2);
        texture.setTransform(transform);
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture source, int width, int height) { resize(); }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture source) { }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture source) { stop(); return true; }
    void stop() {
        stopped = true;
        if (engines != null) engines.stop();
        if (player != null) { player.release(); player = null; }
    }
    @Override protected void onDetachedFromWindow() { stop(); super.onDetachedFromWindow(); }
}
