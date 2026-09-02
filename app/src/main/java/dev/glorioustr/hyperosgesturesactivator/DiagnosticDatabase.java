package dev.glorioustr.hyperosgesturesactivator;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class DiagnosticDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "live_diagnostics.db";
    private static final int DATABASE_VERSION = 1;
    private static final int SCREEN_EVENT_LIMIT = 1000;

    private static DiagnosticDatabase instance;

    static synchronized DiagnosticDatabase get(Context context) {
        if (instance == null) {
            Context storageContext = context.createDeviceProtectedStorageContext();
            instance = new DiagnosticDatabase(storageContext);
        }
        return instance;
    }

    private DiagnosticDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE events ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "timestamp INTEGER NOT NULL,"
                + "status TEXT NOT NULL,"
                + "category TEXT NOT NULL,"
                + "operation TEXT NOT NULL,"
                + "detail TEXT NOT NULL,"
                + "process_name TEXT NOT NULL,"
                + "thread_name TEXT NOT NULL)");
        database.execSQL("CREATE INDEX events_status_id ON events(status, _id DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        throw new IllegalStateException(
                "Unsupported diagnostics database upgrade " + oldVersion + " -> " + newVersion);
    }

    long insert(DiagnosticEvent event) {
        ContentValues values = new ContentValues();
        values.put("timestamp", event.timestamp);
        values.put("status", sanitize(event.status, 32));
        values.put("category", sanitize(event.category, 128));
        values.put("operation", sanitize(event.operation, 256));
        values.put("detail", sanitize(event.detail, 32_768));
        values.put("process_name", sanitize(event.processName, 256));
        values.put("thread_name", sanitize(event.threadName, 256));
        return getWritableDatabase().insertOrThrow("events", null, values);
    }

    List<DiagnosticEvent> latest(String statusFilter) {
        return queryEvents(statusFilter, String.valueOf(SCREEN_EVENT_LIMIT));
    }

    List<DiagnosticEvent> all() {
        return queryEvents(null, null);
    }

    private List<DiagnosticEvent> queryEvents(String statusFilter, String limit) {
        String selection = null;
        String[] selectionArgs = null;
        if (statusFilter != null) {
            selection = "status=?";
            selectionArgs = new String[]{statusFilter};
        }
        List<DiagnosticEvent> events = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                "events",
                new String[]{"_id", "timestamp", "status", "category", "operation",
                        "detail", "process_name", "thread_name"},
                selection,
                selectionArgs,
                null,
                null,
                "_id DESC",
                limit)) {
            while (cursor.moveToNext()) {
                events.add(new DiagnosticEvent(
                        cursor.getLong(0),
                        cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6),
                        cursor.getString(7)));
            }
        }
        return events;
    }

    Counts counts() {
        long total = scalar("SELECT COUNT(*) FROM events");
        long success = scalar("SELECT COUNT(*) FROM events WHERE status='SUCCESS'");
        long failure = scalar("SELECT COUNT(*) FROM events WHERE status='FAILURE'");
        long info = scalar("SELECT COUNT(*) FROM events WHERE status='INFO'");
        return new Counts(total, success, failure, info);
    }

    void clear() {
        getWritableDatabase().delete("events", null, null);
    }

    private long scalar(String sql) {
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        }
    }

    private static String sanitize(String value, int maxLength) {
        String safe = value == null ? "" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    static final class Counts {
        final long total;
        final long success;
        final long failure;
        final long info;

        Counts(long total, long success, long failure, long info) {
            this.total = total;
            this.success = success;
            this.failure = failure;
            this.info = info;
        }
    }
}
