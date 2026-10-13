# How the grid and extension loading work

Notes for porting Quick Export to other SQL Developer versions. Everything here comes from public class and
method signatures (`javap -public`), extension manifests, and observing a running IDE. No Oracle code is
reproduced. **Verify every name against your own installation before relying on it.**

Verified on: **SQL Developer 24.3.1.347.1826** (bundled Oracle JDK 17.0.13, Windows 11).

## The result grid

| What | Where (24.3.1) |
|---|---|
| Grid component | `oracle.dbtools.raptor.controls.grid.RaptorGridTable` (query results: `ResultSetTable`) |
| Registering a right-click menu item | `public static RaptorGridTable.addGridContextMenu(GridContextMenuItem)` and `removeGridContextMenu` |
| Menu item base class | `oracle.dbtools.raptor.controls.grid.contextmenu.GridContextMenuItem`: implement `canShow(ContextMenu)`, `createAndShowMenu(ContextMenu)`, `handleEvent(IdeAction, Context)`, `update(IdeAction, Context)`; the protected field `_table` is the grid after `setTableFromContext(context)` |
| Query behind the grid | `IResultSetTableModel`: `getModifiedSql()`, `getSql()`, `getBindList()`, `getBindMap()`, `getConnectionName()` |
| Rows | `AbstractRaptorGridModel`: `getLoadedRowCount()`, `allRowsFetched()`, `fetchAllRows(boolean)`, `getValueAt(row, col)` |
| Separate session | `oracle.dbtools.raptor.utils.Connections.getInstance().cloneConnection(connectionName)` |

`getValueAt` returns Oracle driver objects (`oracle.sql.CHAR`, `NUMBER`, `DATE`, `ROWID`), not plain Java values.
That, and the grid holding only the fetched rows, is why Quick Export re-runs the query over JDBC on a cloned
connection and reads plain Java types from the `ResultSet`.

Oracle's own grid items (Export, Count Rows, Single Record View, ...) are subclasses of
`GridContextMenuItem` registered the same way. There is no `extension.xml` hook for the grid menu: the
declarative `context-menu-hook` only covers sites such as `db_nav` and `dba_nav`.

These are internal, unsupported classes. `QuickExportAddin` therefore calls all grid code through one class
(`GridIntegration`) inside a `try/catch (Throwable)`, so a mismatch disables the extension with a log entry
rather than breaking start-up.

## Why installing via Check for Updates matters

SQL Developer extensions are OSGi bundles that also carry a `META-INF/extension.xml`. Two separate steps
decide whether the addin runs:

1. **Registration.** The bundle must be registered with the framework (`bundles.info`, `dropins`, or what
   Check for Updates writes) and its manifest parsed. Dropping a jar into a folder achieves this.
2. **Activation.** The IDE keeps extensions in a lazy state until they are used (`oracle.ide.extension.HooksProcessingMode=LAZY`).
   An extension that is not part of a start-up feature stays in `TRIGGERS_LOADED` and its hooks, including the
   `<addin>`, are never initialised. Oracle's own start-up extensions are listed in the `members` of the
   `sqldeveloper` feature, which a third party cannot edit.

Quick Export's `extension.xml` declares a `service` feature with `reload-if-used="true"`. Once the extension has
been loaded a single time, the IDE remembers the feature in `ide-extension-prefs.xml`
(`featuresToReloadAtStartup`) and loads it at every start. Something has to cause that first load. The update
bundle (`META-INF/bundle.xml`) declares a `post-startup-hook` (`FirstRunHook`) which asks the IDE's public
`oracle.ide.ExtensionRegistry.fullyLoadExtension(...)` to load it after the restart that follows installation.

Things that did **not** work, each tried on 24.3.1:

- Jar in `dropins/`, in `sqldeveloper/extensions/`, or both: extension registered, never fully loaded.
- `<feature-member id="sqldeveloper"/>` in the manifest: has no effect for a third-party extension.
- A `service` feature without anything performing the first load: stays lazy.

## Diagnosing a lazy extension

With the JDK that ships in the install (it includes `jcmd` and the attach API):

```text
jcmd <pid> GC.class_histogram          # are any dev.quickexport classes loaded?
jcmd <pid> VM.system_properties        # ide.user.dir, ide.extension.search.path, ...
```

A small Java agent attached to the running IDE can ask `oracle.ide.ExtensionRegistry` for
`findExtension(id)`, its `getState()` (`TRIGGERS_LOADED` vs `FULLY_LOADED`), `isLoaded(id)` and
`getNotLoadedReason(id)`. For a throwaway profile, set the `IDE_USER_DIR` environment variable before starting
SQL Developer so your own settings are untouched.

## Bundle details that matter

- `Bundle-SymbolicName` equals the extension id; `Require-Bundle` lists the Oracle bundles whose packages we use
  (`oracle.ide`, `oracle.javatools`, `oracle.javaxide`, `oracle.sqldeveloper`, `oracle.sqldeveloper.utils`,
  `oracle.jdeveloper.db.connection`, `oracle.ide.webupdate`). Both `oracle.sqldeveloper` and
  `oracle.sqldeveloper.utils` export the `...controls.grid` packages.
- The extension version must be numeric (`0.1.0`), also in `bundle.xml`.
- Apache POI is bundled and relocated under `dev.quickexport.shaded`, **except** `org.openxmlformats`,
  `org.etsi`, `org.w3.x2000` and `com.microsoft.schemas`. XMLBeans' binary schema files store the original
  class names of those generated types; relocating them makes XMLBeans fall back to generic objects and fail
  with a `ClassCastException` when a workbook is created. They stay private to our bundle.
