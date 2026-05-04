package org.telegram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Toast;

import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.ValgallovPluginManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Entry point for "user tapped a .js file in a file manager / chat / browser and
 * picked ValgallovGram". Shows a preview of the plugin header metadata, lets the
 * user confirm, then copies the file into Downloads/valgallov_plugins/ and enables it.
 */
public class ValgallovPluginInstallActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        Intent intent = getIntent();
        if (intent == null) { finish(); return; }

        Uri uri = intent.getData();
        if (uri == null) {
            Uri streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (streamUri != null) uri = streamUri;
        }
        if (uri == null) {
            Toast.makeText(this, "Нет файла для установки", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        final String fileName = guessFileName(uri);
        if (!fileName.endsWith(".js")) {
            Toast.makeText(this, "Не .js файл: " + fileName, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        final byte[] content;
        try {
            content = readAll(uri);
        } catch (Throwable t) {
            Toast.makeText(this, "Не удалось прочитать файл: " + t.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String preview = extractHeaderPreview(content);

        new AlertDialog.Builder(this)
            .setTitle("Установить плагин?")
            .setMessage(fileName + "\n\n" + preview)
            .setPositiveButton("Установить", (d, w) -> doInstall(fileName, content))
            .setNeutralButton("Показать код", (d, w) -> {
                String code = new String(content);
                if (code.length() > 4000) code = code.substring(0, 4000) + "\n\n… (обрезано)";
                new AlertDialog.Builder(this)
                    .setTitle(fileName)
                    .setMessage(code)
                    .setPositiveButton("Установить", (dd, ww) -> doInstall(fileName, content))
                    .setNegativeButton("Отмена", (dd, ww) -> finish())
                    .setOnCancelListener(dd -> finish())
                    .show();
            })
            .setNegativeButton("Отмена", (d, w) -> finish())
            .setOnCancelListener(d -> finish())
            .show();
    }

    private void doInstall(String fileName, byte[] content) {
        boolean ok = ValgallovPluginManager.installFromStream(fileName, new ByteArrayInputStream(content));
        if (ok) {
            if (!SharedConfig.valgallovPluginsEnabled) {
                SharedConfig.toggleValgallovPluginsEnabled();
            }
            ValgallovPluginManager.reload();
            int n = ValgallovPluginManager.loadedCount();
            Toast.makeText(this,
                "Установлено: " + fileName + " (активных: " + n + ")",
                Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Не удалось установить " + fileName, Toast.LENGTH_LONG).show();
        }
        finish();
    }

    private String guessFileName(Uri uri) {
        String name = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = c.getString(idx);
                }
            } catch (Throwable ignore) {}
        }
        if (name == null) name = uri.getLastPathSegment();
        if (name == null) name = "plugin.js";
        // strip path
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        if (!name.endsWith(".js")) name = name + ".js";
        // sanitize (no path separators, no spaces that break shell-ish stuff)
        name = name.replaceAll("[^A-Za-z0-9._\\-]", "_");
        return name;
    }

    private byte[] readAll(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new Exception("openInputStream returned null");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private String extractHeaderPreview(byte[] bytes) {
        String text = new String(bytes, 0, Math.min(bytes.length, 4096));
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        int printed = 0;
        for (String raw : lines) {
            if (printed >= 10) break;
            String t = raw.trim();
            if (t.startsWith("//")) {
                sb.append(t.substring(2).trim()).append('\n');
                printed++;
            } else if (!t.isEmpty() && printed > 0) {
                break;
            }
        }
        if (sb.length() == 0) return "(нет заголовка)";
        return sb.toString().trim();
    }
}
