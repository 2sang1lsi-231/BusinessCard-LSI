package com.lsi.businesscard;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Minimal app-private provider used only to hand a writable camera target to external camera apps. */
public class CardFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.lsi.businesscard.fileprovider";

    public static File newCameraFile(Context ctx, String prefix) throws Exception {
        File dir = new File(ctx.getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("카메라 임시 폴더를 만들 수 없습니다.");
        return new File(dir, prefix + "_" + System.currentTimeMillis() + ".jpg");
    }

    public static Uri uriFor(Context ctx, File file) throws Exception {
        File dir = new File(ctx.getCacheDir(), "camera");
        String root = dir.getCanonicalPath() + File.separator;
        if (!file.getCanonicalPath().startsWith(root)) throw new SecurityException("허용되지 않은 파일 경로");
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.getName()).build();
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (getContext() == null || uri == null || !AUTHORITY.equals(uri.getAuthority())) throw new FileNotFoundException();
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("..")) throw new FileNotFoundException();
        try {
            File dir = new File(getContext().getCacheDir(), "camera");
            File file = new File(dir, name);
            String root = dir.getCanonicalPath() + File.separator;
            if (!file.getCanonicalPath().startsWith(root)) throw new FileNotFoundException();
            return file;
        } catch (Exception e) { throw new FileNotFoundException(); }
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return uri!=null&&uri.getLastPathSegment()!=null&&uri.getLastPathSegment().endsWith(".vcf")?"text/vcard":"image/jpeg"; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = resolve(uri);
        int flags = ParcelFileDescriptor.MODE_READ_ONLY;
        if (mode != null && mode.contains("w")) flags = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
        return ParcelFileDescriptor.open(f, flags);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File f = resolve(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (Exception e) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
