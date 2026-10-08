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

/** App-private camera targets and read-only exported attachments, with per-URI grants. */
public class CardFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.lsi.businesscard.fileprovider";

    public static File newCameraFile(Context ctx, String prefix) throws Exception {
        File dir = new File(ctx.getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("카메라 임시 폴더를 만들 수 없습니다.");
        return new File(dir, prefix + "_" + System.currentTimeMillis() + ".jpg");
    }

    public static Uri uriFor(Context ctx, File file) throws Exception {
        for(String folder:new String[]{"camera","exports"}){
            File dir=new File(ctx.getCacheDir(),folder);
            if(file.getParentFile()!=null&&file.getParentFile().getCanonicalPath().equals(dir.getCanonicalPath())&&file.getCanonicalPath().startsWith(dir.getCanonicalPath()+File.separator))return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(folder).appendPath(file.getName()).build();
        }
        throw new SecurityException("허용되지 않은 파일 경로");
    }
    private File resolve(Uri uri) throws FileNotFoundException {
        if(getContext()==null||uri==null||!"content".equals(uri.getScheme())||!AUTHORITY.equals(uri.getAuthority()))throw new FileNotFoundException();
        java.util.List<String> parts=uri.getPathSegments();String folder,name;
        if(parts.size()==1){folder="camera";name=parts.get(0);}else if(parts.size()==2){folder=parts.get(0);name=parts.get(1);}else throw new FileNotFoundException();
        if((!folder.equals("camera")&&!folder.equals("exports"))||name.isEmpty()||name.contains("/")||name.contains("\\")||name.contains(".."))throw new FileNotFoundException();
        try{File dir=new File(getContext().getCacheDir(),folder);File file=new File(dir,name);if(!file.getCanonicalPath().startsWith(dir.getCanonicalPath()+File.separator))throw new FileNotFoundException();return file;}catch(Exception e){throw new FileNotFoundException();}
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        String name=uri==null?null:uri.getLastPathSegment();if(name==null)return "application/octet-stream";name=name.toLowerCase(java.util.Locale.ROOT);
        if(name.endsWith(".xlsx"))return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if(name.endsWith(".csv"))return "text/csv";if(name.endsWith(".vcf"))return "text/vcard";if(name.endsWith(".zip"))return "application/zip";if(name.endsWith(".png"))return "image/png";if(name.endsWith(".jpg")||name.endsWith(".jpeg"))return "image/jpeg";return "application/octet-stream";
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        File f=resolve(uri);boolean export=uri.getPathSegments().size()==2&&uri.getPathSegments().get(0).equals("exports");
        if(export){if(!"r".equals(mode))throw new FileNotFoundException("내보내기 파일은 읽기 전용입니다.");return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);}
        if("r".equals(mode))return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);
        if("w".equals(mode)||"wt".equals(mode))return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE);
        if("rw".equals(mode)||"rwt".equals(mode))return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_WRITE|ParcelFileDescriptor.MODE_CREATE|("rwt".equals(mode)?ParcelFileDescriptor.MODE_TRUNCATE:0));
        throw new FileNotFoundException("지원하지 않는 파일 접근 방식");
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try{File f=resolve(uri);String[] cols=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor result=new MatrixCursor(cols);Object[] values=new Object[cols.length];for(int i=0;i<cols.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(cols[i])?f.getName():OpenableColumns.SIZE.equals(cols[i])?f.length():null;result.addRow(values);return result;}catch(Exception e){return null;}
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
