package com.lsi.businesscard;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "business_cards.db";
    public static final int DB_VERSION = 2;
    public static final String[] TEXT_COLUMNS = {
            "created_at","updated_at","name","industry","location_text",
            "company1","department1","title1","company2","department2","title2","company3","department3","title3",
            "mobile1","mobile2","mobile3","phone1","phone2","phone3","fax1","fax2","fax3",
            "email1","email2","email3","address1","address2","address3","website","instant_message","sns_account",
            "nickname","birthday","anniversary","note1","note2","note3",
            "image_front","image_back","image_front2","image_back2","group_name","source"
    };

    public DbHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        StringBuilder sql = new StringBuilder("CREATE TABLE contacts (_id INTEGER PRIMARY KEY AUTOINCREMENT, favorite INTEGER NOT NULL DEFAULT 0");
        for (String col : TEXT_COLUMNS) sql.append(", ").append(col).append(" TEXT NOT NULL DEFAULT ''");
        sql.append(")");
        db.execSQL(sql.toString());
        db.execSQL("CREATE INDEX idx_contacts_name ON contacts(name)");
        db.execSQL("CREATE INDEX idx_contacts_mobile1 ON contacts(mobile1)");
        db.execSQL("CREATE INDEX idx_contacts_email1 ON contacts(email1)");
        db.execSQL("CREATE INDEX idx_contacts_group ON contacts(group_name)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE contacts ADD COLUMN updated_at TEXT NOT NULL DEFAULT ''"); }
            catch (Exception ignored) { }
            try { db.execSQL("CREATE INDEX idx_contacts_group ON contacts(group_name)"); }
            catch (Exception ignored) { }
        }
    }

    public long insert(Contact x) { return getWritableDatabase().insert("contacts", null, x.toContentValues()); }

    public int update(Contact x) {
        return getWritableDatabase().update("contacts", x.toContentValues(), "_id=?", new String[]{String.valueOf(x.id)});
    }

    public void delete(long id) { getWritableDatabase().delete("contacts", "_id=?", new String[]{String.valueOf(id)}); }

    public void deleteWithUnusedImages(long id) {
        Contact c = get(id);
        if (c == null) return;
        delete(id);
        for (String key : imageColumns()) {
            String path = c.get(key);
            if (!path.isEmpty() && !isImagePathReferenced(path)) {
                try { new File(path).delete(); } catch (Exception ignored) { }
            }
        }
    }

    public void cleanupImageIfUnused(String path) {
        if (path == null || path.isEmpty() || isImagePathReferenced(path)) return;
        try { new File(path).delete(); } catch (Exception ignored) { }
    }

    private boolean isImagePathReferenced(String path) {
        String where = "image_front=? OR image_back=? OR image_front2=? OR image_back2=?";
        Cursor c = getReadableDatabase().query("contacts", new String[]{"_id"}, where,
                new String[]{path,path,path,path}, null, null, null, "1");
        try { return c.moveToFirst(); } finally { c.close(); }
    }

    public Contact get(long id) {
        Cursor c = getReadableDatabase().query("contacts", null, "_id=?", new String[]{String.valueOf(id)}, null, null, null);
        try { return c.moveToFirst() ? Contact.fromCursor(c) : null; } finally { c.close(); }
    }

    public List<Contact> search(String q, boolean favoritesOnly, String group, int sort) {
        List<Contact> out = new ArrayList<>();
        ArrayList<String> parts = new ArrayList<>();
        ArrayList<String> args = new ArrayList<>();
        if (q != null && !q.trim().isEmpty()) {
            String like = "%" + q.trim() + "%";
            String[] cols = {"name","company1","company2","company3","department1","department2","department3","title1","title2","title3",
                    "mobile1","mobile2","mobile3","phone1","phone2","phone3","email1","email2","email3","address1","address2","address3","website","group_name","note1","note2","note3"};
            StringBuilder b = new StringBuilder("(");
            for (int i=0;i<cols.length;i++) { if (i>0) b.append(" OR "); b.append(cols[i]).append(" LIKE ?"); args.add(like); }
            b.append(")"); parts.add(b.toString());
        }
        if (favoritesOnly) parts.add("favorite=1");
        if (group != null && !group.isEmpty()) { parts.add("group_name=?"); args.add(group); }
        String where = parts.isEmpty() ? null : join(" AND ", parts);
        Cursor c = getReadableDatabase().query("contacts", null, where, args.isEmpty()?null:args.toArray(new String[0]), null, null,
                orderFor(sort));
        try { while (c.moveToNext()) out.add(Contact.fromCursor(c)); } finally { c.close(); }
        return out;
    }

    public static final String[] SORT_LABELS = {"입력순 · 먼저 입력한 순서", "입력순 · 최근 입력한 순서", "이름 · 가나다순", "이름 · 역순", "회사 · 가나다순", "회사 · 역순", "최근 수정한 순서"};
    public static String orderFor(int sort) {
        switch(sort) {
            case 1: return "_id DESC";
            case 2: return "CASE WHEN trim(name)='' THEN 1 ELSE 0 END, name COLLATE LOCALIZED ASC, _id ASC";
            case 3: return "CASE WHEN trim(name)='' THEN 1 ELSE 0 END, name COLLATE LOCALIZED DESC, _id ASC";
            case 4: return "CASE WHEN trim(company1)='' THEN 1 ELSE 0 END, company1 COLLATE LOCALIZED ASC, _id ASC";
            case 5: return "CASE WHEN trim(company1)='' THEN 1 ELSE 0 END, company1 COLLATE LOCALIZED DESC, _id ASC";
            case 6: return "CASE WHEN updated_at='' THEN 1 ELSE 0 END, updated_at DESC, _id DESC";
            default: return "_id ASC";
        }
    }
    public List<Contact> search(String q, boolean favoritesOnly, String group) { return search(q, favoritesOnly, group, 1); }
    public List<Contact> search(String q, boolean favoritesOnly) { return search(q, favoritesOnly, ""); }

    public List<String> groups() {
        ArrayList<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT group_name FROM contacts WHERE trim(group_name)<>'' ORDER BY group_name COLLATE LOCALIZED", null);
        try { while (c.moveToNext()) out.add(c.getString(0)); } finally { c.close(); }
        return out;
    }

    public long findDuplicateId(Contact x) {
        if (x == null) return -1;
        for (Contact y : all()) if (samePerson(x, y)) return y.id;
        return -1;
    }

    public boolean isDuplicate(Contact x) { return findDuplicateId(x) > 0; }

    public Contact mergeInto(long id, Contact incoming) {
        Contact base = get(id); if (base == null) return null;
        base.favorite = Math.max(base.favorite, incoming.favorite);
        for (String col : TEXT_COLUMNS) {
            String old = base.get(col), add = incoming.get(col);
            if (old.trim().isEmpty() && !add.trim().isEmpty()) base.put(col, add);
        }
        base.put("updated_at", now());
        update(base); return base;
    }

    private static boolean samePerson(Contact a, Contact b) {
        String an=normText(a.get("name")), bn=normText(b.get("name"));
        String ac=firstCompany(a), bc=firstCompany(b);
        Set<String> ap=phones(a), bp=phones(b);
        Set<String> ae=emails(a), be=emails(b);
        boolean sameName = !an.isEmpty() && an.equals(bn);
        boolean sameCompany = !ac.isEmpty() && ac.equals(bc);
        boolean phoneHit = intersects(ap,bp);
        boolean emailHit = intersects(ae,be);
        if (sameName && (sameCompany || phoneHit || emailHit)) return true;
        if (sameName && ac.isEmpty() && bc.isEmpty() && ap.isEmpty() && bp.isEmpty() && ae.isEmpty() && be.isEmpty()) return true;
        if ((an.isEmpty() || bn.isEmpty()) && sameCompany && (phoneHit || emailHit)) return true;
        return false;
    }

    private static String firstCompany(Contact c) {
        for (String k : new String[]{"company1","company2","company3"}) { String v=normText(c.get(k)); if(!v.isEmpty()) return v; }
        return "";
    }
    private static Set<String> phones(Contact c) {
        HashSet<String> s=new HashSet<>();
        for(String k:new String[]{"mobile1","mobile2","mobile3","phone1","phone2","phone3"}) { String v=normPhone(c.get(k)); if(!v.isEmpty())s.add(v); }
        return s;
    }
    private static Set<String> emails(Contact c) {
        HashSet<String> s=new HashSet<>();
        for(String k:new String[]{"email1","email2","email3"}) { String v=c.get(k).trim().toLowerCase(Locale.ROOT); if(!v.isEmpty())s.add(v); }
        return s;
    }
    private static boolean intersects(Set<String>a,Set<String>b){ for(String x:a)if(b.contains(x))return true; return false; }
    private static String normText(String s){return s==null?"":s.trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);}
    private static String normPhone(String s) { return s == null ? "" : s.replaceAll("[^0-9+]", ""); }
    private static String join(String sep, List<String> s){StringBuilder b=new StringBuilder();for(String x:s){if(b.length()>0)b.append(sep);b.append(x);}return b.toString();}
    private static String now(){return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.KOREA).format(new java.util.Date());}
    private static String[] imageColumns(){return new String[]{"image_front","image_back","image_front2","image_back2"};}

    public List<Contact> all() {
        ArrayList<Contact> out=new ArrayList<>();
        Cursor c=getReadableDatabase().query("contacts",null,null,null,null,null,"_id ASC");
        try{while(c.moveToNext())out.add(Contact.fromCursor(c));}finally{c.close();}
        return out;
    }

    public int count() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM contacts", null);
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public void setFavorite(long id, boolean on) {
        ContentValues cv = new ContentValues(); cv.put("favorite", on ? 1 : 0); cv.put("updated_at", now());
        getWritableDatabase().update("contacts", cv, "_id=?", new String[]{String.valueOf(id)});
    }
}
