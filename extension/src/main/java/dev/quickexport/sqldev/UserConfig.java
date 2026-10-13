package dev.quickexport.sqldev;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.quickexport.core.ExportSettings;

/**
 * User-level files, kept in a {@code quickexport} folder next to SQL Developer's own settings:
 *
 * <ul>
 *   <li>{@code quickexport.properties}: hand-edited settings (delimiter and so on). Read on
 *       every export, never written by the extension, and not created automatically.</li>
 *   <li>{@code state.properties}: written by the extension; remembers the last folder.</li>
 * </ul>
 */
final class UserConfig {

    private static final Logger LOG = Logger.getLogger("dev.quickexport");
    private static final String LAST_DIR = "lastDirectory";

    private UserConfig() {
    }

    static Path configDir() {
        String base = System.getProperty("ide.user.dir");
        Path root = base != null ? Paths.get(base) : Paths.get(System.getProperty("user.home"), ".sqldeveloper");
        return root.resolve("quickexport");
    }

    static ExportSettings loadSettings() {
        Properties p = load(configDir().resolve("quickexport.properties"));
        return ExportSettings.fromProperties(p);
    }

    static Path lastDirectory() {
        String v = load(configDir().resolve("state.properties")).getProperty(LAST_DIR);
        if (v != null) {
            try {
                Path dir = Paths.get(v);
                if (Files.isDirectory(dir)) {
                    return dir;
                }
            } catch (RuntimeException e) {
                // fall through to the default
            }
        }
        return Paths.get(System.getProperty("user.home"));
    }

    static void rememberDirectory(Path dir) {
        Path file = configDir().resolve("state.properties");
        Properties p = load(file);
        p.setProperty(LAST_DIR, dir.toString());
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "Quick Export state (written by the extension)");
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Could not remember the last export folder", e);
        }
    }

    private static Properties load(Path file) {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                p.load(in);
            } catch (IOException | IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Could not read " + file, e);
            }
        }
        return p;
    }
}
