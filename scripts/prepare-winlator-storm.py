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

# Keep upstream MainActivity's public API and runtime callbacks intact.
source = launcher.read_text(encoding="utf-8")
on_create = r"""    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        buildStormLauncher();
        RootFSInstaller.installIfNeeded(this);
        stormContainers = new com.winlator.container.ContainerManager(this);
        java.io.File saved = new java.io.File(getExternalFilesDir(null), "StormGame");
        java.io.File exe = findStormExe(saved);
        if (exe != null && isStormPe32(exe)) {
            stormGameDirectory = saved;
            stormStatus.setText("GAME READY\\nNaruto Shippuden: Ultimate Ninja Storm Revolution");
        }
    }
"""
source, count = re.subn(r"    @Override\s+protected void onCreate\(Bundle savedInstanceState\)\s*\{.*?\n    \}\n\n    @Override\s+protected void attachBaseContext", on_create + "\n    @Override\n    protected void attachBaseContext", source, count=1, flags=re.S)
if count != 1:
    raise SystemExit("Could not safely replace upstream onCreate method.")
source, count = re.subn(r"(public void onActivityResult\(int requestCode, int resultCode, @Nullable UriDataPlaceholder data\))", r"\1", source)
# Inject custom SAF handling into the existing callback without removing upstream file-picker handling.
needle = "        super.onActivityResult(requestCode, resultCode, data);"
if source.count(needle) != 1:
    raise SystemExit("Could not locate upstream onActivityResult callback.")
