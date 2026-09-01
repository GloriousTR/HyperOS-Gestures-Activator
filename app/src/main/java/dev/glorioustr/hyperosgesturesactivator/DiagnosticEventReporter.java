package dev.glorioustr.hyperosgesturesactivator;

import android.app.BroadcastOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

final class DiagnosticEventReporter {
    private DiagnosticEventReporter() {
    }

    static void send(
            Context context,
            String status,
            String category,
            String operation,
            String detail,
            String processName) {
        Intent intent = new Intent(DiagnosticEventContract.ACTION_EVENT)
                .setComponent(new ComponentName(
                        BuildConfig.APPLICATION_ID,
                        DiagnosticEventReceiver.class.getName()))
                .putExtra(DiagnosticEventContract.EXTRA_TIMESTAMP,
                        System.currentTimeMillis())
                .putExtra(DiagnosticEventContract.EXTRA_STATUS, status)
                .putExtra(DiagnosticEventContract.EXTRA_CATEGORY, category)
                .putExtra(DiagnosticEventContract.EXTRA_OPERATION, operation)
                .putExtra(DiagnosticEventContract.EXTRA_DETAIL, detail)
                .putExtra(DiagnosticEventContract.EXTRA_PROCESS, processName)
                .putExtra(DiagnosticEventContract.EXTRA_THREAD,
                        Thread.currentThread().getName());
        BroadcastOptions options = BroadcastOptions.makeBasic()
                .setShareIdentityEnabled(true);
        context.sendBroadcast(intent, null, options.toBundle());
    }
}
