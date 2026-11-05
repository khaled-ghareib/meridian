package com.meridian.finance;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives incoming text messages. Bank/wallet messages that mention an EGP amount are
 * saved for Meridian to read; nothing is sent anywhere. Long messages arrive in parts,
 * so the parts are joined per sender first.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;

        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        long time = System.currentTimeMillis();
        for (SmsMessage p : parts) {
            if (p == null) continue;
            String from = p.getDisplayOriginatingAddress();
            if (from == null) from = "";
            StringBuilder sb = bodies.get(from);
            if (sb == null) { sb = new StringBuilder(); bodies.put(from, sb); }
            if (p.getMessageBody() != null) sb.append(p.getMessageBody());
        }
        boolean saved = false;
        for (Map.Entry<String, StringBuilder> e : bodies.entrySet()) {
            String body = e.getValue().toString();
            if (SmsStore.looksFinancial(body)) {
                SmsStore.add(context, e.getKey(), body, time);
                saved = true;
            }
        }
        // If Meridian is open right now, let it pick the message up immediately.
        if (saved) MainActivity.notifySmsArrived();
    }
}
