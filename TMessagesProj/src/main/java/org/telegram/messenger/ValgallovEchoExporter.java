package org.telegram.messenger;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Exports deleted messages and edit history for a chat into an HTML file.
 * Output: Downloads/valgallov_<chatname>_<timestamp>.html
 */
public final class ValgallovEchoExporter {

    private ValgallovEchoExporter() {}

    public static void export(Context context, long dialogId, String chatName) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String result = generateHtml(dialogId, chatName);
                String safeName = chatName.replaceAll("[^a-zA-Z0-9а-яА-ЯёЁ_\\-]", "_");
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String fileName = "valgallov_" + safeName + "_" + ts + ".html";

                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File outFile = new File(dir, fileName);

                try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8)) {
                    writer.write(result);
                }

                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(context, "Эхо сохранено: Downloads/" + fileName, Toast.LENGTH_LONG).show();
                });
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(context, "Ошибка экспорта: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private static String generateHtml(long dialogId, String chatName) {
        StringBuilder sb = new StringBuilder(8192);
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());

        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>Эхо — ");
        sb.append(escHtml(chatName));
        sb.append("</title><style>\n");
        sb.append("body{background:#15191F;color:#B0B7C0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;padding:20px;max-width:800px;margin:0 auto}\n");
        sb.append("h1{color:#E5DBC4;font-size:22px;border-bottom:1px solid #2a3040;padding-bottom:12px}\n");
        sb.append("h2{color:#C9A86C;font-size:16px;margin-top:32px;border-bottom:1px solid #2a3040;padding-bottom:8px}\n");
        sb.append(".msg{background:#1c2129;border-radius:8px;padding:12px 16px;margin:8px 0;border-left:3px solid #D44040}\n");
        sb.append(".edit{background:#1c2129;border-radius:8px;padding:12px 16px;margin:8px 0;border-left:3px solid #C9A86C}\n");
        sb.append(".meta{color:#7A8AA0;font-size:12px;margin-bottom:4px}\n");
        sb.append(".text{color:#E5DBC4;font-size:14px;white-space:pre-wrap}\n");
        sb.append(".rune{color:#D44040;font-weight:bold;font-size:16px}\n");
        sb.append(".badge{display:inline-block;background:#3a2000;color:#FF6B35;font-size:10px;font-weight:bold;padding:2px 6px;border-radius:3px;margin-left:8px}\n");
        sb.append(".sender{color:#E5DBC4;font-weight:bold;font-size:13px}\n");
        sb.append(".version-num{color:#7A8AA0;font-size:11px}\n");
        sb.append("</style></head><body>\n");
        sb.append("<h1><span class='rune'>ᚺ</span> Эхо чата: ").append(escHtml(chatName)).append("</h1>\n");
        sb.append("<p style='color:#7A8AA0;font-size:12px'>Экспорт: ").append(sdf.format(new Date())).append(" • ValgallovGram</p>\n");

        // Deleted messages section
        ArrayList<ValgallovDeletedTracker.DeletedMessage> deleted = ValgallovDeletedTracker.getDeletedMessages(dialogId);
        sb.append("<h2><span class='rune'>ᚺ</span> Удалённые сообщения (").append(deleted.size()).append(")</h2>\n");
        if (deleted.isEmpty()) {
            sb.append("<p style='color:#7A8AA0'>Нет удалённых сообщений.</p>\n");
        } else {
            for (ValgallovDeletedTracker.DeletedMessage dm : deleted) {
                sb.append("<div class='msg'>");
                sb.append("<div class='meta'>");
                if (dm.senderName != null && !dm.senderName.isEmpty()) {
                    sb.append("<span class='sender'>").append(escHtml(dm.senderName)).append("</span> • ");
                }
                sb.append(dm.outgoing ? "Ты удалил" : "Собеседник удалил");
                if (dm.date > 0) {
                    sb.append(" • ").append(sdf.format(new Date((long) dm.date * 1000)));
                }
                if (dm.mediaType != null && !dm.mediaType.isEmpty()) {
                    sb.append("<span class='badge'>").append(getMediaLabel(dm.mediaType)).append("</span>");
                }
                sb.append("</div>");
                sb.append("<div class='text'>").append(escHtml(dm.text)).append("</div>");
                sb.append("</div>\n");
            }
        }

        // Edit history section
        ArrayList<ValgallovEditTracker.Version> edits = ValgallovEditTracker.getAllVersionsForDialog(dialogId);
        sb.append("<h2>✎ История редактирований (").append(edits.size()).append(" версий)</h2>\n");
        if (edits.isEmpty()) {
            sb.append("<p style='color:#7A8AA0'>Нет сохранённых редактирований.</p>\n");
        } else {
            int currentMsgId = -1;
            int versionNum = 0;
            for (ValgallovEditTracker.Version v : edits) {
                if (v.msgId != currentMsgId) {
                    currentMsgId = v.msgId;
                    versionNum = 1;
                    sb.append("<div style='margin-top:16px;color:#7A8AA0;font-size:11px'>Сообщение #").append(v.msgId).append("</div>\n");
                } else {
                    versionNum++;
                }
                sb.append("<div class='edit'>");
                sb.append("<div class='meta'>");
                sb.append("<span class='version-num'>v").append(versionNum).append("</span> • ");
                sb.append(sdf.format(new Date(v.timestamp)));
                sb.append("</div>");
                sb.append("<div class='text'>").append(escHtml(v.text)).append("</div>");
                sb.append("</div>\n");
            }
        }

        sb.append("<p style='color:#7A8AA0;font-size:11px;margin-top:32px;border-top:1px solid #2a3040;padding-top:12px'>ValgallovGram • Эхо Чертога</p>\n");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String getMediaLabel(String mediaType) {
        switch (mediaType) {
            case "sticker": return "СТИКЕР";
            case "photo": return "ФОТО";
            case "video": return "ВИДЕО";
            case "voice": return "ГОЛОС";
            case "video_note": return "КРУЖОК";
            case "gif": return "GIF";
            case "document": return "ФАЙЛ";
            default: return mediaType.toUpperCase(Locale.ROOT);
        }
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
