package dev.quickexport.sqldev;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.quickexport.core.ExportSettings;

/**
 * User-level files, kept in a {@code quickexport} folder inside SQL Developer's user directory:
 *
 * <ul>
 *   <li>{@code quickexport.properties}: hand-edited settings. Read on every export, never
 *       written by the extension and not created automatically.</li>
 *   <li>{@code state.properties}: written by the extension; remembers the last export folder.</li>
 * </ul>
 */
final class UserConfig {

    private static final Logger LOG = Logger.getLogger("dev.quickexport");
    private static final String SETTINGS_FILE = "quickexport.properties";
    private static final String STATE_FILE = "state.properties";
    private static final String LAST_DIRECTORY = "lastDirectory";

    private UserConfig() {
    }

    static ExportSettings loadSettings() {
        return ExportSettings.fromProperties(load(configDir().resolve(SETTINGS_FILE)));
    }

    static Path lastDirectory() {
        String saved = load(configDir().resolve(STATE_FILE)).getProperty(LAST_DIRECTORY);
        if (saved != null) {
            try {
                Path dir = Paths.get(saved);
                if (Files.isDirectory(dir)) {
                    return dir;
                }
            } catch (InvalidPathException e) {
                // fall back to the home directory
            }
        }
        return Paths.get(System.getProperty("user.home"));
    }

    static void rememberDirectory(Path dir) {
        Path file = configDir().resolve(STATE_FILE);
        Properties state = load(file);
        state.setProperty(LAST_DIRECTORY, dir.toString());
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                state.store(out, "Quick Export state (written by the extension)");
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Could not remember the last export folder", e);
        }
    }

    private static Path configDir() {
        String userDir = System.getProperty("ide.user.dir");
        Path root = userDir != null ? Paths.get(userDir) : Paths.get(System.getProperty("user.home"), ".sqldeveloper");
        return root.resolve("quickexport");
    }

    private static Properties load(Path file) {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException | IllegalArgumentException e) {
                LOG.log(Level.WARNING, "Could not read " + file, e);
            }
        }
        return properties;
    }
}
