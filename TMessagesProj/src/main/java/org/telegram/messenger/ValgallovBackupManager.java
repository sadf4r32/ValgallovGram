package org.telegram.messenger;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypted backup/restore for ValgallovGram deleted messages and edit history.
 * AES-256-CBC with SHA-256 key derivation from user PIN.
 */
public final class ValgallovBackupManager {

    private static final String MAGIC = "VALG_BKP_V1\n";

    private ValgallovBackupManager() {}

    public static void backup(Context context, String pin) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String data = collectData();
                byte[] encrypted = encrypt(data, pin);

                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String fileName = "valgallov_backup_" + ts + ".enc";

                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, fileName);

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    fos.write(encrypted);
                }

                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(context, "Бэкап сохранён: Downloads/" + fileName, Toast.LENGTH_LONG).show();
                });
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(context, "Ошибка бэкапа: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    public static void restore(Context context, File file, String pin) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                byte[] encrypted = readFile(file);
                String data = decrypt(encrypted, pin);
                importData(data);

                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(context, "Бэкап восстановлен", Toast.LENGTH_LONG).show();
                });
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(context, "Ошибка: неверный PIN или повреждённый файл", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private static String collectData() {
        StringBuilder sb = new StringBuilder(16384);

        // Deleted messages
        sb.append("==DELETED==\n");
        ArrayList<ValgallovDeletedTracker.DeletedMessage> deleted = ValgallovDeletedTracker.getAllDeletedMessages();
        for (ValgallovDeletedTracker.DeletedMessage dm : deleted) {
            sb.append(dm.dialogId).append('\t')
              .append(dm.msgId).append('\t')
              .append(dm.date).append('\t')
              .append(dm.outgoing ? "1" : "0").append('\t')
              .append(dm.senderId).append('\t')
              .append(esc(dm.senderName)).append('\t')
              .append(esc(dm.mediaType)).append('\t')
              .append(esc(dm.text)).append('\n');
        }

        // Edit history
        sb.append("==EDITS==\n");
        try {
            android.content.SharedPreferences prefs = ApplicationLoader.applicationContext
                .getSharedPreferences("valgallov_edits_v1", 0);
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (val instanceof String) {
                    sb.append(key).append('\t').append(esc((String) val)).append('\n');
                }
            }
        } catch (Throwable ignore) {}

        return sb.toString();
    }

    private static void importData(String data) {
        if (!data.startsWith("==DELETED==\n")) {
            throw new RuntimeException("Invalid backup format");
        }

        String[] sections = data.split("==EDITS==\n", 2);
        String deletedSection = sections[0].substring("==DELETED==\n".length());
        String editsSection = sections.length > 1 ? sections[1] : "";

        // Import deleted messages
        android.content.SharedPreferences contentPrefs = ApplicationLoader.applicationContext
            .getSharedPreferences("valgallov_deleted_content_v2", 0);
        android.content.SharedPreferences.Editor editor = contentPrefs.edit();

        for (String line : deletedSection.split("\n")) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t", 8);
            if (parts.length < 8) continue;
            try {
                long dialogId = Long.parseLong(parts[0]);
                int msgId = Integer.parseInt(parts[1]);
                int date = Integer.parseInt(parts[2]);
                boolean outgoing = "1".equals(parts[3]);
                long senderId = Long.parseLong(parts[4]);
                String senderName = unesc(parts[5]);
                String mediaType = unesc(parts[6]);
                String text = unesc(parts[7]);

                ValgallovDeletedTracker.markDeleted(dialogId, msgId);
                ValgallovDeletedTracker.saveContent(dialogId, msgId, text, date, outgoing, senderName, senderId, mediaType);
            } catch (Throwable ignore) {}
        }

        // Import edit history
        if (!editsSection.isEmpty()) {
            android.content.SharedPreferences editPrefs = ApplicationLoader.applicationContext
                .getSharedPreferences("valgallov_edits_v1", 0);
            android.content.SharedPreferences.Editor editEditor = editPrefs.edit();
            for (String line : editsSection.split("\n")) {
                if (line.isEmpty()) continue;
                String[] parts = line.split("\t", 2);
                if (parts.length < 2) continue;
                editEditor.putString(parts[0], unesc(parts[1]));
            }
            editEditor.apply();
        }
    }

    private static byte[] encrypt(String data, String pin) throws Exception {
        byte[] key = deriveKey(pin);
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

        byte[] plaintext = (MAGIC + data).getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = cipher.doFinal(plaintext);

        // Format: IV (16 bytes) + ciphertext
        byte[] result = new byte[16 + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, 16);
        System.arraycopy(ciphertext, 0, result, 16, ciphertext.length);
        return result;
    }

    private static String decrypt(byte[] encrypted, String pin) throws Exception {
        if (encrypted.length < 17) throw new RuntimeException("Too short");

        byte[] key = deriveKey(pin);
        byte[] iv = new byte[16];
        System.arraycopy(encrypted, 0, iv, 0, 16);
        byte[] ciphertext = new byte[encrypted.length - 16];
        System.arraycopy(encrypted, 16, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

        byte[] plaintext = cipher.doFinal(ciphertext);
        String result = new String(plaintext, StandardCharsets.UTF_8);

        if (!result.startsWith(MAGIC)) {
            throw new RuntimeException("Invalid PIN or corrupted backup");
        }
        return result.substring(MAGIC.length());
    }

    private static byte[] deriveKey(String pin) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update("ValgallovGram_backup_salt_v1".getBytes(StandardCharsets.UTF_8));
        md.update(pin.getBytes(StandardCharsets.UTF_8));
        return md.digest();
    }

    private static byte[] readFile(File f) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) f.length());
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
        }
        return bos.toByteArray();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unesc(String s) {
        if (s == null || s.isEmpty()) return null;
        return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }
}
