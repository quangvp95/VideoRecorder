package com.junerver.videorecorder.pip;

import android.content.Context;
import android.view.WindowManager;

public class PipManager {
    private final Context mContext;
    private PipView mPipView;

    public PipManager(Context context) {
        this.mContext = context;
    }

    public void enterPip() {
        WindowManager windowManager = (WindowManager) mContext.getSystemService(
                Context.WINDOW_SERVICE);

        mPipView = new PipView(mContext);
        mPipView.addToWindow(windowManager);

    }

}
