package devyana.kekita.printbridge.Helper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "settings.db";
    private static final int DATABASE_VERSION = 1;
    public static final String TABLE_SETTINGS = "settings";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_KEY = "setting_key";
    public static final String COLUMN_VALUE = "setting_value";

    public DatabaseHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableQuery = "CREATE TABLE " + TABLE_SETTINGS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_KEY + " TEXT UNIQUE, " +
                COLUMN_VALUE + " TEXT)";
        db.execSQL(createTableQuery);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETTINGS);
        onCreate(db);
    }

    /**
     * Menyimpan atau memperbarui sebuah pengaturan.
     * Menggunakan UNIQUE constraint pada 'setting_key' untuk otomatis replace.
     */
    public void saveSetting(String key, String value) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_KEY, key);
        values.put(COLUMN_VALUE, value);

        // insertWithOnConflict akan menggantikan baris jika key sudah ada (UPSERT)
        db.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        db.close();
    }

    /**
     * Mengambil sebuah pengaturan berdasarkan key.
     * Mengembalikan null jika tidak ditemukan.
     */
    public String getSetting(String key) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_SETTINGS,
                new String[]{COLUMN_VALUE},
                COLUMN_KEY + " = ?",
                new String[]{key},
                null, null, null);

        String value = null;
        if (cursor != null && cursor.moveToFirst()) {
            int valueIndex = cursor.getColumnIndex(COLUMN_VALUE);
            if (valueIndex != -1) {
                value = cursor.getString(valueIndex);
            }
            cursor.close();
        }
        db.close();
        return value;
    }

    /**
     * Mengambil semua pengaturan dari database dan mengembalikannya sebagai Map.
     * Key dari Map adalah setting_key, dan Value adalah setting_value.
     */
    public Map<String, String> getAllSettings() {
        Map<String, String> settingsMap = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_SETTINGS, null);

        if (cursor != null && cursor.moveToFirst()) {
            int keyIndex = cursor.getColumnIndex(COLUMN_KEY);
            int valueIndex = cursor.getColumnIndex(COLUMN_VALUE);

            do {
                if (keyIndex != -1 && valueIndex != -1) {
                    String key = cursor.getString(keyIndex);
                    String value = cursor.getString(valueIndex);
                    settingsMap.put(key, value);
                }
            } while (cursor.moveToNext());

            cursor.close();
        }
        db.close();
        return settingsMap;
    }

    /**
     * Menghapus semua pengaturan. Berguna untuk reset atau ganti akun.
     */
    public void clearAllSettings() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SETTINGS, null, null);
        db.close();
    }
}