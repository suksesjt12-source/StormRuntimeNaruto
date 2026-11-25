#!/usr/bin/env python3
"""Patch the pinned runtime into a Storm-first Naruto launcher."""
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()
gradle = root / "app/build.gradle"
strings = root / "app/src/main/res/values/strings.xml"
launcher = root / "app/src/main/java/com/winlator/MainActivity.java"
if not all(p.is_file() for p in (gradle, strings, launcher)):
    raise SystemExit("Pinned Android runtime source is incomplete.")

g = gradle.read_text(encoding="utf-8")
if "applicationId 'id.stormruntime.naruto'" not in g:
    if g.count("applicationId 'com.winlator'") != 1:
        raise SystemExit("Cannot safely patch applicationId.")
    g = g.replace("applicationId 'com.winlator'", "applicationId 'id.stormruntime.naruto'")
if 'versionName "0.2.0-storm"' not in g:
    g, n = re.subn(r'versionName\s+"[^"]+"', 'versionName "0.2.0-storm"', g, count=1)
    if n != 1:
        raise SystemExit("Cannot safely patch versionName.")
gradle.write_text(g, encoding="utf-8")

s = strings.read_text(encoding="utf-8")
s = re.sub(r'<string name="app_name">.*?</string>',
           '<string name="app_name">Storm Runtime Naruto</string>', s, count=1)
strings.write_text(s, encoding="utf-8")

