package dev.quickexport.sqldev;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ide.extension.Extension;

import oracle.ide.ExtensionRegistry;
import oracle.ide.webupdate.PostStartupHook;

/**
 * Runs once, after the IDE restarts following "Check for Updates &gt; Install From Local File"
 * (declared as {@code post-startup-hook} in bundle.xml).
 *
 * SQL Developer keeps an extension that belongs to no start-up feature in a lazy state until
 * something uses it, and nothing would ever use ours: it only adds a menu item to a grid that
 * Oracle's code builds. Once the extension has been loaded a single time, its feature is
 * remembered ({@code reload-if-used} in extension.xml) and loads at every later start. This
 * hook performs that first load through the IDE's public registry API.
 */
public final class FirstRunHook implements PostStartupHook {

    private static final Logger LOG = Logger.getLogger("dev.quickexport");
    private static final String EXTENSION_ID = "dev.quickexport.sqldeveloper";

    @Override
    public void install() {
        Thread t = new Thread(FirstRunHook::loadExtension, "Quick Export first start");
        t.setDaemon(true);
        t.start();
    }

    private static void loadExtension() {
        try {
            Thread.sleep(2000); // let the IDE finish its own start-up work first
            ExtensionRegistry registry = ExtensionRegistry.getExtensionRegistry();
            Extension extension = registry.findExtension(EXTENSION_ID);
            if (extension != null) {
                registry.fullyLoadExtension(extension, "Quick Export first start");
                LOG.info("Quick Export: extension loaded for the first time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Quick Export could not load itself; restart SQL Developer once more", t);
        }
    }
}
