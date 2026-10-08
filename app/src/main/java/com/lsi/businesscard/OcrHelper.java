package com.lsi.businesscard;

import android.content.Context;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import java.io.File;

public final class OcrHelper {
    public interface Callback { void onSuccess(String text); void onError(Exception e); }

    public static void recognize(Context ctx, String path, Callback cb) {
        final TextRecognizer recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
        try {
            InputImage image = InputImage.fromFilePath(ctx, Uri.fromFile(new File(path)));
            recognizer.process(image)
                    .addOnSuccessListener(result -> { try { cb.onSuccess(result.getText()); } finally { recognizer.close(); } })
                    .addOnFailureListener(e -> { try { cb.onError(e); } finally { recognizer.close(); } });
        } catch (Exception e) { recognizer.close(); cb.onError(e); }
    }
    private OcrHelper(){}
}