java = r'''package com.winlator;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.winlator.box64.Box64Preset;
import com.winlator.container.AudioDrivers;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.AppUtils;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_GAME_FOLDER = 7101;
    private TextView status;
    private File gameDirectory;
    private ContainerManager containers;

    @Override public void onCreate(Bundle state) {
        AppUtils.setActivityTheme(this);
        super.onCreate(state);
        buildStormLauncher();
        RootFSInstaller.installIfNeeded(this);
        containers = new ContainerManager(this);
        File saved = new File(getExternalFilesDir(null), "StormGame");
        File exe = findExe(saved);
        if (exe != null && isPe32(exe)) {
            gameDirectory = saved;
            status.setText("GAME READY\nNaruto Shippuden: Ultimate Ninja Storm Revolution");
        }
    }

    private void buildStormLauncher() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 42, 28, 28);
        root.setBackgroundColor(0xff10131b);

        TextView brand = new TextView(this);
        brand.setText("STORM RUNTIME");
        brand.setTextColor(0xfff0a43a);
        brand.setTextSize(13);
        brand.setLetterSpacing(0.22f);
        root.addView(brand);

        TextView title = new TextView(this);
        title.setText("NARUTO SHIPPUDEN\nULTIMATE NINJA STORM REVOLUTION");
        title.setTextColor(0xffffffff);
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 24, 0, 18);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(0xffb8c0cf);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setText("Pilih folder game yang berisi NSUNSR.exe.");
        status.setPadding(0, 14, 0, 28);
        root.addView(status);

        Button choose = new Button(this);
        choose.setText("PILIH FOLDER GAME");
        choose.setOnClickListener(v -> chooseGameFolder());
        root.addView(choose, new LinearLayout.LayoutParams(-1, -2));

        Button play = new Button(this);
        play.setText("JALANKAN NARUTO");
        play.setOnClickListener(v -> launchGame());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(-1, -2);
        playParams.topMargin = 14;
        root.addView(play, playParams);

        TextView detail = new TextView(this);
        detail.setText("Mali-G57 profile • 960×540 • DXVK • Box64 Performance • PulseAudio");
        detail.setTextColor(0xff7f899a);
        detail.setTextSize(11);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, 30, 0, 0);
        root.addView(detail);
        setContentView(root);
    }

    private void chooseGameFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_GAME_FOLDER);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_GAME_FOLDER || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            status.setText("Menyalin file game ke penyimpanan aplikasi...");
            new Thread(() -> {
                try {
                    File destination = new File(getExternalFilesDir(null), "StormGame");
                    deleteTree(destination);
                    if (!destination.mkdirs() && !destination.isDirectory()) throw new Exception("Tidak bisa membuat folder game.");
                    String rootId = DocumentsContract.getTreeDocumentId(tree);
                    copyDocumentTree(tree, rootId, destination);
                    File exe = findExe(destination);
                    if (exe == null || !isPe32(exe)) throw new Exception("NSUNSR.exe PE32 x86 tidak ditemukan di folder game.");
                    runOnUiThread(() -> {
                        gameDirectory = destination;
                        status.setText("GAME READY\nNSUNSR.exe terdeteksi dan PE32 x86 valid.");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> status.setText("Gagal menyiapkan game: " + e.getMessage()));
                }
            }).start();
        } catch (Exception e) {
            status.setText("Izin folder gagal: " + e.getMessage());
        }
    }

    private void copyDocumentTree(Uri tree, String parentId, File destination) throws Exception {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) throw new Exception("Folder game tidak dapat dibaca.");
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (name == null || name.contains("/") || name.equals(".") || name.equals("..")) continue;
                File out = new File(destination, name);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (!out.mkdirs() && !out.isDirectory()) throw new Exception("Gagal membuat folder " + name);
                    copyDocumentTree(tree, id, out);
                } else {
                    Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    try (InputStream in = getContentResolver().openInputStream(document);
                         FileOutputStream stream = new FileOutputStream(out)) {
                        if (in == null) throw new Exception("Tidak dapat membaca " + name);
                        byte[] buffer = new byte[1024 * 256];
                        int count;
                        while ((count = in.read(buffer)) != -1) stream.write(buffer, 0, count);
                    }
                }
            }
        }
    }

    private File findExe(File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        File direct = new File(dir, "NSUNSR.exe");
        if (direct.isFile()) return direct;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) {
            if (f.isDirectory()) {
                File found = findExe(f);
                if (found != null) return found;
            }
        }
        return null;
    }

    private boolean isPe32(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            if (raf.length() < 128 || raf.readUnsignedByte() != 'M' || raf.readUnsignedByte() != 'Z') return false;
            raf.seek(0x3c);
            int offset = Integer.reverseBytes(raf.readInt());
            if (offset < 64 || offset + 26 > raf.length()) return false;
            raf.seek(offset);
            if (raf.readUnsignedByte() != 'P' || raf.readUnsignedByte() != 'E' || raf.readUnsignedByte() != 0 || raf.readUnsignedByte() != 0) return false;
            int machine = Short.reverseBytes(raf.readShort()) & 0xffff;
            raf.skipBytes(20);
            int magic = Short.reverseBytes(raf.readShort()) & 0xffff;
            return machine == 0x014c && magic == 0x010b;
        } catch (Exception e) { return false; }
    }

    private void launchGame() {
        if (gameDirectory == null) {
            status.setText("Pilih folder game terlebih dahulu.");
            return;
        }
        RootFS root = RootFS.find(this);
        if (!root.isValid()) {
            status.setText("Komponen sistem Wine sedang dipasang. Tunggu selesai, lalu tekan Jalankan Naruto.");
            RootFSInstaller.installIfNeeded(this);
            return;
        }
        File exe = findExe(gameDirectory);
        if (exe == null || !isPe32(exe)) {
            status.setText("NSUNSR.exe tidak valid. Pilih ulang folder game.");
            return;
        }
        Container existing = null;
        for (Container c : containers.getContainers()) if ("Storm Naruto".equals(c.getName())) existing = c;
        if (existing != null) {
            configureAndStart(existing, exe);
            return;
        }
        try {
            JSONObject config = new JSONObject();
            config.put("name", "Storm Naruto");
            config.put("screenSize", "960x540");
            config.put("envVars", Container.DEFAULT_ENV_VARS + " DXVK_ASYNC=1");
            config.put("graphicsDriver", GraphicsDrivers.VORTEK + "," + GraphicsDrivers.GLADIO);
            config.put("dxwrapper", DXWrappers.DXVK);
            config.put("audioDriver", AudioDrivers.PULSEAUDIO);
            config.put("drives", "E:" + gameDirectory.getAbsolutePath());
            config.put("box64Preset", Box64Preset.PERFORMANCE);
            config.put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL);
            config.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            containers.createContainerAsync(config, container -> {
                if (container == null) {
                    status.setText("Gagal membuat profil Wine. Periksa instalasi runtime.");
                    return;
                }
                configureAndStart(container, exe);
            });
            status.setText("Menyiapkan prefix Wine pertama kali...");
        } catch (Exception e) {
            status.setText("Gagal membuat konfigurasi: " + e.getMessage());
        }
    }

    private void configureAndStart(Container container, File exe) {
        container.setName("Storm Naruto");
        container.setScreenSize("960x540");
        container.setGraphicsDriver(GraphicsDrivers.VORTEK + "," + GraphicsDrivers.GLADIO);
        container.setDXWrapper(DXWrappers.DXVK);
        container.setAudioDriver(AudioDrivers.PULSEAUDIO);
        container.setBox64Preset(Box64Preset.PERFORMANCE);
        container.setCPUList(Container.getFallbackCPUList());
        container.setCPUListWoW64(Container.getFallbackCPUList());
        container.setDrives("E:" + gameDirectory.getAbsolutePath());
        container.setEnvVars(Container.DEFAULT_ENV_VARS + " DXVK_ASYNC=1");
        container.saveData();
        Intent intent = new Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", exe.getAbsolutePath());
        status.setText("Membuka runtime Naruto...");
        startActivity(intent);
    }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
'''
launcher.write_text(java, encoding="utf-8")
print("Storm-first launcher patch applied.")
