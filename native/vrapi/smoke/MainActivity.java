package moe.crx.overport.vrapismoke;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public final class MainActivity extends Activity {
    private static final String TAG = "VrApiVrxSmoke";

    static {
        System.loadLibrary("vrapi");
        System.loadLibrary("vrx_smoke");
    }

    private native int runSmoke();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new Thread(() -> {
            int result = runSmoke();
            Log.i(TAG, "SMOKE_RESULT=" + result);
            runOnUiThread(this::finish);
        }, "vrx-vrapi-smoke").start();
    }
}
