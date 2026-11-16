package com.tvos.neo;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only adapter for the television's hardware input registry. */
final class NeoInputs {
    static final class Source {
        String id, label;
        boolean disconnected, passthrough;
        int priority;
    }

    static List<Source> sources(Context context) {
        List<Source> result = new ArrayList<>();
        TvInputManager manager = context.getSystemService(TvInputManager.class);
        if (manager == null) return result;
        try {
            for (TvInputInfo descriptor : manager.getTvInputList()) {
                try {
                    if (descriptor.isHidden(context) || descriptor.getParentId() != null) continue;
                    if (!descriptor.getId().matches(".*/HW[0-9]+")) continue;
                    Source source = new Source();
                    source.id = descriptor.getId();
                    source.passthrough = descriptor.isPassthroughInput();
                    if (!source.passthrough && descriptor.getType() != TvInputInfo.TYPE_TUNER) continue;
                    CharSequence label = descriptor.loadCustomLabel(context);
                    if (label == null || label.length() == 0) label = descriptor.loadLabel(context);
                    source.label = label == null ? "TV input" : label.toString().replaceAll("([A-Za-z])(\\d)", "$1 $2");
                    source.disconnected = manager.getInputState(source.id) == TvInputManager.INPUT_STATE_DISCONNECTED;
                    source.priority = descriptor.getType() == TvInputInfo.TYPE_HDMI ? 0 : source.passthrough ? 1 : 2;
                    result.add(source);
                } catch (RuntimeException unavailable) { /* An OEM can revoke an entry during enumeration. */ }
            }
        } catch (RuntimeException unavailable) { return result; }
        result.sort(Comparator.comparingInt((Source item) -> item.priority).thenComparing(item -> item.label, String.CASE_INSENSITIVE_ORDER));
        return result;
    }
}