source = source.replace(needle, needle + "\n        onStormActivityResult(requestCode, resultCode, data);", 1)
class_needle = "public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {"
methods = r"""
    private static final int STORM_PICK_GAME_FOLDER = 7101;
    private android.widget.TextView stormStatus;
    private java.io.File stormGameDirectory;
    private com.winlator.container.ContainerManager stormContainers;

    private void buildStormLauncher() {
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        root.setPadding(28, 42, 28, 28);
        root.setBackgroundColor(0xff10131b);
        android.widget.TextView brand = new android.widget.TextView(this);
        brand.setText("STORM RUNTIME");
        brand.setTextColor(0xfff0a43a);
        brand.setTextSize(13);
        brand.setLetterSpacing(0.22f);
        root.addView(brand);
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("NARUTO SHIPPUDEN\\nULTIMATE NINJA STORM REVOLUTION");
        title.setTextColor(0xffffffff);
        title.setTextSize(21);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 24, 0, 18);
        root.addView(title);
        stormStatus = new android.widget.TextView(this);
        stormStatus.setTextColor(0xffb8c0cf);
        stormStatus.setTextSize(14);
        stormStatus.setGravity(android.view.Gravity.CENTER);
        stormStatus.setText("Pilih folder game yang berisi NSUNSR.exe.");
        stormStatus.setPadding(0, 14, 0, 28);
        root.addView(stormStatus);
        android.widget.Button choose = new android.widget.Button(this);
        choose.setText("PILIH FOLDER GAME");
        choose.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, STORM_PICK_GAME_FOLDER);
        });
        root.addView(choose, new android.widget.LinearLayout.LayoutParams(-1, -2));
        android.widget.Button play = new android.widget.Button(this);
        play.setText("JALANKAN NARUTO");
        play.setOnClickListener(v -> launchStormGame());
        android.widget.LinearLayout.LayoutParams pp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = 14;
        root.addView(play, pp);
        android.widget.TextView detail = new android.widget.TextView(this);
        detail.setText("Mali-G57 profile • 960×540 • DXVK • Box64 Performance • PulseAudio");
        detail.setTextColor(0xff7f899a);
        detail.setTextSize(11);
        detail.setGravity(android.view.Gravity.CENTER);
        detail.setPadding(0, 30, 0, 0);
        root.addView(detail);
        setContentView(root);
    }

    private void onStormActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode != STORM_PICK_GAME_FOLDER || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        android.net.Uri tree = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(tree, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            stormStatus.setText("Menyalin file game ke penyimpanan aplikasi...");
            new Thread(() -> {
                try {
                    java.io.File destination = new java.io.File(getExternalFilesDir(null), "StormGame");
                    deleteStormTree(destination);
                    if (!destination.mkdirs() && !destination.isDirectory()) throw new Exception("Tidak bisa membuat folder game.");
                    copyStormTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree), destination);
                    java.io.File exe = findStormExe(destination);
                    if (exe == null || !isStormPe32(exe)) throw new Exception("NSUNSR.exe PE32 x86 tidak ditemukan.");
                    runOnUiThread(() -> {
                        stormGameDirectory = destination;
                        stormStatus.setText("GAME READY\\nNSUNSR.exe terdeteksi dan PE32 x86 valid.");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> stormStatus.setText("Gagal menyiapkan game: " + e.getMessage()));
                }
            }).start();
        } catch (Exception e) {
            stormStatus.setText("Izin folder gagal: " + e.getMessage());
        }
    }

    private void copyStormTree(android.net.Uri tree, String parentId, java.io.File destination) throws Exception {
        android.net.Uri children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] columns = {android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (android.database.Cursor cursor = getContentResolver().query(children, columns, null, null, null)) {
            if (cursor == null) throw new Exception("Folder game tidak dapat dibaca.");
            while (cursor.moveToNext()) {
                String id = cursor.getString(0), name = cursor.getString(1), mime = cursor.getString(2);
                if (name == null || name.contains("/") || name.equals(".") || name.equals("..")) continue;
                java.io.File out = new java.io.File(destination, name);
                if (android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    if (!out.mkdirs() && !out.isDirectory()) throw new Exception("Gagal membuat folder " + name);
                    copyStormTree(tree, id, out);
                } else {
                    android.net.Uri document = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id);
                    try (java.io.InputStream in = getContentResolver().openInputStream(document);
                         java.io.FileOutputStream stream = new java.io.FileOutputStream(out)) {
                        if (in == null) throw new Exception("Tidak dapat membaca " + name);
                        byte[] buffer = new byte[262144];
                        int n;
                        while ((n = in.read(buffer)) != -1) stream.write(buffer, 0, n);
                    }
                }
            }
        }
    }

    private java.io.File findStormExe(java.io.File dir) {
        if (dir == null || !dir.isDirectory()) return null;
        java.io.File direct = new java.io.File(dir, "NSUNSR.exe");
        if (direct.isFile()) return direct;
        java.io.File[] files = dir.listFiles();
        if (files != null) for (java.io.File f : files) if (f.isDirectory()) {
            java.io.File found = findStormExe(f);
            if (found != null) return found;
        }
        return null;
    }

    private boolean isStormPe32(java.io.File file) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            if (raf.length() < 128 || raf.readUnsignedByte() != 'M' || raf.readUnsignedByte() != 'Z') return false;
            raf.seek(0x3c);
            int offset = Integer.reverseBytes(raf.readInt());
            if (offset < 64 || offset + 26 > raf.length()) return false;
            raf.seek(offset);
            if (raf.readUnsignedByte() != 'P' || raf.readUnsignedByte() != 'E' || raf.readUnsignedByte() != 0 || raf.readUnsignedByte() != 0) return false;
            int machine = Short.reverseBytes(raf.readShort()) & 0xffff;
            raf.skipBytes(18);
            int magic = Short.reverseBytes(raf.readShort()) & 0xffff;
            return machine == 0x014c && magic == 0x010b;
        } catch (Exception e) { return false; }
    }

    private void launchStormGame() {
        if (stormGameDirectory == null) {
            stormStatus.setText("Pilih folder game terlebih dahulu.");
            return;
        }
        if (!com.winlator.xenvironment.RootFS.find(this).isValid()) {
            stormStatus.setText("Komponen Wine sedang dipasang. Tunggu selesai, lalu tekan Jalankan Naruto.");
            com.winlator.xenvironment.RootFSInstaller.installIfNeeded(this);
            return;
        }
        java.io.File exe = findStormExe(stormGameDirectory);
        if (exe == null || !isStormPe32(exe)) {
            stormStatus.setText("NSUNSR.exe tidak valid. Pilih ulang folder game.");
            return;
        }
        com.winlator.container.Container found = null;
        for (com.winlator.container.Container c : stormContainers.getContainers()) if ("Storm Naruto".equals(c.getName())) found = c;
        if (found != null) {
            configureAndStartStorm(found, exe);
            return;
        }
        try {
            org.json.JSONObject config = new org.json.JSONObject();
            config.put("name", "Storm Naruto");
            config.put("screenSize", "960x540");
            config.put("envVars", com.winlator.container.Container.DEFAULT_ENV_VARS + " DXVK_ASYNC=1");
            config.put("graphicsDriver", com.winlator.container.GraphicsDrivers.VORTEK + "," + com.winlator.container.GraphicsDrivers.GLADIO);
            config.put("dxwrapper", com.winlator.container.DXWrappers.DXVK);
            config.put("audioDriver", com.winlator.container.AudioDrivers.PULSEAUDIO);
            config.put("drives", "E:" + stormGameDirectory.getAbsolutePath());
            config.put("box64Preset", com.winlator.box64.Box64Preset.PERFORMANCE);
            config.put("startupSelection", com.winlator.container.Container.STARTUP_SELECTION_ESSENTIAL);
            config.put("wincomponents", com.winlator.container.Container.DEFAULT_WINCOMPONENTS);
            stormContainers.createContainerAsync(config, container -> {
                if (container == null) {
                    stormStatus.setText("Gagal membuat profil Wine.");
                    return;
                }
                configureAndStartStorm(container, exe);
            });
            stormStatus.setText("Menyiapkan prefix Wine pertama kali...");
        } catch (Exception e) {
            stormStatus.setText("Gagal membuat konfigurasi: " + e.getMessage());
        }
    }

    private void configureAndStartStorm(com.winlator.container.Container container, java.io.File exe) {
        container.setName("Storm Naruto");
        container.setScreenSize("960x540");
        container.setGraphicsDriver(com.winlator.container.GraphicsDrivers.VORTEK + "," + com.winlator.container.GraphicsDrivers.GLADIO);
        container.setDXWrapper(com.winlator.container.DXWrappers.DXVK);
        container.setAudioDriver(com.winlator.container.AudioDrivers.PULSEAUDIO);
        container.setBox64Preset(com.winlator.box64.Box64Preset.PERFORMANCE);
        container.setCPUList(com.winlator.container.Container.getFallbackCPUList());
        container.setCPUListWoW64(com.winlator.container.Container.getFallbackCPUList());
        container.setDrives("E:" + stormGameDirectory.getAbsolutePath());
        container.setEnvVars(com.winlator.container.Container.DEFAULT_ENV_VARS + " DXVK_ASYNC=1");
        container.saveData();
        android.content.Intent intent = new android.content.Intent(this, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", exe.getAbsolutePath());
        stormStatus.setText("Membuka runtime Naruto...");
        startActivity(intent);
    }

    private void deleteStormTree(java.io.File file) {
        if (file == null || !file.exists()) return;
        java.io.File[] children = file.listFiles();
        if (children != null) for (java.io.File child : children) deleteStormTree(child);
        file.delete();
    }
"""
class_needle = "public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {"
if source.count(class_needle) != 1:
    raise SystemExit("MainActivity class declaration mismatch.")
source = source.replace(class_needle, class_needle + methods, 1)
launcher.write_text(source, encoding="utf-8")
print("Storm launcher patched while preserving upstream MainActivity API.")
