package dev.glorioustr.hyperosgesturesactivator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

public final class DiagnosticEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null
                || !DiagnosticEventContract.ACTION_EVENT.equals(intent.getAction())) {
            return;
        }

        int senderUid = getSentFromUid();
        if (senderUid != Process.SYSTEM_UID) {
            DiagnosticDatabase.get(context).insert(new DiagnosticEvent(
                    0L,
                    System.currentTimeMillis(),
                    DiagnosticEvent.STATUS_FAILURE,
                    "security",
                    "reject-diagnostic-event",
                    "Rejected sender uid=" + senderUid
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
}
