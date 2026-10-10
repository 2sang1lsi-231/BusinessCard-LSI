package com.lsi.businesscard;

import android.app.Activity;
import android.content.Intent;
import com.google.mlkit.vision.documentscanner.*;

/** SDK supplies live edge detection, four-corner adjustment and perspective correction. */
public final class DocumentScan {
    public interface Ready {boolean ready();}
    public interface Failure {void failed(Exception error);}
    static GmsDocumentScannerOptions options(){return new GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true).setPageLimit(1)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_BASE).build();}
    public static void launch(Activity activity,int request,Ready ready,Failure failure){
        GmsDocumentScanning.getClient(options()).getStartScanIntent(activity)
            .addOnSuccessListener(sender->{if(activity.isFinishing()||activity.isDestroyed())return;if(!ready.ready())return;try{activity.startIntentSenderForResult(sender,request,null,0,0,0);}catch(Exception e){failure.failed(e);}})
            .addOnFailureListener(e->{if(!activity.isFinishing()&&!activity.isDestroyed()){if(ready.ready())failure.failed(e);}});
    }
    public static android.net.Uri image(Intent data){GmsDocumentScanningResult result=GmsDocumentScanningResult.fromActivityResultIntent(data);return result==null||result.getPages()==null||result.getPages().isEmpty()?null:result.getPages().get(0).getImageUri();}
    private DocumentScan(){}
}
