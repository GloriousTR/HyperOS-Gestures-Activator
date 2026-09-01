package dev.glorioustr.hyperosgesturesactivator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;

public final class DiagnosticEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !DiagnosticEventContract.ACTION_EVENT.equals(intent.getAction())) {
            return;
        }

        int senderUid = getSentFromUid();
        int systemUiUid = resolveSystemUiUid(context);
        if (senderUid != Process.INVALID_UID && senderUid != systemUiUid) {
            DiagnosticDatabase.get(context).insert(new DiagnosticEvent(
                    0L,
                    System.currentTimeMillis(),
                    DiagnosticEvent.STATUS_FAILURE,
                    "security",
                    "reject-diagnostic-event",
                    "Rejected sender uid=" + senderUid
                            + ", expectedSystemUiUid=" + systemUiUid
                            + ", package=" + getSentFromPackage(),
                    context.getPackageName(),
                    Thread.currentThread().getName()));
            return;
        }

        String status = intent.getStringExtra(DiagnosticEventContract.EXTRA_STATUS);
        if (!DiagnosticEvent.STATUS_SUCCESS.equals(status)
                && !DiagnosticEvent.STATUS_FAILURE.equals(status)
                && !DiagnosticEvent.STATUS_INFO.equals(status)) {
            DiagnosticDatabase.get(context).insert(new DiagnosticEvent(
                    0L,
                    System.currentTimeMillis(),
                    DiagnosticEvent.STATUS_FAILURE,
                    "security",
                    "reject-invalid-diagnostic-status",
                    "Rejected status=" + status,
                    context.getPackageName(),
                    Thread.currentThread().getName()));
            return;
        }

        DiagnosticEvent event = new DiagnosticEvent(
                0L,
                intent.getLongExtra(
                        DiagnosticEventContract.EXTRA_TIMESTAMP,
                        System.currentTimeMillis()),
                status,
                intent.getStringExtra(DiagnosticEventContract.EXTRA_CATEGORY),
                intent.getStringExtra(DiagnosticEventContract.EXTRA_OPERATION),
                intent.getStringExtra(DiagnosticEventContract.EXTRA_DETAIL),
                intent.getStringExtra(DiagnosticEventContract.EXTRA_PROCESS),
                intent.getStringExtra(DiagnosticEventContract.EXTRA_THREAD));
        DiagnosticDatabase.get(context).insert(event);
    }

    private static int resolveSystemUiUid(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                    "com.android.systemui",
                    PackageManager.ApplicationInfoFlags.of(0L));
            return info.uid;
        } catch (Throwable throwable) {
            return Process.SYSTEM_UID;
        }
    }
}
