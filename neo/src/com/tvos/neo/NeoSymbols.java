package com.tvos.neo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/** Small vector-like control marks rendered at the actual display density. */
final class NeoSymbols {
    static Bitmap render(String symbol, int pixels, int color) {
        Bitmap image = Bitmap.createBitmap(Math.max(1, pixels), Math.max(1, pixels), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.scale(pixels / 32f, pixels / 32f);
        Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        pen.setColor(color);
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(1.8f);
        pen.setStrokeCap(Paint.Cap.ROUND);
        if ("input".equals(symbol)) {
            canvas.drawRoundRect(new RectF(4, 5, 28, 23), 3, 3, pen);
            canvas.drawLine(11, 28, 21, 28, pen);
            canvas.drawLine(16, 23, 16, 28, pen);
            canvas.drawLine(1, 14, 17, 14, pen);
            canvas.drawLine(13, 10, 17, 14, pen);
            canvas.drawLine(13, 18, 17, 14, pen);
        } else if ("timer".equals(symbol)) {
            canvas.drawCircle(16, 18, 11, pen);
            canvas.drawLine(12, 2, 20, 2, pen);
            canvas.drawLine(16, 2, 16, 6, pen);
            canvas.drawLine(16, 18, 16, 11, pen);
            canvas.drawLine(16, 18, 21, 21, pen);
        } else if ("shelf".equals(symbol)) {
            for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++)
                canvas.drawRoundRect(new RectF(4 + col * 14, 4 + row * 14, 14 + col * 14, 14 + row * 14), 2, 2, pen);
        } else {
            canvas.drawCircle(16, 16, 8, pen);
            canvas.drawCircle(16, 16, 3, pen);
            for (int i = 0; i < 8; i++) {
                canvas.save(); canvas.rotate(i * 45, 16, 16);
                canvas.drawLine(16, 3, 16, 7, pen); canvas.restore();
            }
        }
        return image;
    }
}
