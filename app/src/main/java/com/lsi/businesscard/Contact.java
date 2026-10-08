package com.lsi.businesscard;

import android.content.ContentValues;
import android.database.Cursor;
import java.util.LinkedHashMap;
import java.util.Map;

public class Contact {
    public long id = -1;
    public int favorite = 0;
    public final Map<String, String> values = new LinkedHashMap<>();

    public String get(String key) {
        String v = values.get(key);
        return v == null ? "" : v;
    }

    public void put(String key, String value) {
        values.put(key, value == null ? "" : value);
    }

    public ContentValues toContentValues() {
        ContentValues cv = new ContentValues();
        cv.put("favorite", favorite);
        for (String col : DbHelper.TEXT_COLUMNS) cv.put(col, get(col));
        return cv;
    }

    public static Contact fromCursor(Cursor c) {
        Contact x = new Contact();
        x.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        x.favorite = c.getInt(c.getColumnIndexOrThrow("favorite"));
        for (String col : DbHelper.TEXT_COLUMNS) {
            int i = c.getColumnIndex(col);
            if (i >= 0 && !c.isNull(i)) x.put(col, c.getString(i));
        }
        return x;
    }
}
