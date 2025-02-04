package com.example.scannerpdf;

import static com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG;
import static com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.example.scannerpdf.databinding.ActivityMainBinding;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private File pdfFile;

    // スキャナーのActivityResultLauncher
    private final ActivityResultLauncher<IntentSenderRequest> scannerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartIntentSenderForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK) {
                            GmsDocumentScanningResult scanResult =
                                    GmsDocumentScanningResult.fromActivityResultIntent(result.getData());

                            if (scanResult != null && scanResult.getPdf() != null) {
                                savePdfToInternalStorage(scanResult.getPdf().getUri());
                            }
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // スキャンしたPDFファイルの保存場所を設定
        pdfFile = new File(getFilesDir(), "Documents/scan.pdf");

        // ボタンのクリックリスナーを設定
        binding.btnOpenPdf.setOnClickListener(v -> openPdf());
        binding.button1.setOnClickListener(v -> startScanner(GmsDocumentScannerOptions.SCANNER_MODE_BASE));
        binding.button2.setOnClickListener(v -> startScanner(GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER));
        binding.button3.setOnClickListener(v -> startScanner(GmsDocumentScannerOptions.SCANNER_MODE_FULL));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePdfButtonState(); // PDFの状態を確認・更新
    }

    /**
     * ドキュメントスキャナーを開始する
     *
     * @param scannerMode 使用するスキャンモード
     */
    private void startScanner(int scannerMode) {
        try {
            // ドキュメントスキャナーの設定を構築
            GmsDocumentScannerOptions.Builder options = new GmsDocumentScannerOptions.Builder()
                    .setScannerMode(scannerMode)                                // スキャンモードを設定（SCANNER_MODE_BASE, SCANNER_MODE_FULLなど）
                    .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)    // スキャン結果のフォーマットをJPEGとPDFに設定
                    .setGalleryImportAllowed(true)                              // 画像ギャラリーからのインポートを許可
                    .setPageLimit(20);                                          // スキャンできるページの最大数を20に設定

            // スキャナーを取得し、スキャンを開始するインテントを取得
            GmsDocumentScanning.getClient(options.build())
                    .getStartScanIntent(MainActivity.this)
                    .addOnSuccessListener(intentSender ->
                            // スキャン開始インテントを受け取り、ActivityResultLauncherで起動
                            scannerLauncher.launch(new IntentSenderRequest.Builder(intentSender).build()))
                    .addOnFailureListener(e ->
                            // スキャン開始に失敗した場合、エラーメッセージをトーストで表示
                            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show());


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * スキャンしたPDFを内部ストレージに保存する
     *
     * @param pdfUri スキャンしたPDFのURI
     */
    private void savePdfToInternalStorage(Uri pdfUri) {
        try {
            File documentsDir = new File(getFilesDir(), "Documents");
            if (!documentsDir.exists() && !documentsDir.mkdirs()) {
                throw new IOException("ディレクトリの作成に失敗しました");
            }

            File pdfFile = new File(documentsDir, "scan.pdf");
            try (InputStream inputStream = getContentResolver().openInputStream(pdfUri);
                 FileOutputStream fos = new FileOutputStream(pdfFile)) {
                if (inputStream != null) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
            }

            // PDFの状態を更新
            runOnUiThread(this::updatePdfButtonState);
            Toast.makeText(this, "PDFが保存されました: " + pdfFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "PDFの保存に失敗しました", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 外部PDFビューアを使用してPDFを開く
     */
    private void openPdf() {
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(this, "PDFファイルが見つかりません", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Uri pdfUri = FileProvider.getUriForFile(this, "com.example.scannerpdf.provider", pdfFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(pdfUri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(intent, "PDFを開く"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "PDFを開くアプリが見つかりません", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * PDFボタンの状態を更新し、ファイルサイズを表示する
     */
    private void updatePdfButtonState() {
        if (pdfFile.exists()) {
            binding.btnOpenPdf.setEnabled(true);
            binding.tvPdfSize.setVisibility(View.VISIBLE);
            binding.tvPdfSize.setText("PDFサイズ: " + getFileSize(pdfFile));
        } else {
            binding.btnOpenPdf.setEnabled(false);
            binding.tvPdfSize.setVisibility(View.GONE);
        }
    }

    /**
     * ファイルサイズを取得（KBまたはMB単位）
     *
     * @param file ファイルサイズを取得する対象のファイル
     * @return 整形されたファイルサイズの文字列
     */
    private String getFileSize(File file) {
        double fileSizeInBytes = file.length();
        double fileSizeInKB = fileSizeInBytes / 1024;
        double fileSizeInMB = fileSizeInKB / 1024;

        if (fileSizeInMB >= 1) {
            return String.format("%.2f MB", fileSizeInMB);
        } else {
            return String.format("%.2f KB", fileSizeInKB);
        }
    }
}
