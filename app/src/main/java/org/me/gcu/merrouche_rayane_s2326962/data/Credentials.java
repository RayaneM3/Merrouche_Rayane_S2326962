package org.me.gcu.merrouche_rayane_s2326962.data;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;

/**
 * Traffic Scotland client ID / key. They are stored in local.properties (not shared on GitHub),
 * injected into the manifest <meta-data> by the secrets-gradle-plugin, and read back here
 * (approach described in "Protecting your API keys when using GitHub").
 */
public class Credentials
{
    private static final String TAG = "Credentials";
    private final String clientId;
    private final String clientKey;

    public Credentials(String clientId, String clientKey)
    {
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientKey = clientKey == null ? "" : clientKey.trim();
    }

    public static Credentials fromManifest(Context context)
    {
        String id = "", key = "";
        try
        {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            Bundle metaData = info.metaData;
            if (metaData != null)
            {
                id = metaData.getString(context.getPackageName() + ".TS_CLIENT_ID", "");
                key = metaData.getString(context.getPackageName() + ".TS_CLIENT_KEY", "");
            }
        }
        catch (PackageManager.NameNotFoundException e)
        {
            Log.e(TAG, "Cannot read meta-data", e);
        }
        return new Credentials(id, key);
    }

    /** false while local.properties still has the placeholder values */
    public boolean isConfigured()
    {
        return !clientId.isEmpty() && !clientKey.isEmpty()
                && !clientId.equals("YOUR_ID_HERE") && !clientKey.equals("YOUR_KEY_HERE");
    }

    /** Value of the Authorization header expected by the DATEX II API: Base64("id:key") */
    public String authorizationHeader()
    {
        byte[] bytes = (clientId + ":" + clientKey).getBytes(StandardCharsets.UTF_8);
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
