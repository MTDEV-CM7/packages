
package com.android.mms.templates;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class TemplatesDb {

    SQLiteDatabase mDb;

    DbHelper mDbHelper;

    Context mContext;

    private static final String DB_NAME = "message_templates.db";

    private static final int DB_VERSION = 1;

    private static final String LOG_TAG = TemplatesDb.class.getCanonicalName();

    static class TemplateMetaData {
        static final String TABLE_NAME = "message_template";

        static final String TEMPLATE_ID = "_id";

        static final String TEMPLATE_TEXT = "text";
    }

    public TemplatesDb(Context ctx) {
        mContext = ctx;
        mDbHelper = new DbHelper(ctx, DB_NAME, null, DB_VERSION);
    }

    public void open() {
        mDb = mDbHelper.getWritableDatabase();
    }

    public void close() {
        mDb.close();
    }

    public boolean isOpen() {
        return mDb.isOpen();
    }

    public Cursor getAllTemplates() {
        return mDb.query(TemplateMetaData.TABLE_NAME, null, null, null, null, null, null);
    }

    public String[] getAllTemplatesText() {
        Cursor c = mDb.query(TemplateMetaData.TABLE_NAME, null, null, null, null, null, null);

        if (c != null && c.getCount() > 0) {

            String[] array = new String[c.getCount()];
            int colIndex = c.getColumnIndex(TemplateMetaData.TEMPLATE_TEXT);
            int i = 0;

            c.moveToFirst();

            do {
                array[i] = c.getString(colIndex);
                i++;
            } while (c.moveToNext());

            c.close();

            return array;
        } else {
            return new String[0];
        }
    }

    public String getTemplateTextFromId(long id) {

        Cursor cursor = null;
        String text = "";

        try {
            cursor = mDb.query(TemplateMetaData.TABLE_NAME, new String[] {
                TemplateMetaData.TEMPLATE_TEXT
            }, TemplateMetaData.TEMPLATE_ID + "=?", new String[] {
                String.valueOf(id)
            }, null, null, null);

            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                text = cursor.getString(0);
            }
        } catch (final Exception e) {
            Log.e(LOG_TAG, "Error during getting text: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return text;

    }

    public long insertTemplate(String text) {
        final ContentValues cv = new ContentValues();
        cv.put(TemplateMetaData.TEMPLATE_TEXT, text);
        return mDb.insert(TemplateMetaData.TABLE_NAME, null, cv);
    }

    public void updateTemplate(long id, String newText) {

        final ContentValues cv = new ContentValues();
        cv.put(TemplateMetaData.TEMPLATE_TEXT, newText);

        mDb.update(TemplateMetaData.TABLE_NAME, cv, TemplateMetaData.TEMPLATE_ID + " = ?",
                new String[] {
                    String.valueOf(id)
                });
    }

    public void deleteTemplate(long id) {
        mDb.delete(TemplateMetaData.TABLE_NAME, TemplateMetaData.TEMPLATE_ID + " = ?",
                new String[] {
                    String.valueOf(id)
                });
    }

    private static final String TEMPLATE_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS "
            + TemplateMetaData.TABLE_NAME + " (" + TemplateMetaData.TEMPLATE_ID
            + " integer primary key autoincrement, " + TemplateMetaData.TEMPLATE_TEXT
            + " text not null);";

    private class DbHelper extends SQLiteOpenHelper {

        public DbHelper(Context context, String name, CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(TEMPLATE_TABLE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        }

    }

}
