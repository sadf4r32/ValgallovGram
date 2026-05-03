/*
 * Valgallov Auto Join — automatically subscribes the user to the brand channel
 * once after first successful login.
 */

package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

public class ValgallovAutoJoin {

    // Brand channel users are auto-subscribed to on first launch
    public static final String CHANNEL_USERNAME = "fucktrollingh";

    private static final String PREFS_KEY = "valgallov_autojoin_done_v1";

    public static void attempt(int accountIndex) {
        try {
            UserConfig userConfig = UserConfig.getInstance(accountIndex);
            if (!userConfig.isClientActivated()) {
                return;
            }
            SharedPreferences prefs = MessagesController.getGlobalMainSettings();
            String key = PREFS_KEY + "_" + accountIndex;
            if (prefs.getBoolean(key, false)) {
                return;
            }
            // Mark optimistically so we don't spam the API on every launch
            prefs.edit().putBoolean(key, true).apply();

            AndroidUtilities.runOnUIThread(() -> doResolveAndJoin(accountIndex), 5000);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void doResolveAndJoin(int accountIndex) {
        if (TextUtils.isEmpty(CHANNEL_USERNAME)) {
            return;
        }
        TLRPC.TL_contacts_resolveUsername resolveReq = new TLRPC.TL_contacts_resolveUsername();
        resolveReq.username = CHANNEL_USERNAME;
        ConnectionsManager.getInstance(accountIndex).sendRequest(resolveReq, (response, error) -> {
            if (error != null || !(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("Valgallov auto-join: resolve failed " + (error != null ? error.text : "null"));
                }
                return;
            }
            TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
            MessagesController.getInstance(accountIndex).putUsers(res.users, false);
            MessagesController.getInstance(accountIndex).putChats(res.chats, false);

            long channelId = 0;
            if (res.chats != null && !res.chats.isEmpty()) {
                channelId = res.chats.get(0).id;
            }
            if (channelId == 0) {
                return;
            }
            TLRPC.InputChannel inputChannel = MessagesController.getInstance(accountIndex).getInputChannel(channelId);
            if (inputChannel == null) {
                return;
            }
            TLRPC.TL_channels_joinChannel joinReq = new TLRPC.TL_channels_joinChannel();
            joinReq.channel = inputChannel;
            ConnectionsManager.getInstance(accountIndex).sendRequest(joinReq, (joinResp, joinErr) -> {
                if (joinErr != null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("Valgallov auto-join: join failed " + joinErr.text);
                    }
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("Valgallov auto-join: joined @" + CHANNEL_USERNAME);
                    }
                    if (joinResp instanceof TLRPC.Updates) {
                        MessagesController.getInstance(accountIndex).processUpdates((TLRPC.Updates) joinResp, false);
                    }
                }
            });
        });
    }
}
